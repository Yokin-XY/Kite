#!/usr/bin/env python3
"""从官方上游刷新资源清单的 latestVersionWindow。

只查询官方端点（registry.npmjs.org / pypi.org）：镜像只承运字节，
新鲜度事实必须来自上游本身。支持的 source.type：npm、pypi；
其余类型（acp_registry_binary、official_release_archive、android_apk、git）跳过并报告。

写入采用外科手术式文本替换：只重写 latestVersionWindow 数组块并沿用该文件
既有的条目键序与内联风格，清单其余字节保持不变。

用法：
  py -3 tools/resource-store/update_windows.py --resources assets/resources [--dry-run]
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import urllib.request
from pathlib import Path

NPM_REGISTRY = "https://registry.npmjs.org"
PYPI_INDEX = "https://pypi.org/pypi"
USER_AGENT = "Kite-Resource-Store-Updater/1"
SEMVER = re.compile(r"^(\d+)\.(\d+)\.(\d+)$")
WINDOW_BLOCK = re.compile(r'("latestVersionWindow": \[)[\s\S]*?(\][ \t]*(?:,\r?\n|\r?\n))')


def fetch_json(url: str) -> object:
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT, "Accept": "application/json"})
    with urllib.request.urlopen(request, timeout=60) as response:
        return json.loads(response.read().decode("utf-8"))


def semver_key(version: str):
    match = SEMVER.match(version)
    if not match:
        return None
    return tuple(int(part) for part in match.groups())


def npm_window(package: str, size: int) -> list[dict]:
    encoded = urllib.request.quote(package, safe="")
    document = fetch_json(f"{NPM_REGISTRY}/{encoded}")
    stable = []
    for version, detail in document.get("versions", {}).items():
        key = semver_key(version)
        integrity = (detail.get("dist") or {}).get("integrity")
        if key is not None and integrity:
            stable.append((key, version, integrity))
    stable.sort(reverse=True)
    return [
        {"artifact": package, "version": version, "integrity": integrity}
        for _, version, integrity in stable[:size]
    ]


def pypi_window(package: str, size: int) -> list[dict]:
    document = fetch_json(f"{PYPI_INDEX}/{package}/json")
    releases = document.get("releases", {})
    dated = []
    for version, files in releases.items():
        if semver_key(version) is None or not files:
            continue
        newest = max(file.get("upload_time_iso_8601", "") for file in files)
        dated.append((newest, version, files))
    dated.sort(reverse=True)
    window: list[dict] = []
    for _, version, files in dated:
        wheel = next(
            (f for f in files if f.get("filename", "").endswith(".whl") and (
                ("aarch64" in f["filename"] and "linux" in f["filename"])
                or "py3-none-any" in f["filename"]
            )),
            None,
        )
        sha256 = (wheel or {}).get("digests", {}).get("sha256")
        if not sha256:
            continue
        window.append({"artifact": package, "version": version, "sha256": sha256})
        if len(window) >= size:
            break
    return window


def entry_text(entry: dict, style: list[str]) -> str:
    """按既有键序与内联风格序列化一个窗口条目。"""
    return "{ " + ", ".join(f'"{key}": {json.dumps(entry[key], ensure_ascii=False)}' for key in style) + " }"


def rewrite_window(raw: str, entries: list[dict], original_entries: list[dict]) -> str:
    match = WINDOW_BLOCK.search(raw)
    if match is None:
        raise ValueError("找不到 latestVersionWindow 块")
    newline = "\r\n" if "\r\n" in raw else "\n"
    line_start = raw.rfind("\n", 0, match.start()) + 1
    base_indent = raw[line_start:match.start()]
    entry_indent = base_indent + "  "
    keep_artifact = any("artifact" in entry for entry in original_entries) or len(
        {entry["artifact"] for entry in entries}
    ) > 1
    style = [key for key in ("artifact", "version", "integrity", "sha256")
             if key == "artifact" and keep_artifact or key in entries[0] and key != "artifact"]
    body = newline.join(entry_indent + entry_text(entry, style) + "," for entry in entries[:-1])
    body += newline + entry_indent + entry_text(entries[-1], style)
    return (raw[:match.start()] + match.group(1) + newline + body + newline
            + base_indent + match.group(2) + raw[match.end():])


def update_manifest(path: Path, size: int, dry_run: bool) -> str:
    raw = path.read_bytes().decode("utf-8")
    manifest = json.loads(raw)
    source = manifest.get("source", {})
    kind = source.get("type")
    package = source.get("package")
    if kind not in ("npm", "pypi") or not package:
        return f"跳过 {path.parent.name}: type={kind}（暂不支持自动刷新）"
    packages = [package] + list(source.get("companionPackages") or [])
    entries: list[dict] = []
    for name in packages:
        window = npm_window(name, size) if kind == "npm" else pypi_window(name, size)
        if not window:
            return f"失败 {path.parent.name}: {name} 无法从官方源取得可用稳定版本，保持原窗口"
        entries.extend(window)
    old = [entry.get("version") for entry in source.get("latestVersionWindow", [])]
    if [e["version"] for e in entries] == old:
        return f"不变 {path.parent.name}: {', '.join(old)}"
    if not dry_run:
        path.write_bytes(rewrite_window(raw, entries, source.get("latestVersionWindow", [])).encode("utf-8"))
    new = [entry["version"] for entry in entries]
    return f"更新 {path.parent.name}: {', '.join(old)} -> {', '.join(new)}"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--resources", type=Path, required=True)
    parser.add_argument("--window-size", type=int, default=3)
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()
    for path in sorted(args.resources.iterdir()):
        manifest = path / "manifest.json"
        if not manifest.is_file():
            continue
        try:
            print(update_manifest(manifest, args.window_size, args.dry_run))
        except Exception as error:  # noqa: BLE001 - 单资源失败不应中断整批刷新
            print(f"失败 {path.name}: {error}", file=sys.stderr)


if __name__ == "__main__":
    main()

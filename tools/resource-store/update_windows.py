#!/usr/bin/env python3
"""从官方上游刷新资源清单的 latestVersionWindow。

只查询官方端点（registry.npmjs.org / pypi.org / 仓库 tag / 官网发布页）：
镜像只承运字节，新鲜度事实必须来自上游本身。支持的 source.type：
npm、pypi、git（refs/tags 窗口）、official_release_archive（latestFormat=regex）；
其余类型（acp_registry_binary、android_apk、official_release_archive 的
latestFormat=json 滑窗）跳过并报告。

archive 类型只在版本变化时下载制品计算 SHA-256（沿用已知版本的旧值），
保证 CI 每日运行的常规开销只是抓一次元数据页。

写入采用外科手术式文本替换：只重写 latestVersionWindow 数组块并沿用该文件
既有的条目键序与内联风格，清单其余字节保持不变。

用法：
  py -3 tools/resource-store/update_windows.py --resources assets/resources [--dry-run]
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
import sys
import urllib.request
from pathlib import Path

NPM_REGISTRY = "https://registry.npmjs.org"
PYPI_INDEX = "https://pypi.org/pypi"
USER_AGENT = "Kite-Resource-Store-Updater/1"
SEMVER = re.compile(r"^(\d+)\.(\d+)\.(\d+)$")
GIT_TAG = re.compile(r"^refs/tags/(v\d+\.\d+\.\d+)$")
GIT_TAG_PEELED = re.compile(r"^refs/tags/(v\d+\.\d+\.\d+)\^\{\}$")
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


def npm_latest_version(package: str) -> str:
    encoded = urllib.request.quote(package, safe="")
    document = fetch_json(f"{NPM_REGISTRY}/{encoded}")
    tag = (document.get("dist-tags") or {}).get("latest", "")
    return str(tag).strip()


def rewrite_latest_version(raw: str, latest: str) -> str:
    """只重写 official_command 清单里的 latestVersion 单值字段。"""
    pattern = re.compile(r'("latestVersion": ")([^"]*)(")')
    if pattern.search(raw):
        return pattern.sub(lambda m: m.group(1) + latest + m.group(3), raw, count=1)
    # 字段尚不存在：插到 source 块的 type 行之后
    newline = "\r\n" if "\r\n" in raw else "\n"
    type_line = re.compile(r'(' + newline + r'([ \t]+)"type": "official_command"(.*)' + newline + r')')
    match = type_line.search(raw)
    if match is None:
        raise ValueError("找不到 official_command source 块")
    indent = match.group(2)
    return raw[:match.end()] + f'{indent}"latestVersion": "{latest}",' + newline + raw[match.end():]


def git_window(repositories: list[str], size: int) -> list[dict]:
    """从仓库 tag 列表构造 {version, ref, commit} 窗口。

    按声明顺序逐个尝试（官方源优先，失败回退镜像）；commit 哈希本身
    是内容寻址，镜像与官方指向同一提交时窗口等价。
    优先采用 peeled（^{} 行的 commit，注解 tag 指向的真实提交）。
    """
    last_error: Exception | None = None
    shas: dict[str, str] = {}
    peeled: dict[str, str] = {}
    for repository in repositories:
        try:
            completed = subprocess.run(
                ["git", "ls-remote", "--tags", repository],
                capture_output=True, text=True, timeout=180,
            )
            if completed.returncode != 0:
                raise RuntimeError(f"git ls-remote 失败: {completed.stderr.strip()[:200]}")
        except Exception as error:  # noqa: BLE001 - 单仓库失败时回退下一个
            last_error = error
            continue
        for line in completed.stdout.splitlines():
            if "\t" not in line:
                continue
            sha, ref = line.split("\t", 1)
            match = GIT_TAG.match(ref)
            if match:
                shas[match.group(1)] = sha
                continue
            match = GIT_TAG_PEELED.match(ref)
            if match:
                peeled[match.group(1)] = sha
        if shas:
            break
    if not shas:
        raise RuntimeError(f"所有仓库都不可用: {last_error or '没有任何 tag'}")
    tags = sorted(shas, key=lambda tag: tuple(int(part) for part in tag[1:].split(".")), reverse=True)
    return [
        {"version": tag, "ref": tag, "commit": peeled.get(tag) or shas[tag]}
        for tag in tags[:size]
    ]


def fetch_text(url: str) -> str:
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=60) as response:
        return response.read().decode("utf-8", errors="replace")


def stream_sha256(url: str) -> str:
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    digest = hashlib.sha256()
    with urllib.request.urlopen(request, timeout=120) as response:
        while chunk := response.read(1 << 20):
            digest.update(chunk)
    return digest.hexdigest()


def archive_regex_window(source: dict, size: int) -> list[dict]:
    """从官网发布页抓版本 URL，构造 {version, artifact, url, sha256} 窗口。

    已在旧窗口中的版本沿用旧 SHA，只有新版本才下载制品计算，
    保证无变化时的日常刷新只花一次页面请求。
    """
    pattern = re.compile(source["latestRegex"])
    page = fetch_text(source["latestUrl"])
    versions: list[str] = []
    for match in pattern.finditer(page):
        version = match.group(1)
        if semver_key(version) and version not in versions:
            versions.append(version)
    versions.sort(key=semver_key, reverse=True)
    if not versions:
        raise RuntimeError("发布页没有匹配到任何稳定版本")
    existing = {entry["version"]: entry for entry in source.get("latestVersionWindow", [])}
    template = source.get("latestVersionWindow", [{}])[0]
    if not template.get("url") or not template.get("version"):
        raise RuntimeError("缺少可用的窗口模板条目")
    window: list[dict] = []
    for version in versions[:size]:
        if version in existing:
            window.append(existing[version])
            continue
        url = template["url"].replace(template["version"], version)
        artifact = template.get("artifact", "").replace(template["version"], version)
        window.append({
            "version": version,
            "artifact": artifact,
            "url": url,
            "sha256": stream_sha256(url),
        })
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
    if original_entries:
        # 沿用原窗口的键序（npm/pypi 的 artifact-version-integrity/sha256、
        # git 的 version-ref-commit、archive 的 version-artifact-url-sha256）。
        style = list(original_entries[0].keys())
    else:
        keep_artifact = any("artifact" in entry for entry in entries) or len(
            {entry.get("artifact", "") for entry in entries}
        ) > 1
        style = [key for key in ("artifact", "version", "integrity", "sha256")
                 if key == "artifact" and keep_artifact or key in entries[0] and key != "artifact"]
    missing = [key for key in style if any(key not in entry for entry in entries)]
    if missing:
        raise ValueError(f"新窗口条目缺少键 {missing}")
    body = newline.join(entry_indent + entry_text(entry, style) + "," for entry in entries[:-1])
    body += newline + entry_indent + entry_text(entries[-1], style)
    return (raw[:match.start()] + match.group(1) + newline + body + newline
            + base_indent + match.group(2) + raw[match.end():])


def git_step_repositories(manifest: dict) -> tuple[list[str], list[dict]]:
    """返回 (仓库列表, 原窗口条目)；窗口位于 actions.install[].steps[type=git]。"""
    for action in (manifest.get("actions", {}) or {}).get("install", []):
        for step in action.get("steps", []):
            if step.get("type") == "git" and step.get("latestVersionWindow"):
                repositories = step.get("repositories") or []
                if repositories:
                    return repositories, step["latestVersionWindow"]
    return [], []


def update_manifest(path: Path, size: int, dry_run: bool) -> str:
    raw = path.read_bytes().decode("utf-8")
    manifest = json.loads(raw)
    source = manifest.get("source", {})
    kind = source.get("type")
    package = source.get("package")
    entries: list[dict]
    original: list[dict]
    if kind in ("npm", "pypi") and package:
        original = source.get("latestVersionWindow", [])
        packages = [package] + list(source.get("companionPackages") or [])
        entries = []
        for name in packages:
            window = npm_window(name, size) if kind == "npm" else pypi_window(name, size)
            if not window:
                return f"失败 {path.parent.name}: {name} 无法从官方源取得可用稳定版本，保持原窗口"
            entries.extend(window)
    elif kind == "git":
        repositories, original = git_step_repositories(manifest)
        if not repositories:
            return f"跳过 {path.parent.name}: type=git（缺少带窗口的仓库步骤）"
        entries = git_window(repositories, size)
        if not entries:
            return f"失败 {path.parent.name}: {'、'.join(repositories)} 没有可用稳定 tag，保持原窗口"
    elif kind == "official_command" and package:
        # 官方命令直装：只维护最新版本号事实（App 端零网络读取）。
        original = source.get("latestVersion", "")
        latest = npm_latest_version(package)
        if not latest:
            return f"失败 {path.parent.name}: {package} 无法从官方 registry 取得最新版本，保持原值"
        if latest == original:
            return f"不变 {path.parent.name}: {latest}"
        if not dry_run:
            path.write_bytes(rewrite_latest_version(raw, latest).encode("utf-8"))
        return f"更新 {path.parent.name}: {original or '(空)'} -> {latest}"
    elif kind == "official_release_archive" and source.get("latestFormat") == "regex":
        original = source.get("latestVersionWindow", [])
        entries = archive_regex_window(source, size)
    else:
        detail = f"latestFormat={source.get('latestFormat')}" if kind == "official_release_archive" else f"type={kind}"
        return f"跳过 {path.parent.name}: {detail}（暂不支持自动刷新）"
    old = [entry.get("version") for entry in original]
    if [e["version"] for e in entries] == old:
        return f"不变 {path.parent.name}: {', '.join(old)}"
    if not dry_run:
        path.write_bytes(rewrite_window(raw, entries, original).encode("utf-8"))
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

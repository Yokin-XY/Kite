#!/usr/bin/env python3
"""从 Kite 内置资源定义构建一个确定性的远程资源目录快照。"""

from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import tempfile


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--resources", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--revision", type=int, required=True)
    parser.add_argument("--key-id", required=True)
    parser.add_argument("--channel", default="stable")
    return parser.parse_args()


def load_json(path: Path) -> object:
    with path.open("r", encoding="utf-8") as source:
        return json.load(source)


def main() -> None:
    args = parse_args()
    if args.revision < 1:
        raise SystemExit("revision 必须大于 0")
    manifests: dict[str, object] = {}
    for directory in sorted(path for path in args.resources.iterdir() if path.is_dir()):
        manifest_path = directory / "manifest.json"
        if not manifest_path.is_file():
            continue
        manifest = load_json(manifest_path)
        if not isinstance(manifest, dict) or manifest.get("id") != directory.name:
            raise SystemExit(f"资源目录与 manifest id 不一致：{directory.name}")
        manifests[directory.name] = manifest
    if not manifests:
        raise SystemExit("没有找到资源 manifest")
    snapshot = {
        "schemaVersion": 1,
        "channel": args.channel,
        "revision": args.revision,
        "keyId": args.key_id,
        "homeLayout": load_json(args.resources / "home.json"),
        "manifests": manifests,
    }
    payload = (json.dumps(snapshot, ensure_ascii=False, indent=2, sort_keys=True) + "\n").encode("utf-8")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary = tempfile.mkstemp(prefix=".pending-", dir=args.output.parent)
    try:
        with os.fdopen(descriptor, "wb") as target:
            target.write(payload)
            target.flush()
            os.fsync(target.fileno())
        os.replace(temporary, args.output)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


if __name__ == "__main__":
    main()

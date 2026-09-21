#!/usr/bin/env python
"""models.dev 内置快照生成器：TOML 源 → 精选 api.json 形状快照。

数据源：sst/models.dev（MIT）providers/*/provider.toml + models/*.toml。
产出与 models.dev /api.json 相同形状（App 端 ModelsDevProviderPresetRepository
现有解析逻辑直接消费，零新代码路径）。

精选规则（控制 APK 体积，只保留 Kite 六 Agent 用得上的部分）：
- 协议族：npm == @ai-sdk/openai-compatible 或 @ai-sdk/anthropic
  （openrouter 的 @openrouter/ai-sdk-provider 视为 OpenAI 兼容）；
- 模型：非 deprecated、输出模态含 text（聊天模型）、非嵌入/语音/重排序类
  名称标记（与 App 端 NON_CHAT_MODEL_MARKERS 同口径）；
- 字段：id/name/reasoning/limit/modalities/status —— 目录元数据不带价格。

用法：python tools/modelsdev/snapshot.py <repo_dir> <out_json>
仓库获取（国内可达）：
  curl -sL -o repo.zip https://ghproxy.net/https://github.com/sst/models.dev/archive/refs/heads/dev.zip
"""
import json
import sys
import tomllib
from pathlib import Path

NON_CHAT_MARKERS = (
    "embed", "rerank", "whisper", "tts", "speech", "audio", "voice",
    "image-gen", "dall-e", "stable-diffusion", "moderation", "bge-",
    "clip", "transcribe",
)


def is_chat_model(model_id: str, model: dict) -> bool:
    if str(model.get("status", "")).lower() == "deprecated":
        return False
    output = (model.get("modalities") or {}).get("output") or []
    output = [str(x).lower() for x in output]
    if output and "text" not in output:
        return False
    searchable = f"{model_id} {model.get('name', '')}".lower()
    return not any(marker in searchable for marker in NON_CHAT_MARKERS)


def protocol_kept(npm: str | None, provider_id: str) -> bool:
    if npm in ("@ai-sdk/openai-compatible", "@ai-sdk/anthropic"):
        return True
    if provider_id == "openrouter" and npm == "@openrouter/ai-sdk-provider":
        return True
    return False


def resolve_model_file(path: Path, depth: int = 0) -> Path | None:
    """zip 快照把 symlink 变成'相对路径文本'文件；递归解引用到真实 TOML。

    Windows 文件系统无法落地名含冒号的目标（如 amazon nova v1:0），解不开
    返回 None 跳过——数量极少且多为非目标协议族，可容忍。
    """
    if depth > 6:
        return None
    text = path.read_text(encoding="utf-8", errors="replace").strip()
    if "\n" not in text and text.startswith("../") and text.endswith(".toml"):
        target = (path.parent / text)
        if target.is_file():
            return resolve_model_file(target, depth + 1)
        return None
    return path


def main(repo_dir: str, out_path: str) -> None:
    providers_root = Path(repo_dir) / "providers"
    snapshot: dict = {}
    total_models = 0
    for provider_dir in sorted(providers_root.iterdir()):
        if not provider_dir.is_dir():
            continue
        provider_file = provider_dir / "provider.toml"
        if not provider_file.is_file():
            continue
        provider = tomllib.loads(provider_file.read_text(encoding="utf-8"))
        npm = provider.get("npm")
        provider_id = provider.get("id") or provider_dir.name
        if not protocol_kept(npm, provider_id):
            continue
        api = provider.get("api")
        if not isinstance(api, str) or not api.startswith("http"):
            continue
        models_out: dict = {}
        for model_file in sorted((provider_dir / "models").glob("*.toml")):
            source = resolve_model_file(model_file)
            if source is None:
                continue
            model_id = model_file.stem
            model = tomllib.loads(source.read_text(encoding="utf-8"))
            if not is_chat_model(model_id, model):
                continue
            entry: dict = {"name": model.get("name") or model_id}
            if "reasoning" in model:
                entry["reasoning"] = bool(model["reasoning"])
            limit = model.get("limit")
            if isinstance(limit, dict) and limit:
                flat = {}
                for key in ("context", "output"):
                    value = limit.get(key)
                    if isinstance(value, int) and value > 0:
                        flat[key] = value
                if flat:
                    entry["limit"] = flat
            modalities = model.get("modalities")
            if isinstance(modalities, dict) and modalities:
                compact = {}
                for key in ("input", "output"):
                    values = modalities.get(key)
                    if isinstance(values, list) and values:
                        compact[key] = [str(x) for x in values]
                if compact:
                    entry["modalities"] = compact
            models_out[model_id] = entry
        if not models_out:
            continue
        snapshot[provider_id] = {
            "id": provider_id,
            "name": provider.get("name") or provider_id,
            "api": api,
            "npm": npm,
            "models": models_out,
        }
        if provider.get("doc"):
            snapshot[provider_id]["doc"] = provider["doc"]
        total_models += len(models_out)

    payload = json.dumps(snapshot, ensure_ascii=False, separators=(",", ":"))
    Path(out_path).write_text(payload, encoding="utf-8")
    size = Path(out_path).stat().st_size
    print(f"providers={len(snapshot)} models={total_models} bytes={size} → {out_path}")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])

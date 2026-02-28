import json
import os
from typing import Any


def _expand_env(value: Any) -> Any:
    if isinstance(value, str):
        return os.path.expandvars(value)
    if isinstance(value, list):
        return [_expand_env(item) for item in value]
    if isinstance(value, dict):
        return {k: _expand_env(v) for k, v in value.items()}
    return value


def load_config(path: str) -> dict[str, Any]:
    with open(path, "r", encoding="utf-8") as fp:
        raw = json.load(fp)
    return _expand_env(raw)

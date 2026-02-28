"""Bootstrap sys.path so that `from utils.xxx import ...` works in every job."""
from __future__ import annotations

import sys
from pathlib import Path

_SPARK_ROOT = str(Path(__file__).resolve().parent.parent)
if _SPARK_ROOT not in sys.path:
    sys.path.append(_SPARK_ROOT)

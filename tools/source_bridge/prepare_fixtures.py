"""Prepare existing native fixtures for binding-aware official-name translation."""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools"))
from fixture_adapters import adapt_fixture

output = ROOT / "build/source-bridge/fixture-input"
output.mkdir(parents=True, exist_ok=True)
for source in ("tools/replays/ReplayFilmContextClientCheck.java", "tools/soft/SoftTransparencyClientCheck.java"):
    adapt_fixture("1.21.11", ROOT / source, output)
source = ROOT / "tools/modern/CasterDepthClientCheck.java"
(output / source.name).write_text(source.read_text(encoding="utf-8"), encoding="utf-8")

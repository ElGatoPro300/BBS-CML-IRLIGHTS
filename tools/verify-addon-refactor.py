"""Compare the extracted caster bounds with the pinned pre-refactor implementation."""
import argparse
from pathlib import Path
import subprocess
from client_check import ROOT, run_check, target_version

parser = argparse.ArgumentParser()
parser.add_argument("--mc")
args = parser.parse_args()
mc = target_version(args.mc)
output = ROOT / "build/targets" / mc / "refactor-baseline"
output.mkdir(parents=True, exist_ok=True)
source = subprocess.check_output(["git", "show", "9c93d2a:src/client/java/org/qualet/irl/light/shadow/IRLiteBbsCasterSource.java"], cwd=ROOT, text=True, encoding="utf-8")
source = source.replace("IRLiteBbsCasterSource", "LegacyBbsCasterSource")
legacy = output / "LegacyBbsCasterSource.java"
legacy.write_text(source, encoding="utf-8")
run_check(mc, "addon-refactor", "tools/refactor/AddonRefactorClientCheck.java", "AddonRefactorClientCheck", "PASS: addon refactor native contracts", extra_sources=[legacy])

"""Compile the real domain sources with an empty engine classpath and run behavior contracts."""
from pathlib import Path
import subprocess

root = Path(__file__).resolve().parents[1]
output = root / "build/domain-check"
output.mkdir(parents=True, exist_ok=True)
sources = sorted((root / "src/domain/java").rglob("*.java")) + [root / "tools/domain/DomainRegression.java"]
args = output / "compile.args"
values = ["--release", "17", "-encoding", "UTF-8", "-cp", str(output), "-d", str(output)] + [str(p) for p in sources]
args.write_text("\n".join('"' + value.replace("\\", "/") + '"' for value in values), encoding="utf-8")
subprocess.run(["javac", "@" + str(args)], check=True)
subprocess.run(["java", "-cp", str(output), "DomainRegression"], check=True)

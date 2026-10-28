"""Run GPU compatibility checks with an existing IRLights-patched Complementary shaderpack."""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument("--shaderpack", type=Path, required=True)
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
source_pack = args.shaderpack.resolve(strict=True)
run = root / "build/soft-transparency-run"
name = "compatibility-pack.zip" if source_pack.is_file() else "compatibility-pack"
destination = run / "shaderpacks" / name
destination.parent.mkdir(parents=True, exist_ok=True)
if source_pack.is_dir():
    shutil.copytree(source_pack, destination, dirs_exist_ok=True)
else:
    shutil.copy2(source_pack, destination)
gradle = str(root / ("gradlew.bat" if os.name == "nt" else "gradlew"))
subprocess.run([gradle, "build", "profileTestClasspath", "-I", "tools/profiles/classpath.init.gradle", "--console=plain"], cwd=root, check=True)
classpath = (root / "build/profiles-bbs-test/classpath.txt").read_text(encoding="utf-8")
output = root / "build/soft-transparency-check"
output.mkdir(parents=True, exist_ok=True)
source = root / "tools/soft/SoftTransparencyClientCheck.java"
compile_args = output / "compile.args"
compile_args.write_text("\n".join('"' + value.replace("\\", "/") + '"' for value in ["--release", "17", "-encoding", "UTF-8", "-proc:none", "-cp", classpath, "-d", str(output), str(source)]), encoding="utf-8")
subprocess.run([shutil.which("javac") or "javac", "@" + str(compile_args)], cwd=root, check=True)
jar = output / "soft-check.jar"
with zipfile.ZipFile(jar, "w") as archive:
    for item in output.glob("SoftTransparencyClientCheck*.class"):
        archive.write(item, item.name)
    archive.writestr("fabric.mod.json", json.dumps({"schemaVersion": 1, "id": "irl_soft_check", "version": "1.0.0", "environment": "client", "entrypoints": {"client": ["SoftTransparencyClientCheck"]}, "depends": {"irlite": "*"}}))
env = dict(os.environ)
env["JAVA_TOOL_OPTIONS"] = (env.get("JAVA_TOOL_OPTIONS", "") + " -Dfabric.addMods=" + jar.as_posix() + " -Dirlite.softCheck.shaderpack=" + name).strip()
logfile = output / "client.log"
with logfile.open("w", encoding="utf-8") as log:
    result = subprocess.run([gradle, "runClient", "-PrunDir=build/soft-transparency-run", "--console=plain"], cwd=root, env=env, stdout=log, stderr=subprocess.STDOUT)
text = logfile.read_text(encoding="utf-8", errors="replace")
for line in text.splitlines():
    if "PASS:" in line or "FAIL:" in line or "CHECKED:" in line:
        print(line)
if result.returncode or "FAIL:" in text or "PASS: soft transparency GPU blend" not in text:
    raise SystemExit("Soft transparency check failed; see " + str(logfile))

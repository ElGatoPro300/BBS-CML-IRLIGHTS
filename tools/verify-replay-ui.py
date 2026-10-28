"""Build and run the opt-in replay-context UI regression in an isolated Minecraft client."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import zipfile

root = Path(__file__).resolve().parents[1]
gradle = str(root / ("gradlew.bat" if os.name == "nt" else "gradlew"))
subprocess.run([gradle, "build", "profileTestClasspath", "-I", "tools/profiles/classpath.init.gradle", "--console=plain"], cwd=root, check=True)
classpath = (root / "build/profiles-bbs-test/classpath.txt").read_text(encoding="utf-8")
output = root / "build/replay-ui-check"
output.mkdir(parents=True, exist_ok=True)
args = output / "compile.args"
source = root / "tools/replays/ReplayFilmContextClientCheck.java"
args.write_text("\n".join('"' + value.replace("\\", "/") + '"' for value in ["--release", "17", "-encoding", "UTF-8", "-proc:none", "-cp", classpath, "-d", str(output), str(source)]), encoding="utf-8")
subprocess.run([shutil.which("javac") or "javac", "@" + str(args)], cwd=root, check=True)
jar = output / "replay-ui-check.jar"
with zipfile.ZipFile(jar, "w") as archive:
    for item in output.glob("ReplayFilmContextClientCheck*.class"):
        archive.write(item, item.name)
    archive.writestr("fabric.mod.json", json.dumps({"schemaVersion": 1, "id": "irl_replay_ui_check", "version": "1.0.0", "environment": "client", "entrypoints": {"client": ["ReplayFilmContextClientCheck"]}, "depends": {"irlite": "*"}}))
env = dict(os.environ)
env["JAVA_TOOL_OPTIONS"] = (env.get("JAVA_TOOL_OPTIONS", "") + " -Dfabric.addMods=" + jar.as_posix()).strip()
logfile = output / "client.log"
with logfile.open("w", encoding="utf-8") as log:
    result = subprocess.run([gradle, "runClient", "-PrunDir=build/replay-ui-run", "--console=plain"], cwd=root, env=env, stdout=log, stderr=subprocess.STDOUT)
text = logfile.read_text(encoding="utf-8", errors="replace")
for line in text.splitlines():
    if "PASS:" in line or "FAIL:" in line:
        print(line)
if result.returncode or "FAIL:" in text or "PASS: replay film context client regression" not in text:
    raise SystemExit("Replay UI check failed; see " + str(logfile))

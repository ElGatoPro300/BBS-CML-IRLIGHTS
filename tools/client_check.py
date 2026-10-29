"""Shared opt-in native client fixture runner, isolated by Minecraft target."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import zipfile
from fixture_adapters import adapt_fixture

ROOT = Path(__file__).resolve().parents[1]


def target_version(requested):
    if requested:
        return requested
    return next(line.split("=", 1)[1].strip() for line in (ROOT / "gradle.properties").read_text().splitlines() if line.startswith("minecraft_version="))


def run_check(mc, name, source, entrypoint, marker, shaderpack=None, extra_sources=()):
    mc = target_version(mc)
    build = ROOT / "build/targets" / mc
    output = build / (name + "-check")
    output.mkdir(parents=True, exist_ok=True)
    run = build / (name + "-run")
    options = []
    if shaderpack:
        source_pack = shaderpack.resolve(strict=True)
        pack_name = "compatibility-pack.zip" if source_pack.is_file() else "compatibility-pack"
        destination = run / "shaderpacks" / pack_name
        destination.parent.mkdir(parents=True, exist_ok=True)
        if source_pack.is_dir():
            shutil.copytree(source_pack, destination, dirs_exist_ok=True)
        else:
            shutil.copy2(source_pack, destination)
        options.append("-Dirlite.softCheck.shaderpack=" + pack_name)
    gradle = str(ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew"))
    command = [gradle, "-Pmc=" + mc, "--project-cache-dir", str(ROOT / ".gradle/targets" / mc), "--console=plain"]
    subprocess.run(command + ["build", "profileTestClasspath", "-I", "tools/profiles/classpath.init.gradle"], cwd=ROOT, check=True)
    classpath = (build / "profiles-bbs-test/classpath.txt").read_text(encoding="utf-8")
    args = output / "compile.args"
    spec = json.loads((ROOT / "gradle/minecraft-targets.json").read_text())[mc]
    source = adapt_fixture(mc, ROOT / source, output)
    values = ["--release", str(spec["java"]), "-encoding", "UTF-8", "-proc:none", "-cp", classpath, "-d", str(output), str(ROOT / source)] + [str(path) for path in extra_sources]
    args.write_text("\n".join('"' + value.replace("\\", "/") + '"' for value in values), encoding="utf-8")
    subprocess.run([shutil.which("javac") or "javac", "@" + str(args)], cwd=ROOT, check=True)
    jar = output / (name + ".jar")
    with zipfile.ZipFile(jar, "w") as archive:
        for item in output.rglob("*.class"):
            archive.write(item, item.relative_to(output).as_posix())
        archive.writestr("fabric.mod.json", json.dumps({"schemaVersion": 1, "id": "irl_" + name.replace("-", "_"), "version": "1.0.0", "environment": "client", "entrypoints": {"client": [entrypoint]}, "depends": {"irlite": "*"}}))
    env = dict(os.environ)
    options.insert(0, "-Dfabric.addMods=" + jar.as_posix())
    env["JAVA_TOOL_OPTIONS"] = (env.get("JAVA_TOOL_OPTIONS", "") + " " + " ".join(options)).strip()
    logfile = output / "client.log"
    with logfile.open("w", encoding="utf-8") as log:
        result = subprocess.run(command + ["runClient", "-PrunDir=" + run.as_posix()], cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT)
    text = logfile.read_text(encoding="utf-8", errors="replace")
    for line in text.splitlines():
        if any(word in line for word in ["PASS:", "FAIL:", "CHECKED:"]):
            print(line)
    if result.returncode or "FAIL:" in text or marker not in text:
        raise SystemExit("Client check failed; see " + str(logfile))
    return logfile

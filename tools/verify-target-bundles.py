"""Verify exact host versions, bytecode and embedded libraries in shared-source target artifacts."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import zipfile

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument("--mc", action="append")
args = parser.parse_args()
targets = json.loads((root / "gradle/minecraft-targets.json").read_text())
version = next(line.split("=", 1)[1].strip() for line in (root / "gradle.properties").read_text().splitlines() if line.startswith("mod_version="))
reports = []
for mc in args.mc or [mc for mc, spec in targets.items() if spec["status"] == "available"]:
    spec = targets[mc]
    jar = root / "build/targets" / mc / "libs" / ("bbs-cml-irlights-" + version + "-" + mc + ".jar")
    with zipfile.ZipFile(jar) as archive:
        metadata = json.loads(archive.read("fabric.mod.json"))
        assert metadata["version"] == version + "-" + mc
        assert metadata["depends"]["minecraft"] == "=" + mc
        assert not any(name.startswith("META-INF/jars/bbs-") for name in archive.namelist())
        cores = [name for name in archive.namelist() if name.startswith("META-INF/jars/irl-core")]
        assert len(cores) == 1, cores
        core_bytes = archive.read(cores[0])
        local = [file for file in (root / "lib" / mc).glob("*.jar") if not file.name.endswith("-sources.jar") and json.loads(zipfile.ZipFile(file).read("fabric.mod.json"))["id"] == "irl-core"]
        assert len(local) == 1 and core_bytes == local[0].read_bytes(), "Embedded Core differs from selected library"
        for name in archive.namelist():
            if name.endswith(".class"):
                assert int.from_bytes(archive.read(name)[6:8], "big") <= spec["java"] + 44, name
        for config in ["irlite.mixins.json", "irlite.client.mixins.json"]:
            mixins = json.loads(archive.read(config))
            assert mixins["compatibilityLevel"] == "JAVA_" + str(spec["java"])
            for name in mixins.get("mixins", []) + mixins.get("client", []):
                assert (mixins["package"] + "." + name).replace(".", "/") + ".class" in archive.namelist(), name
        patches = list((root / "patches").glob("*.irlights"))
        assert len(patches) == 7
        for patch in patches:
            assert archive.read("assets/irlite/patches/" + patch.name) == patch.read_bytes(), patch.name
        assert "qualet/irlite/domain/replay/ReplayFrameState.class" in archive.namelist()
        reports.append({"minecraft": mc, "jar": str(jar), "sha256": hashlib.sha256(jar.read_bytes()).hexdigest(), "coreMatches": True, "status": "PASS"})
    print("PASS:", mc, "bundle, bytecode, exact Core, mixins and metadata")
output = root / "build/refactor-bundles.json"
output.write_text(json.dumps(reports, indent=2) + "\n")

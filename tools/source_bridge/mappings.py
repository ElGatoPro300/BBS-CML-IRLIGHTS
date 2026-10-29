"""Build an exact Yarn -> Mojang symbol map for the pinned namespace bridge."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import urllib.request

VERSION = "1.21.11"
YARN = "1.21.11+build.6"
PRIMITIVES = dict(zip(["void", "boolean", "byte", "char", "short", "int", "long", "float", "double"], "VZBCSIJFD"))


def build(cache, output):
    info = json.loads((cache / VERSION / "mojang_minecraft_info.json").read_text())
    download = info["downloads"]["client_mappings"]
    destination = output.parent / "mojang-client.txt"
    destination.parent.mkdir(parents=True, exist_ok=True)
    if not destination.exists() or hashlib.sha1(destination.read_bytes()).hexdigest() != download["sha1"]:
        request = urllib.request.Request(download["url"], headers={"User-Agent": "IRLights-source-bridge"})
        with urllib.request.urlopen(request, timeout=60) as response:
            data = response.read()
        if hashlib.sha1(data).hexdigest() != download["sha1"]:
            raise ValueError("Mojang client mapping checksum mismatch")
        destination.write_bytes(data)
    lines = destination.read_text(encoding="utf-8").splitlines()
    official_to_obfuscated = {}
    for line in lines:
        if line and not line.startswith((" ", "#")) and " -> " in line:
            source, target = line.removesuffix(":").split(" -> ")
            official_to_obfuscated[source] = target.replace(".", "/")

    def descriptor(java_type):
        arrays = 0
        while java_type.endswith("[]"):
            arrays += 1
            java_type = java_type[:-2]
        base = PRIMITIVES.get(java_type)
        if base is None:
            base = "L" + official_to_obfuscated.get(java_type, java_type.replace(".", "/")) + ";"
        return "[" * arrays + base

    members = {}
    current = None
    for line in lines:
        if not line or line.startswith("#"):
            continue
        if not line.startswith(" "):
            current = line.removesuffix(":").split(" -> ")[1].replace(".", "/")
            members[current] = {"methods": {}, "fields": {}}
            continue
        if " -> " not in line:
            continue
        source, target = line.strip().split(" -> ")
        source = re.sub(r"^\d+:\d+:", "", source)
        method = re.fullmatch(r"(.+?) ([^ ]+)\((.*?)\)(?::\d+(?::\d+)?)?", source)
        if method:
            returns, name, arguments = method.groups()
            desc = "(" + "".join(descriptor(arg) for arg in arguments.split(",") if arg) + ")" + descriptor(returns)
            members[current]["methods"][(target, desc)] = name
        else:
            java_type, name = source.rsplit(" ", 1)
            members[current]["fields"][(target, descriptor(java_type))] = name

    tiny = cache / VERSION / ("net.fabricmc.yarn.1_21_11." + YARN + "-v2") / "mappings.tiny"
    rows = tiny.read_text(encoding="utf-8").splitlines()
    if rows[0] != "tiny\t2\t0\tofficial\tintermediary\tnamed":
        raise ValueError("Unexpected bridge mapping namespaces")
    classes = {}
    for row in rows[1:]:
        if row.startswith("c\t"):
            _, obfuscated, intermediary, named = row.split("\t")
            classes[obfuscated] = named
    obfuscated_to_official = {value: name.replace(".", "/") for name, value in official_to_obfuscated.items()}

    def named_descriptor(value):
        return re.sub(r"L([^;]+);", lambda match: "L" + classes.get(match[1], match[1]) + ";", value)

    result = []
    current = None
    current_obfuscated = None
    for row in rows[1:]:
        if row.startswith("c\t"):
            _, obfuscated, intermediary, named = row.split("\t")
            current_obfuscated = obfuscated
            current = {"source": named, "target": obfuscated_to_official.get(obfuscated, obfuscated), "methods": [], "fields": []}
            result.append(current)
        elif row.startswith(("\tm\t", "\tf\t")):
            _, kind, desc, obfuscated, intermediary, named = row.split("\t")
            category = "methods" if kind == "m" else "fields"
            target = members.get(current_obfuscated, {}).get(category, {}).get((obfuscated, desc))
            if target is not None and named != target:
                current[category].append({"source": named, "target": target, "descriptor": named_descriptor(desc)})
    output.write_text(json.dumps(result, separators=(",", ":")), encoding="utf-8")
    print("Bridge mapping:", len(result), "classes; checksum verified for", VERSION)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--cache", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    build(args.cache, args.output)

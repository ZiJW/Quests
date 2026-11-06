#!/usr/bin/env python3
"""Compile the dependency-free Paper probe against supplied Quests and Bukkit API jars."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument("--quests-jar", required=True, type=Path)
parser.add_argument("--bukkit-api-jar", required=True, type=Path)
parser.add_argument("--output", default="NameLookupProbe.jar", type=Path)
parser.add_argument("--java-home", type=Path)
arguments = parser.parse_args()
root = Path(__file__).resolve().parent
javac = str(arguments.java_home / "bin/javac") if arguments.java_home else shutil.which("javac")
if not javac:
    raise SystemExit("A JDK with javac is required; Paper 1.21.4 uses Java 21.")
with tempfile.TemporaryDirectory(prefix="quests-name-lookup-build-") as temporary:
    classes = Path(temporary)
    subprocess.run([javac, "--release", "8", "-proc:none", "-cp",
                    os.pathsep.join([str(arguments.quests_jar.resolve()), str(arguments.bukkit_api_jar.resolve())]),
                    "-d", str(classes)] + [str(p) for p in sorted((root / "src").glob("*.java"))], check=True)
    with zipfile.ZipFile(arguments.output, "w", compression=zipfile.ZIP_DEFLATED) as jar:
        for path in classes.rglob("*.class"):
            jar.write(path, str(path.relative_to(classes)))
        jar.write(root / "plugin.yml", "plugin.yml")
print(arguments.output.resolve())

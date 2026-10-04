"""Reject packaged Material classes and dangling references after dependency exclusion."""
import argparse
import re
import zipfile


parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("apks", nargs="+")
args = parser.parse_args()
prohibited = re.compile(rb"L(?:androidx/compose/material(?:3)?/|com/google/android/material/)[A-Za-z0-9_/$]+;")
failures = []
for apk in args.apks:
    with zipfile.ZipFile(apk) as archive:
        dex_files = [name for name in archive.namelist() if name.endswith(".dex")]
        if not dex_files:
            raise SystemExit(f"No DEX files found: {apk}")
        references = sorted({
            match.decode("ascii")
            for name in dex_files
            for match in prohibited.findall(archive.read(name))
        })
        if references:
            failures.append(f"{apk}: " + ", ".join(references))
        else:
            print(f"PASS: {apk} contains no Material classes or references")
if failures:
    raise SystemExit("Material is forbidden in packaged APKs:\n" + "\n".join(failures))

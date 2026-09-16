"""Record and check the exact plugin ZIP passed between validation and publication."""

import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import shutil
import xml.etree.ElementTree as ET
import zipfile

WASM_PATH = "wasm/tree-sitter/tree-sitter.wasm"
ARCHIVE_NAME = "verified-plugin.zip"


def digest(data):
    return hashlib.sha256(data).hexdigest()


def plugin_contents(archive):
    matches = []
    with zipfile.ZipFile(archive) as plugin:
        for name in plugin.namelist():
            if name.endswith(".jar"):
                with zipfile.ZipFile(io.BytesIO(plugin.read(name))) as jar:
                    if WASM_PATH in jar.namelist():
                        descriptor = ET.fromstring(jar.read("META-INF/plugin.xml"))
                        matches.append((digest(jar.read(WASM_PATH)), descriptor.findtext("version")))
    if len(matches) != 1:
        raise ValueError("Expected one plugin JAR containing the WASM resource")
    return matches[0]


def report_hashes(directory):
    return {str(p.relative_to(directory)): digest(p.read_bytes()) for p in sorted(directory.rglob("*")) if p.is_file()}


def current_verifier_reports(directory, version):
    reports = {}
    verdicts = {}
    for verdict in sorted(directory.glob(f"*/plugins/com.livteam.jsoninja/{version}/verification-verdict.txt")):
        result = verdict.read_text().strip()
        if not result.startswith("Compatible"):
            raise ValueError(f"Plugin verification failed: {result}")
        ide_directory = verdict.parents[3]
        verdicts[ide_directory.name] = result
        files = [p for p in verdict.parent.rglob("*") if p.is_file()]
        if (ide_directory / "report.html").is_file():
            files.append(ide_directory / "report.html")
        reports.update({str(p.relative_to(directory)): digest(p.read_bytes()) for p in sorted(files)})
    if not verdicts:
        raise ValueError("Missing verifier verdicts for the packaged plugin version")
    return reports, verdicts


def check_bundle(bundle, commit, tag):
    metadata = json.loads((bundle / "provenance.json").read_text())
    if metadata["archive_name"] != ARCHIVE_NAME:
        raise ValueError("Unexpected publisher archive name")
    if metadata["commit"] != commit or metadata["tag"] != tag:
        raise ValueError("Artifact commit/tag does not match the checked-out release")
    archive = bundle / ARCHIVE_NAME
    if digest(archive.read_bytes()) != metadata["archive_sha256"]:
        raise ValueError("Plugin archive SHA-256 mismatch")
    wasm_hash, version = plugin_contents(archive)
    if wasm_hash != metadata["wasm_sha256"] or wasm_hash != metadata["tested_wasm_sha256"]:
        raise ValueError("Packaged and tested WASM SHA-256 mismatch")
    if version != metadata["version"] or tag and tag != "v" + version:
        raise ValueError("Plugin version/tag mismatch")
    if metadata["verifier_result"] != "passed":
        raise ValueError("Plugin verifier did not pass")
    reports = report_hashes(bundle / "verifier-reports")
    if not reports or reports != metadata["verifier_reports"]:
        raise ValueError("Verifier report SHA-256 mismatch or missing reports")
    _, verdicts = current_verifier_reports(bundle / "verifier-reports", version)
    if verdicts != metadata["verified_ides"]:
        raise ValueError("Verified IDE builds do not match the provenance record")
    if digest((bundle / "toolchains.txt").read_bytes()) != metadata["toolchains_sha256"]:
        raise ValueError("Toolchain record SHA-256 mismatch")
    return metadata


def create_bundle(args):
    wasm_hash, version = plugin_contents(args.archive)
    if args.prior_bundle:
        metadata = check_bundle(args.prior_bundle, args.commit, args.tag)
        tested_hash = metadata["tested_wasm_sha256"]
        toolchains = (args.prior_bundle / "toolchains.txt").read_bytes()
        if args.toolchains:
            toolchains += b"\nFinal archive verification toolchains\n" + args.toolchains.read_bytes()
    else:
        tested_hashes = []
        for path in args.tested_plugin_dir.rglob("*.jar"):
            with zipfile.ZipFile(path) as jar:
                if WASM_PATH in jar.namelist():
                    tested_hashes.append(digest(jar.read(WASM_PATH)))
        if len(tested_hashes) != 1 or tested_hashes[0] != digest(args.wasm.read_bytes()):
            raise ValueError("Test sandbox WASM does not match the built resource")
        tested_hash = tested_hashes[0]
        toolchains = args.toolchains.read_bytes()
        metadata = {
            "commit": args.commit,
            "tag": args.tag,
            "producer_run_id": os.environ.get("GITHUB_RUN_ID", "local"),
            "producer_run_attempt": os.environ.get("GITHUB_RUN_ATTEMPT", "local"),
        }
    if wasm_hash != tested_hash:
        raise ValueError("Selected archive does not contain the tested WASM")
    if args.tag and args.tag != "v" + version:
        raise ValueError("Tag does not match the packaged version")
    reports, verdicts = current_verifier_reports(args.verifier_reports, version)
    args.output.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(args.archive, args.output / ARCHIVE_NAME)
    (args.output / "toolchains.txt").write_bytes(toolchains)
    for relative in reports:
        destination = args.output / "verifier-reports" / relative
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(args.verifier_reports / relative, destination)
    metadata.update({
        "version": version,
        "archive_name": ARCHIVE_NAME,
        "producer_archive_name": args.archive.name,
        "archive_sha256": digest(args.archive.read_bytes()),
        "wasm_sha256": wasm_hash,
        "tested_wasm_sha256": tested_hash,
        "toolchains_sha256": digest(toolchains),
        "verifier_result": "passed",
        "verified_ides": verdicts,
        "verifier_reports": reports,
    })
    (args.output / "provenance.json").write_text(json.dumps(metadata, indent=2) + "\n")
    check_bundle(args.output, args.commit, args.tag)
    print(json.dumps(metadata, indent=2))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    for name in ("create", "check"):
        command = commands.add_parser(name)
        command.add_argument("--commit", required=True)
        command.add_argument("--tag", default="")
        if name == "check":
            command.add_argument("--bundle", type=Path, required=True)
        else:
            command.add_argument("--archive", type=Path, required=True)
            command.add_argument("--output", type=Path, required=True)
            command.add_argument("--verifier-reports", type=Path, required=True)
            command.add_argument("--prior-bundle", type=Path)
            command.add_argument("--tested-plugin-dir", type=Path)
            command.add_argument("--wasm", type=Path)
            command.add_argument("--toolchains", type=Path)
    args = parser.parse_args()
    if args.command == "check":
        check_bundle(args.bundle, args.commit, args.tag)
        print("Verified archive, source identity, WASM, toolchains, and verifier reports")
    else:
        if not args.prior_bundle and not all((args.tested_plugin_dir, args.wasm, args.toolchains)):
            parser.error("create requires --prior-bundle or --tested-plugin-dir, --wasm, and --toolchains")
        create_bundle(args)


if __name__ == "__main__":
    main()

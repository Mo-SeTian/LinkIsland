import hashlib
import json
import os
from pathlib import Path
import re
import shutil


output = Path("artifacts")
output.mkdir(exist_ok=True)
checksums = []
versions = set()
for variant, label, package in (
    ("debug", "Debug", "dev.linkisland.app.debug"),
    ("release", "Release", "dev.linkisland.app"),
):
    folder = Path("app/build/outputs/apk") / variant
    metadata = json.loads((folder / "output-metadata.json").read_text())
    assert metadata["applicationId"] == package, "Unexpected application ID"
    assert len(metadata["elements"]) == 1, "Expected one universal APK per variant"
    entry = metadata["elements"][0]
    version = entry["versionName"]
    assert re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9.+_-]*", version), "Invalid version name"
    versions.add(version)
    apk = folder / entry["outputFile"]
    content = apk.read_bytes()
    short_md5 = hashlib.md5(content, usedforsecurity=False).hexdigest()[:8]
    name = f"LinkIsland-{version}-{label}-{short_md5}.apk"
    shutil.copy2(apk, output / name)
    if variant == "release":
        mapping = Path("app/build/outputs/mapping/release/mapping.txt")
        assert mapping.is_file(), "Missing Release R8 mapping"
        (output / "mapping").mkdir(exist_ok=True)
        shutil.copy2(mapping, output / "mapping" / "Release.txt")
    checksums.append(f"{hashlib.sha256(content).hexdigest()}  {name}\n")
    print(name)
assert len(versions) == 1, "Debug and Release versions must match"
(output / "SHA256SUMS.txt").write_text("".join(checksums))
version = versions.pop()
repo = os.environ.get("GITHUB_REPOSITORY", "Mo-SeTian/LinkIsland")
commit = os.environ.get("GITHUB_SHA", "local")
run_id = os.environ.get("GITHUB_RUN_ID", "")
notes = f"# LinkIsland {version}\n\n[项目仓库](https://github.com/{repo})\n\n提交：`{commit}`\n\n"
if run_id:
    notes += f"[构建记录](https://github.com/{repo}/actions/runs/{run_id})\n\n"
notes += "Debug：dev.linkisland.app.debug，可与 Release 共存。\n\nRelease：dev.linkisland.app。首次替换旧本地测试版时可能需要卸载旧版，因为签名已变更。\n\n文件名末尾为 MD5 前 8 位，完整 SHA-256 校验值随 GitHub 构建产物提供。\n"
(output / "build-notes.md").write_text(notes)
if os.environ.get("GITHUB_OUTPUT"):
    with open(os.environ["GITHUB_OUTPUT"], "a") as stream:
        stream.write(f"version={version}\n")

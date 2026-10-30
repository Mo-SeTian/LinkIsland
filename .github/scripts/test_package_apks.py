import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


SCRIPT = Path(__file__).with_name("package_apks.py").resolve()


class PackageNotesTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.git("init", "-q", "-b", "main")
        self.git("config", "user.name", "构建测试")
        self.git("config", "user.email", "fixture@example.invalid")
        self.base = self.commit("初始提交")
        for variant, package in (("debug", "dev.linkisland.app.debug"), ("release", "dev.linkisland.app")):
            folder = self.root / f"app/build/outputs/apk/{variant}"
            folder.mkdir(parents=True)
            (folder / "fixture.apk").write_bytes(f"test fixture {variant}".encode())
            (folder / "output-metadata.json").write_text(json.dumps({
                "applicationId": package,
                "elements": [{"versionName": "1.0.0-test", "outputFile": "fixture.apk"}],
            }))
        mapping = self.root / "app/build/outputs/mapping/release/mapping.txt"
        mapping.parent.mkdir(parents=True)
        mapping.write_text("test fixture mapping")

    def git(self, *args):
        return subprocess.check_output(["git", *args], cwd=self.root, text=True).strip()

    def commit(self, message):
        self.git("commit", "--allow-empty", "-qm", message)
        return self.git("rev-parse", "HEAD")

    def notes(self, event_name, head, before):
        event = self.root / "event.json"
        event.write_text(json.dumps({"before": before}))
        env = dict(os.environ, GITHUB_EVENT_NAME=event_name, GITHUB_EVENT_PATH=str(event),
                   GITHUB_SHA=head, GITHUB_REPOSITORY="example/fixture", GITHUB_RUN_ID="123")
        env.pop("GITHUB_OUTPUT", None)
        subprocess.run([sys.executable, str(SCRIPT)], cwd=self.root, env=env,
                       check=True, capture_output=True, text=True)
        self.assertEqual(len(list((self.root / "artifacts").glob("*.apk"))), 2)
        return (self.root / "artifacts/build-notes.md").read_text()

    def test_push_includes_each_commit_and_full_chinese_message(self):
        first = self.commit("优化后台监听\n\n保留两秒兜底检查。")
        last = self.commit("推送携带提交日志\n\n保留原文：`代码`、$()、中文。")
        notes = self.notes("push", last, self.base)
        self.assertIn("## 提交日志", notes)
        self.assertIn("优化后台监听", notes)
        self.assertIn("> 保留两秒兜底检查。", notes)
        self.assertIn("> 保留原文：`代码`、$()、中文。", notes)
        self.assertIn(f"https://github.com/example/fixture/commit/{first}", notes)
        self.assertIn(f"https://github.com/example/fixture/commit/{last}", notes)
        self.assertLess(notes.index(first), notes.index(last, notes.index("## 提交日志")))
        self.assertNotIn("初始提交", notes)

    def test_manual_build_only_includes_current_commit(self):
        self.commit("此前的提交")
        head = self.commit("手动构建当前版本")
        notes = self.notes("workflow_dispatch", head, self.base)
        self.assertIn("手动构建当前版本", notes)
        self.assertNotIn("此前的提交", notes)

    def test_first_push_includes_current_commit(self):
        head = self.commit("首次推送")
        notes = self.notes("push", head, "0" * 40)
        self.assertIn("首次推送", notes)
        self.assertNotIn("初始提交", notes)


if __name__ == "__main__":
    unittest.main()

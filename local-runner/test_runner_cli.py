from pathlib import Path
from tempfile import TemporaryDirectory
from unittest.mock import patch
import unittest

import runner
from runner import parse_args


class RunnerCliTest(unittest.TestCase):
    def test_browser_starts_minimized_by_default(self) -> None:
        with patch.dict("os.environ", {}, clear=True), patch("sys.argv", ["runner.py"]):
            self.assertTrue(parse_args().start_minimized)

    def test_browser_can_be_kept_visible_from_command_line(self) -> None:
        with patch.dict("os.environ", {"RUNNER_START_MINIMIZED": "true"}), patch(
            "sys.argv", ["runner.py", "--no-start-minimized"]
        ):
            self.assertFalse(parse_args().start_minimized)

    def test_browser_minimized_setting_uses_environment(self) -> None:
        with patch.dict("os.environ", {"RUNNER_START_MINIMIZED": "false"}), patch(
            "sys.argv", ["runner.py"]
        ):
            self.assertFalse(parse_args().start_minimized)

    def test_download_directory_uses_environment(self) -> None:
        with patch.dict("os.environ", {"RUNNER_DOWNLOAD_DIR": "/tmp/ambient-custom"}), patch(
            "sys.argv", ["runner.py"]
        ):
            self.assertEqual(parse_args().download_dir, Path("/tmp/ambient-custom"))

    def test_command_line_download_directory_overrides_environment(self) -> None:
        with patch.dict("os.environ", {"RUNNER_DOWNLOAD_DIR": "/tmp/from-env"}), patch(
            "sys.argv", ["runner.py", "--download-dir", "/tmp/from-cli"]
        ):
            self.assertEqual(parse_args().download_dir, Path("/tmp/from-cli"))

    def test_loads_local_env_without_overwriting_shell_value(self) -> None:
        with TemporaryDirectory() as directory:
            Path(directory, ".env").write_text(
                "RUNNER_DOWNLOAD_DIR=/tmp/from-file\nRUNNER_ID=from-file\nRUNNER_START_MINIMIZED=false\n",
                encoding="utf-8",
            )
            with patch.object(runner, "RUNNER_DIRECTORY", Path(directory)), patch.dict(
                "os.environ", {"RUNNER_ID": "from-shell"}, clear=True
            ):
                runner.load_runner_environment()
                self.assertEqual(runner.os.environ["RUNNER_DOWNLOAD_DIR"], "/tmp/from-file")
                self.assertEqual(runner.os.environ["RUNNER_ID"], "from-shell")
                self.assertEqual(runner.os.environ["RUNNER_START_MINIMIZED"], "false")


if __name__ == "__main__":
    unittest.main()

from pathlib import Path
from tempfile import TemporaryDirectory
from unittest.mock import patch
import unittest

import runner
from runner import parse_args


class RunnerCliTest(unittest.TestCase):
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
                "RUNNER_DOWNLOAD_DIR=/tmp/from-file\nRUNNER_ID=from-file\n",
                encoding="utf-8",
            )
            with patch.object(runner, "RUNNER_DIRECTORY", Path(directory)), patch.dict(
                "os.environ", {"RUNNER_ID": "from-shell"}, clear=True
            ):
                runner.load_runner_environment()
                self.assertEqual(runner.os.environ["RUNNER_DOWNLOAD_DIR"], "/tmp/from-file")
                self.assertEqual(runner.os.environ["RUNNER_ID"], "from-shell")


if __name__ == "__main__":
    unittest.main()

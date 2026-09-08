from pathlib import Path
from tempfile import TemporaryDirectory
from unittest.mock import MagicMock, call, patch
import unittest

import runner
from runner import parse_args


class RunnerCliTest(unittest.TestCase):
    @patch("runner.notify_user")
    @patch("runner.captcha_visible", return_value=True)
    def test_headless_captcha_requests_visible_restart(self, _captcha_visible, notify_user) -> None:
        api = MagicMock()

        with self.assertRaises(runner.HumanVerificationRequired):
            runner.wait_for_captcha(
                MagicMock(),
                api,
                "job-1",
                deadline=100,
                allow_user_interaction=False,
            )

        api.update.assert_called_once_with(
            "job-1",
            "WAITING_FOR_USER",
            "Human verification is required. Restart the runner with --no-headless to complete it.",
        )
        notify_user.assert_called_once()

    def test_minimize_uses_chromium_window_controls(self) -> None:
        context = MagicMock()
        page = MagicMock()
        session = context.new_cdp_session.return_value
        session.send.return_value = {"windowId": 42}

        self.assertTrue(runner.minimize_chromium_window(context, page))
        self.assertEqual(
            session.send.call_args_list,
            [
                call("Browser.getWindowForTarget"),
                call("Browser.setWindowBounds", {"windowId": 42, "bounds": {"windowState": "minimized"}}),
            ],
        )
        session.detach.assert_called_once_with()

    def test_browser_starts_minimized_by_default(self) -> None:
        with patch.dict("os.environ", {}, clear=True), patch("sys.argv", ["runner.py"]):
            self.assertTrue(parse_args().start_minimized)

    def test_browser_is_headless_by_default(self) -> None:
        with patch.dict("os.environ", {}, clear=True), patch("sys.argv", ["runner.py"]):
            self.assertTrue(parse_args().headless)

    def test_visible_browser_can_be_requested_for_verification(self) -> None:
        with patch.dict("os.environ", {}, clear=True), patch("sys.argv", ["runner.py", "--no-headless"]):
            self.assertFalse(parse_args().headless)

    def test_headless_setting_uses_environment(self) -> None:
        with patch.dict("os.environ", {"RUNNER_HEADLESS": "false"}), patch("sys.argv", ["runner.py"]):
            self.assertFalse(parse_args().headless)

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
                "RUNNER_DOWNLOAD_DIR=/tmp/from-file\nRUNNER_ID=from-file\nRUNNER_HEADLESS=true\nRUNNER_START_MINIMIZED=false\n",
                encoding="utf-8",
            )
            with patch.object(runner, "RUNNER_DIRECTORY", Path(directory)), patch.dict(
                "os.environ", {"RUNNER_ID": "from-shell"}, clear=True
            ):
                runner.load_runner_environment()
                self.assertEqual(runner.os.environ["RUNNER_DOWNLOAD_DIR"], "/tmp/from-file")
                self.assertEqual(runner.os.environ["RUNNER_ID"], "from-shell")
                self.assertEqual(runner.os.environ["RUNNER_HEADLESS"], "true")
                self.assertEqual(runner.os.environ["RUNNER_START_MINIMIZED"], "false")


if __name__ == "__main__":
    unittest.main()

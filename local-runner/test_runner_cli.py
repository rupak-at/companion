from io import BytesIO
from urllib.error import HTTPError, URLError
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest.mock import MagicMock, call, patch
import unittest

import runner
from runner import parse_args


class RunnerCliTest(unittest.TestCase):
    def test_claim_recovers_with_capped_backoff(self) -> None:
        api, page = MagicMock(), MagicMock()
        job = {"jobId": "next"}
        api.claim.side_effect = [runner.RunnerApiUnavailable("database offline")] * 6 + [job]
        self.assertEqual(runner.claim_when_available(api, page), job)
        self.assertEqual(
            page.wait_for_timeout.call_args_list,
            [call(ms) for ms in (5000, 10000, 20000, 40000, 60000, 60000)],
        )

    def test_claim_does_not_retry_permanent_errors_or_once(self) -> None:
        for error, once in ((RuntimeError("unauthorized"), False),
                            (runner.RunnerApiUnavailable("offline"), True)):
            with self.subTest(error=error, once=once):
                api, page = MagicMock(), MagicMock()
                api.claim.side_effect = error
                with self.assertRaises(RuntimeError):
                    runner.claim_when_available(api, page, once=once)
                api.claim.assert_called_once()
                page.wait_for_timeout.assert_not_called()

    @patch("runner.urlopen")
    def test_api_classifies_temporary_failures(self, urlopen) -> None:
        api = runner.RunnerApi("http://localhost", "test-token", "runner")
        for error in (
            HTTPError("http://localhost", 500, "error", {}, BytesIO(b'{"code":"P1001"}')),
            URLError("offline"),
            TimeoutError("timed out"),
        ):
            with self.subTest(error=error):
                urlopen.side_effect = error
                with self.assertRaises(runner.RunnerApiUnavailable):
                    api.claim()
        urlopen.side_effect = HTTPError("http://localhost", 401, "error", {}, BytesIO(b"unauthorized"))
        with self.assertRaises(RuntimeError) as caught:
            api.claim()
        self.assertNotIsInstance(caught.exception, runner.RunnerApiUnavailable)

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

    @patch("runner.fetch_generated_download")
    @patch("runner.visible_processing_error", return_value=None)
    @patch("runner.wait_for_captcha", return_value=False)
    @patch("runner.find_submit_control")
    @patch("runner.first_visible")
    @patch("runner.find_download_control")
    def test_processed_result_clicks_before_direct_request(
        self, find_download, first_visible, find_submit, _captcha, _error, fetch
    ) -> None:
        page = MagicMock()
        page.url = runner.DEFAULT_SAVEFROM_URL
        control = MagicMock()
        find_download.side_effect = [None, control]
        callbacks = {}
        page.on.side_effect = lambda event, callback: callbacks.update({event: callback})
        download = MagicMock()
        download.suggested_filename = "video.mp4"
        # Browser events are delivered when Playwright is pumped after the click.
        page.wait_for_timeout.side_effect = lambda _ms: callbacks["download"](download)

        duplicate = MagicMock()
        api = MagicMock()

        def finish_download(_target):
            # A second event arriving while save_as waits must not save twice.
            self.assertFalse(any(
                item.args[1] == "COMPLETED" for item in api.update.call_args_list
            ))
            callbacks["download"](duplicate)
            control.click.assert_called_once()
            fetch.assert_not_called()

        download.save_as.side_effect = finish_download
        with TemporaryDirectory() as directory:
            runner.process_job(
                MagicMock(), page, api,
                {"jobId": "job-1", "sourceUrl": "https://vt.tiktok.com/example/"},
                runner.DEFAULT_SAVEFROM_URL, Path(directory),
            )
            download.save_as.assert_called_once_with(Path(directory) / "video.mp4")

        control.click.assert_called_once_with(timeout=5_000, no_wait_after=True)
        fetch.assert_not_called()
        duplicate.cancel.assert_called_once()
        duplicate.save_as.assert_not_called()
        self.assertEqual(api.update.call_args.args[1], "COMPLETED")
        page.remove_listener.assert_any_call("download", callbacks["download"])

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

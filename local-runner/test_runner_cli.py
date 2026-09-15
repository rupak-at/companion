from io import BytesIO
import json
from urllib.error import HTTPError, URLError
from pathlib import Path
from threading import Event
from tempfile import TemporaryDirectory
from unittest.mock import MagicMock, call, patch
import unittest

import runner
from runner import parse_args


class RunnerCliTest(unittest.TestCase):
    def setUp(self) -> None:
        processing_patch = patch("runner.savefrom_processing_visible", return_value=False)
        self.processing_visible = processing_patch.start()
        self.addCleanup(processing_patch.stop)

    @patch("runner.wait_for_captcha", return_value=False)
    @patch("runner.find_converter_download_control")
    def test_converter_handoff_clicks_next_download_step(self, find_control, _captcha) -> None:
        page = MagicMock()
        page.url = "https://tt.sf-converter.com/get?payload=first"
        first, second = MagicMock(), MagicMock()
        first.get_attribute.return_value = "/next"
        first.inner_text.return_value = "Download"
        second.get_attribute.return_value = "/file"
        second.inner_text.return_value = "Download video"
        started_downloads = []
        first.click.side_effect = lambda **_kwargs: setattr(page, "url", "https://tt.sf-converter.com/next")
        second.click.side_effect = lambda **_kwargs: started_downloads.append(MagicMock())
        find_control.side_effect = lambda _page: first if page.url.endswith("first") else second

        self.assertTrue(runner.follow_converter_handoff(
            page, started_downloads, runner.time.monotonic() + 60, MagicMock(), "job-1", True,
            runner.DEFAULT_SAVEFROM_URL,
        ))
        first.click.assert_called_once()
        second.click.assert_called_once()

    @patch("runner.fetch_generated_download")
    @patch("runner.follow_converter_handoff")
    @patch("runner.visible_processing_error", return_value=None)
    @patch("runner.wait_for_captcha", return_value=False)
    @patch("runner.find_submit_control")
    @patch("runner.first_visible")
    @patch("runner.find_download_control")
    def test_job_stays_on_converter_until_download_starts(
        self, find_download, first_visible, find_submit, _captcha, _error, follow_handoff, fetch
    ) -> None:
        page, api = MagicMock(), MagicMock()
        page.url = runner.DEFAULT_SAVEFROM_URL
        control = MagicMock()
        control.get_attribute.return_value = "/get?payload=test"
        control.evaluate.return_value = "https://tt.sf-converter.com/"
        control.click.side_effect = lambda **_kwargs: setattr(page, "url", "https://tt.sf-converter.com/get?payload=test")
        find_download.side_effect = [None, control]
        download = MagicMock()
        download.suggested_filename = "video.mp4"
        follow_handoff.side_effect = lambda _page, started, *_args: started.append(download)

        with TemporaryDirectory() as directory:
            runner.process_job(
                MagicMock(), page, api,
                {"jobId": "job-1", "sourceUrl": "https://vt.tiktok.com/example/"},
                runner.DEFAULT_SAVEFROM_URL, Path(directory),
            )

        page.go_back.assert_not_called()
        download.save_as.assert_called_once()
        fetch.assert_not_called()

    @patch("runner.fetch_generated_download")
    @patch("runner.visible_processing_error", return_value=None)
    @patch("runner.wait_for_captcha", return_value=False)
    @patch("runner.find_submit_control")
    @patch("runner.first_visible")
    @patch("runner.find_download_control")
    def test_waits_for_processing_banner_before_clicking_visible_result(
        self, find_download, _first_visible, _find_submit, _captcha, _error, fetch
    ) -> None:
        page, api = MagicMock(), MagicMock()
        page.url = runner.DEFAULT_SAVEFROM_URL
        control = MagicMock()
        find_download.side_effect = [None, control]
        self.processing_visible.side_effect = [True, False]
        callbacks = {}
        page.on.side_effect = lambda event, callback: callbacks.update({event: callback})
        download = MagicMock()
        download.suggested_filename = "video.mp4"

        def pump_events(_ms):
            if control.click.called:
                callbacks["download"](download)
            else:
                control.click.assert_not_called()

        page.wait_for_timeout.side_effect = pump_events
        with TemporaryDirectory() as directory:
            runner.process_job(
                MagicMock(), page, api,
                {"jobId": "job-1", "sourceUrl": "https://vt.tiktok.com/example/"},
                runner.DEFAULT_SAVEFROM_URL, Path(directory),
            )

        self.assertEqual(self.processing_visible.call_count, 2)
        control.click.assert_called_once()
        download.save_as.assert_called_once()
        fetch.assert_not_called()

    @patch("runner.fetch_generated_download")
    @patch("runner.follow_converter_handoff")
    @patch("runner.visible_processing_error", return_value=None)
    @patch("runner.wait_for_captcha", return_value=False)
    @patch("runner.find_submit_control")
    @patch("runner.first_visible")
    @patch("runner.find_download_control")
    def test_job_follows_converter_popup_instead_of_closing_it(
        self, find_download, first_visible, find_submit, _captcha, _error, follow_handoff, fetch
    ) -> None:
        page, popup, context, api = MagicMock(), MagicMock(), MagicMock(), MagicMock()
        page.url = runner.DEFAULT_SAVEFROM_URL
        popup.url = "https://tt.sf-converter.com/get?payload=test"
        popup.is_closed.return_value = False
        context.pages = [page]
        control = MagicMock()
        control.get_attribute.return_value = "/get?payload=test"
        control.evaluate.return_value = "https://tt.sf-converter.com/"
        control.click.side_effect = lambda **_kwargs: setattr(context, "pages", [page, popup])
        find_download.side_effect = [None, control]
        download = MagicMock()
        download.suggested_filename = "video.mp4"
        follow_handoff.side_effect = lambda _page, started, *_args: started.append(download)

        with TemporaryDirectory() as directory:
            runner.process_job(
                context, page, api,
                {"jobId": "job-1", "sourceUrl": "https://vt.tiktok.com/example/"},
                runner.DEFAULT_SAVEFROM_URL, Path(directory),
            )

        popup.on.assert_any_call("download", page.on.call_args_list[0].args[1])
        download.save_as.assert_called_once()
        fetch.assert_not_called()

    def test_direct_fallback_accepts_mp4_with_generic_content_type(self) -> None:
        context = MagicMock()
        response = context.request.get.return_value
        response.ok = True
        response.url = "https://media.example/video.mp4"
        response.headers = {"content-type": "application/download"}
        response.body.return_value = b"\x00\x00\x00\x18ftypisomvideo"

        with TemporaryDirectory() as directory:
            target = runner.fetch_generated_download(
                context, "https://tt.sf-converter.com/get?payload=test",
                runner.DEFAULT_SAVEFROM_URL, Path(directory), "fallback.mp4",
            )
            self.assertEqual(target.read_bytes(), response.body.return_value)

        context.request.get.assert_called_once_with(
            "https://tt.sf-converter.com/get?payload=test",
            headers={"Referer": runner.DEFAULT_SAVEFROM_URL},
            timeout=600_000,
            fail_on_status_code=False,
        )

    def test_slow_download_refreshes_lease_until_save_finishes(self) -> None:
        refreshed = Event()
        api, download = MagicMock(), MagicMock()
        api.update.side_effect = lambda *_args: refreshed.set()
        download.save_as.side_effect = lambda _target: self.assertTrue(refreshed.wait(1))

        runner.save_download_with_heartbeat(download, Path("/tmp/video.mp4"), api, "job-1", interval=0.01)

        api.update.assert_called_with("job-1", "DOWNLOADING", "Browser download is still in progress.")
        download.save_as.assert_called_once_with(Path("/tmp/video.mp4"))

    @patch("runner.urlopen")
    def test_retry_requeues_failed_jobs_once(self, urlopen) -> None:
        api = runner.RunnerApi("http://localhost", "test-token", "runner")
        urlopen.return_value.__enter__.return_value.read.return_value = b'{"requeued": 7}'
        self.assertEqual(api.requeue_failed(), 7)
        request = urlopen.call_args.args[0]
        self.assertEqual(request.full_url, "http://localhost/api/v1/runner/jobs/requeue-failed")
        self.assertEqual(
            json.loads(request.data),
            {"runnerId": "runner"},
        )

    @patch("runner.urlopen")
    def test_file_link_captcha_push_uses_separate_endpoint(self, urlopen) -> None:
        api = runner.RunnerApi("https://example.test/", "test-token", "runner")
        urlopen.return_value.__enter__.return_value.read.return_value = b'{"sent":1}'

        status = runner.LocalBatchStatus(api, "123e4567-e89b-12d3-a456-426614174000")
        status.update("batch-1", "DOWNLOADING", "Processing")
        urlopen.assert_not_called()
        status.update("batch-1", "WAITING_FOR_USER", "Solve the CAPTCHA locally")

        request = urlopen.call_args.args[0]
        self.assertEqual(request.full_url, "https://example.test/api/v1/runner/notifications/captcha")
        self.assertEqual(json.loads(request.data), {
            "runnerId": "runner",
            "userId": "123e4567-e89b-12d3-a456-426614174000",
            "message": "Solve the CAPTCHA locally",
        })
        self.assertEqual(request.get_header("Authorization"), "Bearer test-token")

    def test_file_link_captcha_push_failure_does_not_stop_local_verification(self) -> None:
        api = MagicMock()
        api.notify_captcha.side_effect = runner.RunnerApiUnavailable("network unavailable")
        status = runner.LocalBatchStatus(api, "123e4567-e89b-12d3-a456-426614174000")

        status.update("batch-1", "WAITING_FOR_USER", "Solve the CAPTCHA locally")

        api.notify_captcha.assert_called_once()

    @patch("runner.urlopen")
    def test_old_server_gives_clear_retry_error(self, urlopen) -> None:
        api = runner.RunnerApi("http://localhost", "test-token", "runner")
        urlopen.side_effect = HTTPError("http://localhost", 404, "missing", {}, BytesIO(b'{"error":"NOT_FOUND"}'))
        with self.assertRaisesRegex(RuntimeError, "Update and rebuild the server API"):
            api.requeue_failed()

    @patch("runner.urlopen")
    def test_unconfigured_runner_token_is_a_permanent_error(self, urlopen) -> None:
        api = runner.RunnerApi("http://localhost", "test-token", "runner")
        urlopen.side_effect = HTTPError(
            "http://localhost", 503, "unavailable", {}, BytesIO(b'{"error":"RUNNER_NOT_CONFIGURED"}'),
        )

        with self.assertRaisesRegex(RuntimeError, "VPS API has no LOCAL_RUNNER_TOKEN"):
            api.claim()

    def test_retry_command_and_flag(self) -> None:
        for arguments in (("retry",), ("--retry",)):
            with self.subTest(arguments=arguments), patch("runner.sys.argv", ["runner.py", *arguments]):
                args = parse_args()
                self.assertTrue(args.retry or args.command == "retry")

    @patch("runner.download_original_url", return_value=None)
    @patch("runner.time.monotonic", side_effect=range(0, 600, 3))
    @patch("runner.visible_processing_error", return_value="Link not found")
    @patch("runner.wait_for_captcha", return_value=False)
    @patch("runner.find_submit_control")
    @patch("runner.first_visible")
    @patch("runner.find_download_control", return_value=None)
    def test_link_not_found_resubmits_once_before_failing(
        self, _download, first_visible, find_submit, _captcha, _error, _clock, original_download
    ) -> None:
        page, api = MagicMock(), MagicMock()
        page.url = runner.DEFAULT_SAVEFROM_URL
        source_url = "https://vt.tiktok.com/example/"
        with TemporaryDirectory() as directory:
            with self.assertRaisesRegex(RuntimeError, "SaveFrom and original URL both failed: Link not found"):
                runner.process_job(
                    MagicMock(), page, api,
                    {"jobId": "job-1", "sourceUrl": source_url},
                    runner.DEFAULT_SAVEFROM_URL, Path(directory),
                )
        self.assertEqual(find_submit.return_value.click.call_count, 2)
        self.assertEqual(
            first_visible.return_value.fill.call_args_list,
            [call(""), call(source_url), call(""), call(source_url)],
        )
        self.assertEqual(api.update.call_args.args[1], "FAILED")
        original_download.assert_called_once()

    @patch("runner.subprocess.run")
    def test_original_url_fallback_uses_downloaded_file(self, run) -> None:
        api = MagicMock()
        with TemporaryDirectory() as directory:
            downloaded = Path(directory) / "job-1.mp4"
            downloaded.write_bytes(b"video")
            run.return_value.returncode = 0
            run.return_value.stdout = f"{downloaded}\n"
            result = runner.download_original_url(
                "https://www.tiktok.com/@example/video/123", Path(directory), "job-1", api,
            )
        self.assertEqual(result, downloaded)
        command = run.call_args.args[0]
        self.assertEqual(command[:3], [runner.sys.executable, "-m", "yt_dlp"])
        self.assertEqual(command[-1], "https://www.tiktok.com/@example/video/123")

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

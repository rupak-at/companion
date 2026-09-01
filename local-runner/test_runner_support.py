from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

from runner_support import is_savefrom_interstitial, is_savefrom_page, read_env_value, read_link_file, safe_filename


class RunnerSupportTest(unittest.TestCase):
    def test_safe_filename_removes_paths_and_unsafe_characters(self) -> None:
        self.assertEqual(safe_filename("../../A video?.mp4"), "A_video_.mp4")

    def test_savefrom_host_check_rejects_lookalikes(self) -> None:
        self.assertTrue(is_savefrom_page("https://en1.savefrom.net/16Em/download-from-tiktok"))
        self.assertFalse(is_savefrom_page("https://savefrom.net.attacker.example/"))

    def test_detects_savefrom_user_interstitial(self) -> None:
        self.assertTrue(is_savefrom_interstitial("https://en1.savefrom.net/1OD/user.php"))
        self.assertFalse(is_savefrom_interstitial("https://en1.savefrom.net/19wr/"))

    def test_reads_only_requested_env_value(self) -> None:
        with TemporaryDirectory() as directory:
            path = Path(directory) / ".env"
            path.write_text('LOCAL_RUNNER_TOKEN="secret-token"\nOTHER="ignored"\n', encoding="utf-8")
            self.assertEqual(read_env_value(path, "LOCAL_RUNNER_TOKEN"), "secret-token")

    def test_reads_unique_links_and_ignores_blank_lines_and_comments(self) -> None:
        with TemporaryDirectory() as directory:
            path = Path(directory) / "links.txt"
            path.write_text("# queued videos\n\nhttps://www.tiktok.com/@one/video/1\nhttps://www.tiktok.com/@one/video/1\nhttps://example.com/video\n", encoding="utf-8")
            self.assertEqual(
                read_link_file(path),
                ["https://www.tiktok.com/@one/video/1", "https://example.com/video"],
            )

    def test_rejects_invalid_link_file_line(self) -> None:
        with TemporaryDirectory() as directory:
            path = Path(directory) / "links.txt"
            path.write_text("not a URL\n", encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "line 1"):
                read_link_file(path)


if __name__ == "__main__":
    unittest.main()

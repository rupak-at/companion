from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

from runner_support import is_savefrom_interstitial, is_savefrom_page, read_env_value, safe_filename


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


if __name__ == "__main__":
    unittest.main()

from __future__ import annotations

import json
import logging
import os
from pathlib import Path
import re
import sqlite3
import subprocess
import sys
from tempfile import TemporaryDirectory
import unittest


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
DRIVER_ROOT = REPOSITORY_ROOT / "Driver"
PYTHON_PATH = os.pathsep.join((str(DRIVER_ROOT), str(REPOSITORY_ROOT)))


class ApplicationLoggingTest(unittest.TestCase):
    def run_python(self, source: str, *arguments: str, cwd: Path) -> subprocess.CompletedProcess:
        environment = os.environ.copy()
        environment["PYTHONPATH"] = PYTHON_PATH
        return subprocess.run(
            [sys.executable, "-c", source, *arguments],
            cwd=cwd,
            env=environment,
            capture_output=True,
            text=True,
            check=True,
        )

    def test_standalone_setup_is_repeatable_and_keeps_result_stdout(self) -> None:
        with TemporaryDirectory() as directory:
            root = Path(directory)
            child = """
import logging
from pathlib import Path
import sys
from Tools.application_logging import initialize_standalone

stdout = sys.stdout
stderr = sys.stderr
initialize_standalone(Path(sys.argv[1]))
initialize_standalone(Path(sys.argv[1]))
try:
    raise ValueError("standalone-traceback")
except ValueError:
    logging.getLogger("test.application_logging.standalone").exception("standalone-marker")
assert sys.stdout is stdout
assert sys.stderr is stderr
assert "jpy" not in sys.modules
print("result-protocol")
"""
            result = self.run_python(child, str(root), cwd=root)

            self.assertEqual("result-protocol\n", result.stdout)
            self.assertEqual(1, result.stderr.count("standalone-marker"))
            self.assertRegex(result.stderr, r"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z ERROR ")
            self.assertIn("test.application_logging.standalone - standalone-marker", result.stderr)
            self.assertIn("Traceback (most recent call last):", result.stderr)
            self.assertIn("ValueError: standalone-traceback", result.stderr)

            files = list((root / "Data" / "logs").glob("python-*.log"))
            self.assertEqual(1, len(files))
            self.assertEqual(result.stderr, files[0].read_text(encoding="utf-8"))
            self.assertEqual(1, files[0].read_text(encoding="utf-8").count("standalone-marker"))

    def test_embedded_setup_replaces_standalone_handlers_and_forwards_once(self) -> None:
        with TemporaryDirectory() as directory:
            root = Path(directory)
            child = """
import json
import logging
from pathlib import Path
import sys
from Tools.application_logging import initialize_embedded, initialize_standalone

class Sink:
    def __init__(self):
        self.records = []
    def log(self, name, level, message, traceback):
        self.records.append([name, level, message, traceback])

stdout = sys.stdout
stderr = sys.stderr
root = Path(sys.argv[1])
initialize_standalone(root)
logging.getLogger("test.application_logging.mode").info("standalone-only-marker")
sink = Sink()
initialize_embedded(sink)
initialize_embedded(sink)
try:
    raise ValueError("embedded-traceback")
except ValueError:
    logging.getLogger("test.application_logging.embedded").exception("embedded-marker")
assert sys.stdout is stdout
assert sys.stderr is stderr
assert len(logging.getLogger().handlers) == 1
assert type(logging.getLogger().handlers[0]).__name__ == "_EmbeddedHandler"
assert "jpy" not in sys.modules
print(json.dumps(sink.records))
"""
            result = self.run_python(child, str(root), cwd=root)

            records = json.loads(result.stdout)
            self.assertEqual(1, len(records))
            self.assertEqual("test.application_logging.embedded", records[0][0])
            self.assertIsInstance(records[0][1], int)
            self.assertEqual(logging.ERROR, records[0][1])
            self.assertEqual("embedded-marker", records[0][2])
            self.assertIn("Traceback (most recent call last):", records[0][3])
            self.assertIn("ValueError: embedded-traceback", records[0][3])
            self.assertEqual(1, result.stderr.count("standalone-only-marker"))
            self.assertNotIn("embedded-marker", result.stderr)

            files = list((root / "Data" / "logs").glob("python-*.log"))
            self.assertEqual(1, len(files))
            file_output = files[0].read_text(encoding="utf-8")
            self.assertEqual(1, file_output.count("standalone-only-marker"))
            self.assertNotIn("embedded-marker", file_output)

    def test_raw_save_cli_keeps_diagnostic_logging_out_of_result_stdout(self) -> None:
        with TemporaryDirectory() as directory:
            root = Path(directory)
            raw_directory = root / "raw"
            database = root / "jobs.sqlite"
            content = root / "page.html"
            content.write_text("<h2>About the job</h2><p>Fixture content</p>", encoding="utf-8")
            child = """
import sys
from pathlib import Path
from collector import save_raw_page

root = Path(sys.argv[1])
save_raw_page.ROOT = root / "Driver"
sys.argv = ["save_raw_page.py", "--source", "linkedin",
            "--url", "https://www.linkedin.com/jobs/view/9876543210/",
            "--out-dir", str(root / "raw"), "--content-file", str(root / "page.html"),
            "--db", str(root / "jobs.sqlite"), "--collection-method", "playwright"]
save_raw_page.main()
print("cli-result-protocol")
"""
            result = self.run_python(child, str(root), cwd=root)

            self.assertEqual("cli-result-protocol\n", result.stdout)
            self.assertIn("saved linkedin https://www.linkedin.com/jobs/view/9876543210/", result.stderr)
            raw_page = raw_directory / "pages" / "9876543210.html"
            self.assertTrue(raw_page.is_file())
            self.assertEqual(content.read_text(encoding="utf-8"), raw_page.read_text(encoding="utf-8"))
            with sqlite3.connect(database) as connection:
                self.assertEqual(
                    ("linkedin", "9876543210", "RAW"),
                    connection.execute(
                        "SELECT source, source_job_id, processing_status FROM source_jobs"
                    ).fetchone(),
                )

            log_files = list((root / "Data" / "logs").glob("python-*.log"))
            self.assertEqual(1, len(log_files))
            log_text = log_files[0].read_text(encoding="utf-8")
            self.assertEqual(result.stderr, log_text)
            self.assertRegex(log_text, re.compile(r"\d{4}-\d{2}-\d{2}T.*Z INFO "))


if __name__ == "__main__":
    unittest.main()

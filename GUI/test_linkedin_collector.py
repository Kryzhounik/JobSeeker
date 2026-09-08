from __future__ import annotations

import queue
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import Mock, patch

from GUI import jobs_viewer


class FakeProcess:
    def __init__(
        self,
        stdout: str,
        stderr: str,
        return_code: int = 0,
    ) -> None:
        self.stdout = iter(stdout.splitlines(keepends=True))
        self.stderr = iter(stderr.splitlines(keepends=True))
        self.return_code = return_code

    def wait(self) -> int:
        return self.return_code


class LinkedInCollectorTest(unittest.TestCase):
    def test_reads_and_updates_property_without_losing_other_lines(self) -> None:
        with TemporaryDirectory() as directory:
            path = Path(directory) / "linkedin.properties"
            path.write_text(
                "# Collector settings\nkeywords=Java\nlimit=100\ndelaySeconds=15\n",
                encoding="utf-8",
            )

            self.assertEqual(
                jobs_viewer.read_properties_value(path, "limit"),
                "100",
            )
            jobs_viewer.write_properties_value(path, "limit", 250)

            self.assertEqual(
                path.read_text(encoding="utf-8"),
                "# Collector settings\nkeywords=Java\nlimit=250\ndelaySeconds=15\n",
            )

    def test_parses_page_progress_and_final_result(self) -> None:
        self.assertEqual(
            jobs_viewer.parse_linkedin_collector_progress(
                "Poland general start=25 cards=20 new=7 accepted=31/100"
            ),
            (31, 100),
        )
        self.assertIsNone(
            jobs_viewer.parse_linkedin_collector_progress("LinkedIn seed: Poland")
        )
        self.assertEqual(
            jobs_viewer.parse_linkedin_collector_result(
                'noise\n{"status":"complete","accepted_count":31}\n'
            ),
            {"status": "complete", "accepted_count": 31},
        )

    def test_worker_streams_progress_and_returns_final_json(self) -> None:
        process = FakeProcess(
            stdout='{"status":"complete","accepted_count":31}\n',
            stderr="Poland general start=25 cards=20 new=7 accepted=31/100\n",
        )
        viewer = Mock(spec=jobs_viewer.JobsViewer)
        viewer.collector_queue = queue.SimpleQueue()

        with patch.object(jobs_viewer.subprocess, "Popen", return_value=process):
            jobs_viewer.JobsViewer._linkedin_collection_worker(
                viewer,
                ["java", "-jar", "collector.jar", "batch"],
            )

        self.assertEqual(viewer.collector_queue.get_nowait(), ("progress", 31, 100))
        self.assertEqual(
            viewer.collector_queue.get_nowait(),
            ("done", {"status": "complete", "accepted_count": 31}),
        )

    def test_worker_reports_nonzero_exit(self) -> None:
        process = FakeProcess(
            stdout="",
            stderr="collector exploded\n",
            return_code=1,
        )
        viewer = Mock(spec=jobs_viewer.JobsViewer)
        viewer.collector_queue = queue.SimpleQueue()

        with patch.object(jobs_viewer.subprocess, "Popen", return_value=process):
            jobs_viewer.JobsViewer._linkedin_collection_worker(
                viewer,
                ["java", "-jar", "collector.jar", "batch"],
            )

        self.assertEqual(
            viewer.collector_queue.get_nowait(),
            ("error", "collector exploded"),
        )


if __name__ == "__main__":
    unittest.main()

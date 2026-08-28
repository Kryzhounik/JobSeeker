from __future__ import annotations

import unittest
from urllib.error import HTTPError
from unittest.mock import MagicMock, Mock, call, patch

from GUI import jobs_viewer


class LinkedInAvailabilityTest(unittest.TestCase):
    def test_http_errors_distinguish_missing_job_from_stop_errors(self) -> None:
        for code, expected_state in ((404, "not_found"), (429, "error"), (403, "error")):
            with self.subTest(code=code):
                error = HTTPError("https://www.linkedin.com/", code, "error", {}, None)
                with patch.object(jobs_viewer.urllib.request, "urlopen", side_effect=error):
                    state, message = jobs_viewer.JobsViewer._fetch_linkedin_availability(
                        Mock(), "4459395081"
                    )
                self.assertEqual(state, expected_state)
                self.assertIn(str(code), message)

    def test_direct_404_response_is_not_found(self) -> None:
        response = MagicMock()
        response.__enter__.return_value = response
        response.getcode.return_value = 404
        with patch.object(jobs_viewer.urllib.request, "urlopen", return_value=response):
            state, _message = jobs_viewer.JobsViewer._fetch_linkedin_availability(
                Mock(), "4459395081"
            )
        self.assertEqual(state, "not_found")

    def make_worker(self, results: list[tuple[str, str]]) -> Mock:
        worker = Mock(spec=jobs_viewer.JobsViewer)
        worker.availability_queue = Mock()
        worker._linkedin_job_id.side_effect = lambda url: (
            jobs_viewer.JobsViewer._linkedin_job_id(worker, url)
        )
        worker._fetch_linkedin_availability.side_effect = results
        worker._mark_jobs_closed.return_value = 1
        return worker

    def test_404_is_logged_and_skipped_without_stopping_or_closing_job(self) -> None:
        candidates = [
            {"source_url": f"https://www.linkedin.com/jobs/view/{job_id}/"}
            for job_id in (101, 102, 103)
        ]
        worker = self.make_worker([
            ("not_found", "LinkedIn returned HTTP 404."),
            ("closed", ""),
            ("available", ""),
        ])
        with patch.object(jobs_viewer.time, "sleep") as sleep:
            jobs_viewer.JobsViewer._linkedin_availability_worker(worker, candidates, "Closed")

        self.assertEqual(worker._fetch_linkedin_availability.call_count, 3)
        worker._mark_jobs_closed.assert_called_once_with(
            [candidates[1]["source_url"]], "Closed"
        )
        worker._append_availability_log.assert_any_call(
            "skip",
            reason="http_404",
            processed=1,
            total=3,
            job_id="101",
            source_url=candidates[0]["source_url"],
            error="LinkedIn returned HTTP 404.",
        )
        worker.availability_queue.put.assert_any_call(("progress", 1, 3, 0, 1, ""))
        self.assertEqual(
            worker.availability_queue.put.call_args,
            call(("done", 3, 3, 1, 1, "")),
        )
        self.assertEqual(sleep.call_args_list, [call(5), call(5)])
        events = [entry.args[0] for entry in worker._append_availability_log.call_args_list]
        self.assertNotIn("stop_error", events)

    def test_429_still_stops_run_after_a_skipped_404(self) -> None:
        candidates = [
            {"source_url": f"https://www.linkedin.com/jobs/view/{job_id}/"}
            for job_id in (101, 102, 103)
        ]
        message = "LinkedIn returned 429; stopped to avoid rate limit."
        worker = self.make_worker([
            ("not_found", "LinkedIn returned HTTP 404."),
            ("error", message),
        ])
        with patch.object(jobs_viewer.time, "sleep"):
            jobs_viewer.JobsViewer._linkedin_availability_worker(worker, candidates, "Closed")

        self.assertEqual(worker._fetch_linkedin_availability.call_count, 2)
        worker._mark_jobs_closed.assert_not_called()
        self.assertEqual(
            worker.availability_queue.put.call_args,
            call(("done", 2, 3, 0, 1, f"102: {message}")),
        )


if __name__ == "__main__":
    unittest.main()

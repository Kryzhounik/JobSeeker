from __future__ import annotations

import json
from contextlib import closing
import gc
from pathlib import Path
import sqlite3
import sys
import tempfile
import unittest


DRIVER_ROOT = Path(__file__).resolve().parents[1]
if str(DRIVER_ROOT) not in sys.path:
    sys.path.insert(0, str(DRIVER_ROOT))

from collector.java_linkedin import python_bridge


class JavaLinkedInBridgeTest(unittest.TestCase):
    def test_existing_python_pipeline_is_callable_without_intermediate_files(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            database = root / "jobs.sqlite"
            raw_dir = root / "raw" / "linkedin"
            python_bridge.init_session(
                str(DRIVER_ROOT),
                str(database),
                str(raw_dir),
            )

            with closing(sqlite3.connect(database)) as connection:
                connection.execute(
                    """
                    INSERT INTO companies (name, linkedin_id, priority)
                    VALUES ('Priority Company', '12345', 1)
                    """
                )
                connection.commit()
            self.assertEqual(
                json.loads(python_bridge.priority_company_ids()),
                ["12345"],
            )

            run_id = "20260917T120000Z-batch-linkedin"
            python_bridge.start_run(
                run_id,
                json.dumps({"locations": ["Poland"], "limit": 30}),
            )
            page = {
                "sequence": 1,
                "label": "Poland",
                "search": "location",
                "start": 0,
                "requested_url": "https://www.linkedin.com/jobs/search/?location=Poland&start=0",
                "actual_url": "https://www.linkedin.com/jobs/search/?location=Poland&start=0",
                "layout": "lazy",
                "materialized_count": 7,
                "new_count": 6,
                "target_new_count": 6,
                "scroll_iterations": 3,
                "unchanged_iterations": 3,
                "card_ids_hash": "abc123",
                "terminal": True,
                "terminal_reason": "next_absent",
                "next_count": 0,
                "next_visible": False,
                "next_disabled": False,
                "next_aria_disabled": "",
                "next_label": "",
                "stop_reason": "next_absent",
            }
            python_bridge.log_page(run_id, json.dumps(page))
            python_bridge.finish_run(
                run_id,
                "complete",
                "plan_exhausted",
                6,
                "",
            )

            preview = {
                "job_id": "4444444444",
                "source_url": "https://www.linkedin.com/jobs/view/4444444444/",
                "title": "Java Developer",
                "company": "Example Company",
                "location": "Moldova",
                "workplace": "remote",
                "salary": "",
                "label": "Moldova",
                "start": 0,
                "index": 1,
            }
            first_decision = json.loads(
                python_bridge.decide_preview(json.dumps(preview))
            )
            self.assertEqual(first_decision["preview_decision"], "open")

            html = """<!doctype html>
            <html><head>
              <link rel="canonical" href="https://www.linkedin.com/jobs/view/4444444444/">
            </head><body><main>
              <h1>Java Developer</h1>
              <h2>About the job</h2>
              <p>Build backend services in Java.</p>
            </main></body></html>"""
            result = json.loads(
                python_bridge.process_html(json.dumps(preview), html)
            )

            self.assertEqual(result["status"], "raw_saved")
            self.assertTrue((raw_dir / "pages" / "4444444444.html").is_file())
            self.assertFalse((root / "_tmp_collect").exists())
            with closing(sqlite3.connect(database)) as connection:
                collected = connection.execute(
                    """
                    SELECT
                        text.readable_text,
                        text.source_url,
                        text.title,
                        company.name,
                        text.collected_location,
                        text.collected_workplace,
                        text.collected_salary
                    FROM source_job_texts text
                    JOIN source_jobs job ON job.id = text.source_job_ref
                    LEFT JOIN companies company ON company.id = text.company_id
                    WHERE job.source = 'linkedin' AND job.source_job_id = '4444444444'
                    """
                ).fetchone()
                event = connection.execute(
                    """
                    SELECT status
                    FROM linkedin_collection_events
                    WHERE job_id = '4444444444'
                    ORDER BY id DESC
                    LIMIT 1
                    """
                ).fetchone()
                logged_run = connection.execute(
                    """
                    SELECT status, stop_reason, accepted_count, finished_at IS NOT NULL
                    FROM linkedin_collection_runs
                    WHERE run_id = ?
                    """,
                    (run_id,),
                ).fetchone()
                logged_page = connection.execute(
                    """
                    SELECT
                        label,
                        materialized_count,
                        terminal_reason,
                        next_count,
                        stop_reason
                    FROM linkedin_collection_pages
                    WHERE run_id = ? AND sequence_no = 1
                    """,
                    (run_id,),
                ).fetchone()
            self.assertIsNotNone(collected)
            self.assertIn("Build backend services in Java.", collected[0])
            self.assertEqual(
                collected[1:],
                (
                    "https://www.linkedin.com/jobs/view/4444444444/",
                    "Java Developer",
                    "Example Company",
                    "Moldova",
                    "remote",
                    "",
                ),
            )
            self.assertEqual(event, ("raw_saved",))
            self.assertEqual(
                logged_run,
                ("complete", "plan_exhausted", 6, 1),
            )
            self.assertEqual(
                logged_page,
                ("Poland", 7, "next_absent", 0, "next_absent"),
            )

            duplicate = json.loads(
                python_bridge.decide_preview(json.dumps(preview))
            )
            self.assertEqual(duplicate["preview_rule"], "duplicate_source_job_id")
            python_bridge.close_session()
            gc.collect()


if __name__ == "__main__":
    unittest.main()

from __future__ import annotations

from pathlib import Path
import sys
import tempfile
from urllib.parse import parse_qs, urlparse
import unittest


ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from collector.scan_linkedin import collect_batch
from collector.scan_linkedin import parse_location
from collector.scan_linkedin import parse_search_page
from collector.scan_linkedin import search_page_url
from collector.scan_linkedin import should_stop_location
from collector.scan_linkedin import page_overlap_missing
from collector.save_raw_page import validate_content


class ScanLinkedInTest(unittest.TestCase):
    def test_search_page_url_contains_supported_filters(self) -> None:
        settings = {"keywords": "Java", "datePosted": "week"}

        url = search_page_url(
            settings,
            parse_location("Ukraine:102264497"),
            18,
        )
        query = parse_qs(urlparse(url).query)

        self.assertEqual(query["keywords"], ["Java"])
        self.assertEqual(query["location"], ["Ukraine"])
        self.assertEqual(query["geoId"], ["102264497"])
        self.assertEqual(query["f_TPR"], ["r604800"])
        self.assertEqual(query["start"], ["18"])
        self.assertNotIn("f_SAL", query)

    def test_locationless_search_omits_location_parameters(self) -> None:
        url = search_page_url(
            {"keywords": "Java"},
            parse_location("AccountRemote"),
            0,
        )
        query = parse_qs(urlparse(url).query)

        self.assertNotIn("location", query)
        self.assertNotIn("geoId", query)

    def test_parse_search_page(self) -> None:
        html = """
        <ul>
          <li>
            <div class="base-card" data-entity-urn="urn:li:jobPosting:12345">
              <img src="logo.png">
              <h3 class="base-search-card__title"> Senior <span>Java</span> Developer </h3>
              <h4 class="base-search-card__subtitle"><a> Example Corp </a></h4>
              <span class="job-search-card__location"> Kyiv, Ukraine </span>
              <span class="job-search-card__salary-info"> $100 - $200 </span>
            </div>
          </li>
        </ul>
        """

        cards = parse_search_page(html)

        self.assertEqual(len(cards), 1)
        self.assertEqual(cards[0].job_id, "12345")
        self.assertEqual(cards[0].title, "Senior Java Developer")
        self.assertEqual(cards[0].company, "Example Corp")
        self.assertEqual(cards[0].location, "Kyiv, Ukraine")
        self.assertEqual(cards[0].salary, "$100 - $200")
        self.assertEqual(
            cards[0].source_url,
            "https://www.linkedin.com/jobs/view/12345/",
        )

    def test_missing_page_overlap_is_only_a_warning_signal(self) -> None:
        self.assertFalse(page_overlap_missing({"1", "2"}, {"2", "3"}))
        self.assertTrue(page_overlap_missing({"1", "2"}, {"3", "4"}))
        self.assertFalse(page_overlap_missing(set(), {"3", "4"}))

    def test_location_requires_two_pages_without_new_ids(self) -> None:
        self.assertFalse(should_stop_location(0, 20, 0))
        self.assertFalse(should_stop_location(0, 20, 1))
        self.assertTrue(should_stop_location(0, 20, 2))
        self.assertTrue(should_stop_location(20, 20, 0))

    def test_empty_page_is_retried_at_next_start(self) -> None:
        class EmptyClient:
            def __init__(self) -> None:
                self.urls: list[str] = []

            def get(self, url: str) -> str:
                self.urls.append(url)
                return "<ul></ul>"

        client = EmptyClient()
        with tempfile.TemporaryDirectory() as directory:
            result = collect_batch(
                {"keywords": "Java", "locations": "Moldova:106178099"},
                limit=1,
                client=client,
                db_path=Path(directory) / "jobs.sqlite",
                raw_dir=Path(directory) / "raw",
            )

        self.assertEqual([page["start"] for page in result["pages"]], [0, 9])
        self.assertEqual(len(client.urls), 2)

    def test_location_override_processes_only_one_location(self) -> None:
        class EmptyClient:
            def get(self, url: str) -> str:
                return "<ul></ul>"

        with tempfile.TemporaryDirectory() as directory:
            result = collect_batch(
                {
                    "keywords": "Java",
                    "locations": "Ukraine:102264497,Moldova:106178099",
                },
                limit=1,
                client=EmptyClient(),
                db_path=Path(directory) / "jobs.sqlite",
                raw_dir=Path(directory) / "raw",
                location_value="Moldova:106178099",
            )

        self.assertEqual(
            [page["label"] for page in result["pages"]],
            ["Moldova", "Moldova"],
        )

    def test_guest_job_description_is_valid_raw(self) -> None:
        validate_content(
            "linkedin",
            '<div class="show-more-less-html__markup">Description</div>',
        )


if __name__ == "__main__":
    unittest.main()

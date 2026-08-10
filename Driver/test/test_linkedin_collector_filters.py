from __future__ import annotations

import sys
from pathlib import Path
import unittest


DRIVER_ROOT = Path(__file__).resolve().parents[1]
if str(DRIVER_ROOT) not in sys.path:
    sys.path.insert(0, str(DRIVER_ROOT))

from collector.filtering.linkedin_filter import DEFAULT_PREVIEW_CONFIG
from collector.filtering.linkedin_filter import Vacancy
from collector.filtering.linkedin_filter import blocked_terms
from collector.filtering.linkedin_filter import decide_content
from collector.filtering.linkedin_filter import filter_vacancy
from collector.filtering.linkedin_filter import load_config
from collector.filtering.linkedin_filter import matches_term


class LinkedInCollectorFiltersTest(unittest.TestCase):
    def test_moved_preview_filter_loads_its_blocklist(self) -> None:
        terms = blocked_terms(load_config(DEFAULT_PREVIEW_CONFIG))
        self.assertIn("Python", terms)
        self.assertTrue(matches_term("Senior Python Developer", "Python"))

    def test_deep_camunda_requirement_is_blocked(self) -> None:
        result = decide_content("Deep expertise in Camunda 8 (preferably 8.8 or later)")
        self.assertEqual(result["content_decision"], "skip")
        self.assertEqual(result["content_technologies"], ["Camunda"])

    def test_advanced_c_requirement_is_blocked(self) -> None:
        result = decide_content("Advanced C programming skills with industry experience")
        self.assertEqual(result["content_decision"], "skip")
        self.assertEqual(result["content_technologies"], ["C"])

    def test_c_rule_does_not_match_cpp(self) -> None:
        result = decide_content("Advanced C++ programming skills with industry experience")
        self.assertEqual(result["content_decision"], "analyze")

    def test_camunda_in_non_strict_alternative_list_is_not_blocked(self) -> None:
        result = decide_content(
            "Solutions using Power Platform, UiPath, Camunda, Flowable, Appian, n8n or similar"
        )
        self.assertEqual(result["content_decision"], "analyze")

    def test_generic_required_signal_combines_with_technology(self) -> None:
        result = decide_content("Production experience with Camunda is required")
        self.assertEqual(result["content_decision"], "skip")

    def test_optional_blocked_technology_is_not_blocked(self) -> None:
        result = decide_content("Advanced C would be a plus")
        self.assertEqual(result["content_decision"], "analyze")

    def test_available_alternative_prevents_rejection(self) -> None:
        result = decide_content("5+ years of backend development in Java and/or C")
        self.assertEqual(result["content_decision"], "analyze")

    def test_requirement_split_across_adjacent_lines_is_blocked(self) -> None:
        result = decide_content("Deep expertise in\nCamunda 8")
        self.assertEqual(result["content_decision"], "skip")

    def test_java_title_bypasses_content_filter(self) -> None:
        result = decide_content(
            "Advanced C programming skills are required",
            title="Senior Java Backend Developer",
        )
        self.assertEqual(result["content_decision"], "analyze")
        self.assertEqual(result["content_rule"], "title_pass_word")

    def test_javascript_title_does_not_match_java_pass_word(self) -> None:
        result = decide_content(
            "Advanced C programming skills are required",
            title="Senior JavaScript Developer",
        )
        self.assertEqual(result["content_decision"], "skip")

    def test_full_vacancy_runs_title_then_content_filters(self) -> None:
        result = filter_vacancy(
            Vacancy(
                title="Backend Engineer",
                text="Deep expertise in Camunda 8 is required",
            )
        )
        self.assertTrue(result.rejected)
        self.assertEqual(result.rule, "hard_blocked_technology")

    def test_title_only_vacancy_does_not_require_text(self) -> None:
        result = filter_vacancy(Vacancy(title="Senior Python Developer"))
        self.assertTrue(result.rejected)
        self.assertEqual(result.rule, "title_blocked")


if __name__ == "__main__":
    unittest.main()

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

    def test_cpp_matches_only_cpp_technology(self) -> None:
        result = decide_content("Advanced C++ programming skills with industry experience")
        self.assertEqual(result["content_decision"], "skip")
        self.assertEqual(result["content_technologies"], ["C++"])

    def test_camunda_in_non_strict_alternative_list_is_not_blocked(self) -> None:
        result = decide_content(
            "Solutions using Power Platform, UiPath, Camunda, Flowable, Appian, n8n or similar"
        )
        self.assertEqual(result["content_decision"], "analyze")

    def test_technology_explicitly_required_is_blocked(self) -> None:
        result = decide_content("Production experience with Camunda is required")
        self.assertEqual(result["content_decision"], "skip")

    def test_generic_experience_with_technology_is_not_blocked(self) -> None:
        result = decide_content("Experience with React")
        self.assertEqual(result["content_decision"], "analyze")

    def test_optional_blocked_technology_is_not_blocked(self) -> None:
        result = decide_content("Advanced C would be a plus")
        self.assertEqual(result["content_decision"], "analyze")

    def test_available_alternative_prevents_rejection(self) -> None:
        result = decide_content("5+ years of backend development in Java and/or C")
        self.assertEqual(result["content_decision"], "analyze")

    def test_java_in_same_requirement_prevents_rejection(self) -> None:
        result = decide_content("Strong proficiency in C or Java is required")
        self.assertEqual(result["content_decision"], "analyze")

    def test_java_before_punctuation_prevents_rejection(self) -> None:
        result = decide_content("Strong proficiency in C or Java.")
        self.assertEqual(result["content_decision"], "analyze")

    def test_java_in_adjacent_line_prevents_rejection(self) -> None:
        result = decide_content(
            "Advanced C programming skills are required\nJava backend experience"
        )
        self.assertEqual(result["content_decision"], "analyze")

    def test_javascript_in_adjacent_line_does_not_count_as_java(self) -> None:
        result = decide_content(
            "Advanced C programming skills are required\nJavaScript experience"
        )
        self.assertEqual(result["content_decision"], "skip")

    def test_non_java_alternative_is_still_blocked(self) -> None:
        result = decide_content("5+ years of Camunda or UiPath experience")
        self.assertEqual(result["content_decision"], "skip")

    def test_go_requirement_is_blocked(self) -> None:
        result = decide_content("Strong proficiency in Go is required")
        self.assertEqual(result["content_decision"], "skip")
        self.assertEqual(result["content_technologies"], ["Go"])

    def test_lowercase_go_as_ordinary_word_is_not_blocked(self) -> None:
        result = decide_content(
            "Advanced Python Developer: our solutions go far beyond scripting"
        )
        self.assertEqual(result["content_decision"], "analyze")

    def test_unrelated_required_word_does_not_bind_python(self) -> None:
        result = decide_content(
            "Required to be smart, but knowing Python could be not bad"
        )
        self.assertEqual(result["content_decision"], "analyze")

    def test_unrelated_advanced_word_does_not_bind_python(self) -> None:
        result = decide_content(
            "Advanced communication skills. Some familiarity with Python."
        )
        self.assertEqual(result["content_decision"], "analyze")

    def test_plain_proficiency_in_technology_is_blocked(self) -> None:
        result = decide_content("Proficiency in Python")
        self.assertEqual(result["content_decision"], "skip")
        self.assertEqual(result["content_technologies"], ["Python"])
        self.assertEqual(len(result["content_signals"]), 1)

    def test_strong_proficiency_uses_plain_proficiency_template(self) -> None:
        result = decide_content("Strong proficiency in Python")
        self.assertEqual(result["content_decision"], "skip")
        self.assertEqual(len(result["content_signals"]), 1)

    def test_strong_list_of_technology_skills_is_blocked(self) -> None:
        result = decide_content("Strong Python, PySpark, and SQL skills")
        self.assertEqual(result["content_decision"], "skip")
        self.assertEqual(result["content_technologies"], ["Python", "PySpark"])

    def test_strong_software_development_experience_is_blocked(self) -> None:
        result = decide_content("Strong software development experience with Python")
        self.assertEqual(result["content_decision"], "skip")
        self.assertEqual(result["content_technologies"], ["Python"])

    def test_proven_knowledge_of_technology_is_blocked(self) -> None:
        result = decide_content("Proven knowledge of Python")
        self.assertEqual(result["content_decision"], "skip")
        self.assertEqual(result["content_technologies"], ["Python"])

    def test_professional_proficiency_developing_with_technology_is_blocked(
        self,
    ) -> None:
        result = decide_content(
            "You have demonstrated professional proficiency in developing "
            "public-facing APIs and web applications using Python."
        )
        self.assertEqual(result["content_decision"], "skip")
        self.assertEqual(result["content_technologies"], ["Python"])

    def test_unrequired_applied_ai_languages_are_not_blocked(self) -> None:
        result = decide_content(
            "You will work mostly in TypeScript & Python. Experience isn’t "
            "strictly required, but it is a big plus. Comfort with typed "
            "languages and modern backend practices is a must."
        )
        self.assertEqual(result["content_decision"], "analyze")

    def test_one_year_alone_is_not_a_hard_requirement(self) -> None:
        result = decide_content("1+ years of work experience with JavaScript")
        self.assertEqual(result["content_decision"], "analyze")

    def test_two_years_is_a_hard_requirement(self) -> None:
        result = decide_content("2+ years of work experience with JavaScript")
        self.assertEqual(result["content_decision"], "skip")
        self.assertEqual(result["content_technologies"], ["JavaScript"])

    def test_year_range_binds_listed_technologies(self) -> None:
        result = decide_content(
            "5–7 years of experience with React and JavaScript development"
        )
        self.assertEqual(result["content_decision"], "skip")
        self.assertEqual(
            result["content_technologies"],
            ["JavaScript", "React"],
        )

    def test_kotlin_is_not_a_blocked_technology(self) -> None:
        result = decide_content(
            "Commercial experience in Kotlin SW development (3+ years)"
        )
        self.assertEqual(result["content_decision"], "analyze")

    def test_scala_is_not_a_blocked_technology(self) -> None:
        result = decide_content("5+ years of experience with Scala")
        self.assertEqual(result["content_decision"], "analyze")

    def test_not_required_technology_is_not_blocked(self) -> None:
        result = decide_content("Advanced C knowledge is not required")
        self.assertEqual(result["content_decision"], "analyze")

    def test_preferred_technology_is_not_blocked(self) -> None:
        result = decide_content("5+ years of C preferred")
        self.assertEqual(result["content_decision"], "analyze")

    def test_optional_heading_in_adjacent_line_prevents_rejection(self) -> None:
        result = decide_content("Nice to have:\nAdvanced C programming skills")
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

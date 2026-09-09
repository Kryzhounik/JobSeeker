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

    def test_fluent_technology_requirement_is_blocked(self) -> None:
        for text in (
            "Fluent contest C++ (STL, complexity)",
            "Fluent in C++",
            "Fluent C++",
        ):
            with self.subTest(text=text):
                result = decide_content(text)
                self.assertEqual(result["content_decision"], "skip")
                self.assertEqual(result["content_technologies"], ["C++"])

    def test_fluent_requirement_keeps_optional_and_java_exceptions(self) -> None:
        for text in (
            "Nice to have:\nFluent contest C++ (STL, complexity)",
            "Fluent contest C++ or Java (STL, complexity)",
        ):
            with self.subTest(text=text):
                self.assertEqual(decide_content(text)["content_decision"], "analyze")

    def test_fluent_english_does_not_bind_unrelated_technology(self) -> None:
        result = decide_content("Fluent English and some familiarity with C++")
        self.assertEqual(result["content_decision"], "analyze")

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

    def test_proficiency_in_alternative_language_list_is_blocked(self) -> None:
        result = decide_content(
            "Proficiency in programming languages such as "
            "C/C++, C#, Python, or Rust"
        )
        self.assertEqual(result["content_decision"], "skip")
        self.assertEqual(
            result["content_technologies"],
            ["C", "C++", "C#", "Python", "Rust"],
        )

    def test_java_in_proficiency_language_list_prevents_rejection(self) -> None:
        result = decide_content(
            "Proficiency in programming languages such as C++, Java, or Python"
        )
        self.assertEqual(result["content_decision"], "analyze")

    def test_unblocked_proficiency_alternative_prevents_rejection(self) -> None:
        result = decide_content(
            "Proficiency in programming languages such as C++, Scala, or Python"
        )
        self.assertEqual(result["content_decision"], "analyze")

    def test_javascript_in_proficiency_language_list_is_not_java(self) -> None:
        result = decide_content(
            "Proficiency in programming languages such as "
            "C++, JavaScript, or Python"
        )
        self.assertEqual(result["content_decision"], "skip")
        self.assertEqual(
            result["content_technologies"],
            ["C++", "JavaScript", "Python"],
        )

    def test_optional_proficiency_language_list_is_not_blocked(self) -> None:
        result = decide_content(
            "Proficiency in programming languages such as C++ or Python is a plus"
        )
        self.assertEqual(result["content_decision"], "analyze")

    def test_strong_proficiency_uses_plain_proficiency_template(self) -> None:
        result = decide_content("Strong proficiency in Python")
        self.assertEqual(result["content_decision"], "skip")
        self.assertEqual(len(result["content_signals"]), 1)

    def test_electronjs_proficiency_is_blocked(self) -> None:
        result = decide_content(
            "Proficiency in ElectronJS and responsibility for developing and "
            "maintaining ElectronJS desktop applications."
        )
        self.assertEqual(result["content_decision"], "skip")
        self.assertEqual(result["content_technologies"], ["ElectronJS"])

    def test_proven_experience_with_pulumi_is_blocked(self) -> None:
        result = decide_content("Proven experience with Pulumi (TypeScript).")
        self.assertEqual(result["content_decision"], "skip")
        self.assertEqual(result["content_technologies"], ["TypeScript", "Pulumi"])

    def test_proficiency_list_is_blocked_by_pyspark(self) -> None:
        result = decide_content(
            "Proficiency in Amazon Web Services (AWS), Apache Kafka, PySpark, "
            "Snowflake, and dbt"
        )
        self.assertEqual(result["content_decision"], "skip")
        self.assertEqual(result["content_technologies"], ["PySpark"])

    def test_example_technology_list_keeps_java_alternative(self) -> None:
        result = decide_content(
            "Proficiency in data engineering tools and languages "
            "(e.g., Python, Java, Go)."
        )
        self.assertEqual(result["content_decision"], "analyze")

    def test_example_technology_list_blocks_when_all_examples_are_blocked(
        self,
    ) -> None:
        result = decide_content(
            "Proficiency in data engineering languages (e.g., Python, Go)."
        )
        self.assertEqual(result["content_decision"], "skip")
        self.assertEqual(result["content_technologies"], ["Python", "Go"])

    def test_proficiency_with_terraform_is_blocked(self) -> None:
        result = decide_content("Proficiency with Terraform")
        self.assertEqual(result["content_decision"], "skip")
        self.assertEqual(result["content_technologies"], ["Terraform"])

    def test_deep_hands_on_expertise_is_blocked(self) -> None:
        result = decide_content("Deep hands-on expertise with Node.js — must-have.")
        self.assertEqual(result["content_decision"], "skip")
        self.assertEqual(result["content_technologies"], ["Node.js"])

    def test_versioned_cpp_is_blocked_as_cpp(self) -> None:
        result = decide_content(
            "Strong proficiency in C++17 (or later) with a deep understanding "
            "of the language specification, memory management, standard "
            "library, and multi-threading."
        )
        self.assertEqual(result["content_decision"], "skip")
        self.assertEqual(result["content_technologies"], ["C++"])

    def test_versioned_cpp_with_real_java_alternative_is_not_blocked(self) -> None:
        result = decide_content("Strong proficiency in C++17 (or later) or Java")
        self.assertEqual(result["content_decision"], "analyze")

    def test_strong_list_of_technology_skills_is_blocked(self) -> None:
        result = decide_content("Strong Python, PySpark, and SQL skills")
        self.assertEqual(result["content_decision"], "skip")
        self.assertEqual(result["content_technologies"], ["Python", "PySpark"])

    def test_short_strong_technology_list_is_blocked(self) -> None:
        result = decide_content("Strong Python, PySpark and SQL")
        self.assertEqual(result["content_decision"], "skip")
        self.assertEqual(result["content_technologies"], ["Python", "PySpark"])

    def test_strong_technology_before_semicolon_is_blocked(self) -> None:
        result = decide_content("Strong C++; Linux and embedded software.")
        self.assertEqual(result["content_decision"], "skip")
        self.assertEqual(result["content_technologies"], ["C++"])

    def test_short_strong_or_list_keeps_unblocked_option(self) -> None:
        result = decide_content("Strong Python or Java")
        self.assertEqual(result["content_decision"], "analyze")

    def test_optional_short_strong_list_is_not_blocked(self) -> None:
        result = decide_content("Strong Python, PySpark and SQL is a plus")
        self.assertEqual(result["content_decision"], "analyze")

    def test_short_strong_list_does_not_cross_into_next_requirement(self) -> None:
        result = decide_content(
            "A strong comfort level with Linux is highly desired\n"
            "Familiarity with programming/scripting "
            "(C++, Java, Python, Perl, JavaScript, shell)/ Cloud"
        )
        self.assertEqual(result["content_decision"], "analyze")

    def test_strong_software_development_experience_is_blocked(self) -> None:
        result = decide_content("Strong software development experience with Python")
        self.assertEqual(result["content_decision"], "skip")
        self.assertEqual(result["content_technologies"], ["Python"])

    def test_strong_software_engineering_experience_list_is_blocked(self) -> None:
        result = decide_content(
            "Strong software engineering experience in C++ and Python"
        )
        self.assertEqual(result["content_decision"], "skip")
        self.assertEqual(result["content_technologies"], ["C++", "Python"])

    def test_strong_modified_backend_engineering_experience_is_blocked(self) -> None:
        result = decide_content(
            "Strong extensive hands-on practical large-scale distributed "
            "backend engineering experience with Go"
        )
        self.assertEqual(result["content_decision"], "skip")
        self.assertEqual(result["content_technologies"], ["Go"])

    def test_strong_software_engineering_or_list_keeps_unblocked_option(
        self,
    ) -> None:
        result = decide_content(
            "Strong software engineering experience in C++ or Java"
        )
        self.assertEqual(result["content_decision"], "analyze")

    def test_strong_experience_and_list_blocks_each_unsupported_item(self) -> None:
        result = decide_content("Strong experience with Spark, Scala, and Python")
        self.assertEqual(result["content_decision"], "skip")
        self.assertEqual(result["content_rule"], "hard_blocked_technology")
        self.assertEqual(result["content_technologies"], ["Spark", "Python"])

    def test_strong_experience_single_technology_is_blocked(self) -> None:
        result = decide_content("Strong experience with Python")
        self.assertEqual(result["content_decision"], "skip")
        self.assertEqual(result["content_technologies"], ["Python"])

    def test_java_does_not_hide_and_list_requirement(self) -> None:
        result = decide_content("Strong experience with Java and Python")
        self.assertEqual(result["content_decision"], "skip")
        self.assertEqual(result["content_technologies"], ["Python"])

    def test_unblocked_or_list_alternative_prevents_rejection(self) -> None:
        for text in (
            "Strong experience with Java or Python",
            "Strong experience with Scala or Python",
        ):
            with self.subTest(text=text):
                self.assertEqual(decide_content(text)["content_decision"], "analyze")

    def test_all_blocked_or_list_alternatives_are_rejected(self) -> None:
        result = decide_content("Strong experience with JavaScript or Python")
        self.assertEqual(result["content_decision"], "skip")
        self.assertEqual(
            result["content_technologies"],
            ["JavaScript", "Python"],
        )

    def test_optional_strong_experience_list_is_not_blocked(self) -> None:
        result = decide_content(
            "Strong experience with Spark, Scala, and Python is a plus"
        )
        self.assertEqual(result["content_decision"], "analyze")

    def test_strong_proficiency_and_list_is_blocked(self) -> None:
        result = decide_content(
            "Strong proficiency with Python, REST APIs and Cloud (e.g. AWS, GCP)"
        )
        self.assertEqual(result["content_decision"], "skip")
        self.assertEqual(result["content_technologies"], ["Python"])

    def test_long_technology_name_hides_nested_alias(self) -> None:
        result = decide_content("Strong proficiency with Node.js and TypeScript")
        self.assertEqual(result["content_decision"], "skip")
        self.assertEqual(
            result["content_technologies"],
            ["Node.js", "TypeScript"],
        )

    def test_strong_proficiency_or_list_keeps_unblocked_alternative(self) -> None:
        result = decide_content("Strong proficiency with Python or Java")
        self.assertEqual(result["content_decision"], "analyze")

    def test_optional_strong_proficiency_list_is_not_blocked(self) -> None:
        result = decide_content(
            "Strong proficiency with Python, REST APIs and Cloud is a plus"
        )
        self.assertEqual(result["content_decision"], "analyze")

    def test_significant_experience_with_technology_is_blocked(self) -> None:
        result = decide_content("You have significant experience with Python")
        self.assertEqual(result["content_decision"], "skip")
        self.assertEqual(result["content_technologies"], ["Python"])

    def test_significant_experience_or_list_keeps_unblocked_option(self) -> None:
        result = decide_content("Significant experience with Python or Java")
        self.assertEqual(result["content_decision"], "analyze")

    def test_optional_significant_experience_is_not_blocked(self) -> None:
        result = decide_content("Significant experience with Python is a plus")
        self.assertEqual(result["content_decision"], "analyze")

    def test_strong_hands_on_technology_experience_is_blocked(self) -> None:
        for text, technology in (
            (
                "Strong hands-on experience with SAP ABAP and S/4HANA development.",
                "SAP ABAP",
            ),
            ("Strong hands-on experience with ABAP", "ABAP"),
            ("Strong hands on experience with Python", "Python"),
        ):
            with self.subTest(text=text):
                result = decide_content(text)
                self.assertEqual(result["content_decision"], "skip")
                self.assertEqual(result["content_technologies"], [technology])

    def test_optional_strong_hands_on_experience_is_not_blocked(self) -> None:
        result = decide_content(
            "Nice to have:\n"
            "Strong hands-on experience with SAP ABAP and S/4HANA development."
        )
        self.assertEqual(result["content_decision"], "analyze")

    def test_java_alternative_to_strong_hands_on_experience_is_not_blocked(
        self,
    ) -> None:
        result = decide_content("Strong hands-on experience with SAP ABAP or Java")
        self.assertEqual(result["content_decision"], "analyze")

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

    def test_minimum_years_written_in_words_is_blocked(self) -> None:
        for count in ("two", "three", "four", "five", "six", "seven", "eight", "nine", "ten"):
            with self.subTest(count=count):
                result = decide_content(
                    f"Minimum of {count} years of experience in Node.JS development."
                )
                self.assertEqual(result["content_decision"], "skip")
                self.assertEqual(result["content_technologies"], ["Node.js"])

    def test_written_year_ranges_are_blocked(self) -> None:
        for count in ("three-five", "three to five", "two+", "THREE"):
            with self.subTest(count=count):
                result = decide_content(f"{count} years of experience with Python")
                self.assertEqual(result["content_decision"], "skip")
                self.assertEqual(result["content_technologies"], ["Python"])

    def test_written_one_year_stays_optional(self) -> None:
        for text in (
            "Minimum of one year of experience in Node.JS development.",
            "One year of Node.js experience",
            "One to three years of experience with Python",
            "One-three years of experience with Python",
        ):
            with self.subTest(text=text):
                self.assertEqual(decide_content(text)["content_decision"], "analyze")

    def test_written_years_keep_optional_and_java_exceptions(self) -> None:
        for text in (
            "Three years of experience with Python is a plus",
            "Three years of experience with Python or Java",
        ):
            with self.subTest(text=text):
                self.assertEqual(decide_content(text)["content_decision"], "analyze")

    def test_ukrainian_years_and_technology_list_is_blocked(self) -> None:
        result = decide_content(
            "5+ років досвіду backend-розробки з використанням "
            "Node.js та TypeScript"
        )
        self.assertEqual(result["content_decision"], "skip")
        self.assertEqual(
            result["content_technologies"],
            ["Node.js", "TypeScript"],
        )

    def test_ukrainian_or_list_keeps_unblocked_alternative(self) -> None:
        result = decide_content(
            "5+ років досвіду backend-розробки з використанням "
            "Node.js або Java"
        )
        self.assertEqual(result["content_decision"], "analyze")

    def test_ukrainian_one_year_stays_optional(self) -> None:
        result = decide_content(
            "1+ рік досвіду backend-розробки з використанням Node.js"
        )
        self.assertEqual(result["content_decision"], "analyze")

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

    def test_following_preferred_heading_does_not_make_requirement_optional(
        self,
    ) -> None:
        result = decide_content(
            "10-14 years of experience building commercial software in C++\n"
            "Preferred Skills\n"
            "Familiar with Parallel C++ Design Patterns"
        )
        self.assertEqual(result["content_decision"], "skip")
        self.assertEqual(result["content_technologies"], ["C++"])

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

    def test_php_title_is_blocked_with_intervening_role_words(self) -> None:
        result = filter_vacancy(Vacancy(title="Senior PHP Backend Engineer"))
        self.assertTrue(result.rejected)
        self.assertEqual(result.rule, "title_blocked")
        self.assertEqual(result.terms, ("PHP",))


if __name__ == "__main__":
    unittest.main()

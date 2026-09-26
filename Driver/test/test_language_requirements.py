from __future__ import annotations

import sys
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest


DRIVER_ROOT = Path(__file__).resolve().parents[1]
if str(DRIVER_ROOT) not in sys.path:
    sys.path.insert(0, str(DRIVER_ROOT))

from collector.filtering.language_requirements import extract_language_requirements
from collector.filtering.language_requirements import (
    DEFAULT_LANGUAGE_RULES,
)
from collector.filtering.language_requirements import (
    load_language_requirement_extractor,
)
from collector.filtering.linkedin_filter import DEFAULT_LANGUAGE_CONFIG
from collector.filtering.linkedin_filter import decide_content


def write_language_policy(path: Path, *, fluent: str = "C1") -> None:
    path.write_text(
        f"""
[implied_levels]
communication_skills = B1
communicative = B1
good = B2
strong = B2
proficient = B2
professional = B2
advanced = C1
fluent = {fluent}
excellent = C1
native = C2
bilingual = C2
required = C2

[level_aliases]
Intermediate = B1
Upper-Intermediate = B2
Upper Intermediate = B2
zaawansowany = C1
zaawansowanym = C1
Native = C2
""",
        encoding="utf-8",
    )


def write_resume(path: Path) -> None:
    path.write_text(
        """
[languages]
English = B2
Russian = C2
""",
        encoding="utf-8",
    )


class LanguageRequirementsTest(unittest.TestCase):
    def test_default_config_compiles_each_template_once(self) -> None:
        extractor = load_language_requirement_extractor(DEFAULT_LANGUAGE_CONFIG)

        self.assertGreater(len(extractor.patterns), 40)
        self.assertEqual(
            len(extractor.patterns),
            len({
                (item.source, item.implied_level, item.priority)
                for item in extractor.patterns
            }),
        )
        self.assertGreater(len(extractor.list_patterns), 10)
        self.assertEqual(
            len(extractor.list_patterns),
            len({
                (item.source, item.implied_level, item.priority)
                for item in extractor.list_patterns
            }),
        )

    def test_local_language_alias_is_normalized(self) -> None:
        requirements = extract_language_requirements(
            "Українська: C1",
            DEFAULT_LANGUAGE_CONFIG,
        )

        self.assertEqual(len(requirements), 1)
        self.assertEqual(requirements[0].name, "Ukrainian")
        self.assertEqual(requirements[0].level, "C1")

    def test_localized_explicit_level_is_extracted(self) -> None:
        requirements = extract_language_requirements(
            "język angielski na poziomie C1",
            DEFAULT_LANGUAGE_CONFIG,
        )

        self.assertEqual(len(requirements), 1)
        self.assertEqual(requirements[0].name, "English")
        self.assertEqual(requirements[0].level, "C1")

    def test_required_known_language_without_level_is_c2(self) -> None:
        requirements = extract_language_requirements(
            "Polish is required",
            DEFAULT_LANGUAGE_CONFIG,
        )

        self.assertEqual(len(requirements), 1)
        self.assertEqual(requirements[0].name, "Polish")
        self.assertEqual(requirements[0].level, "C2")

    def test_minimum_level_before_language_is_extracted(self) -> None:
        requirements = extract_language_requirements(
            "Minimum C1 English level.",
            DEFAULT_LANGUAGE_CONFIG,
        )

        self.assertEqual(len(requirements), 1)
        self.assertEqual(requirements[0].name, "English")
        self.assertEqual(requirements[0].level, "C1")

    def test_bare_fluent_language_is_c1(self) -> None:
        requirements = extract_language_requirements(
            "Fluent English.",
            DEFAULT_LANGUAGE_CONFIG,
        )

        self.assertEqual(len(requirements), 1)
        self.assertEqual(requirements[0].level, "C1")

    def test_strong_language_is_authoritative_b2(self) -> None:
        requirements = extract_language_requirements(
            "Strong verbal and written English communication skills.",
            DEFAULT_LANGUAGE_CONFIG,
        )

        self.assertEqual(len(requirements), 1)
        self.assertEqual(requirements[0].level, "B2")

    def test_excellent_language_is_authoritative_c1(self) -> None:
        requirements = extract_language_requirements(
            "Excellent communication skills in English.",
            DEFAULT_LANGUAGE_CONFIG,
        )

        self.assertEqual(len(requirements), 1)
        self.assertEqual(requirements[0].level, "C1")

    def test_proficiency_applies_to_language_list(self) -> None:
        requirements = extract_language_requirements(
            "Proficiency in Polish and English is required for this role.",
            DEFAULT_LANGUAGE_CONFIG,
        )

        self.assertEqual(
            [(item.name, item.level) for item in requirements],
            [("Polish", "B2"), ("English", "B2")],
        )

    def test_language_alternatives_are_left_to_agent(self) -> None:
        requirements = extract_language_requirements(
            "Fluent in Russian and/or Kazakh.",
            DEFAULT_LANGUAGE_CONFIG,
        )

        self.assertEqual(requirements, [])

    def test_localized_explicit_level_overrides_fluent_wording(self) -> None:
        requirements = extract_language_requirements(
            "Вільне володіння українською та англійською мовами "
            "(Upper-Intermediate та вище)",
            DEFAULT_LANGUAGE_CONFIG,
        )

        self.assertEqual(
            [(item.name, item.level) for item in requirements],
            [("Ukrainian", "B2"), ("English", "B2")],
        )

    def test_unknown_language_is_left_to_agent(self) -> None:
        requirements = extract_language_requirements(
            "Klingon is required",
            DEFAULT_LANGUAGE_CONFIG,
        )

        self.assertEqual(requirements, [])

    def test_explicit_level_applies_to_language_list(self) -> None:
        requirements = extract_language_requirements(
            "C1 level in English and Polish",
            DEFAULT_LANGUAGE_CONFIG,
        )

        self.assertCountEqual(
            [(item.name, item.level) for item in requirements],
            [("English", "C1"), ("Polish", "C1")],
        )

    def test_explicit_level_overrides_bare_required_policy(self) -> None:
        requirements = extract_language_requirements(
            "English: B2. English is required.",
            DEFAULT_LANGUAGE_CONFIG,
        )

        self.assertEqual(len(requirements), 1)
        self.assertEqual(requirements[0].level, "B2")

    def test_fluent_level_overrides_bare_required_policy(self) -> None:
        requirements = extract_language_requirements(
            "Fluent English is required.",
            DEFAULT_LANGUAGE_CONFIG,
        )

        self.assertEqual(len(requirements), 1)
        self.assertEqual(requirements[0].level, "C1")

    def test_default_config_extracts_reviewed_explicit_level(self) -> None:
        requirements = extract_language_requirements(
            "English: C1 Advanced",
            DEFAULT_LANGUAGE_CONFIG,
        )

        self.assertEqual(len(requirements), 1)
        self.assertEqual(requirements[0].name, "English")
        self.assertEqual(requirements[0].level, "C1")

    def test_default_config_extracts_reviewed_implied_c1_phrase(self) -> None:
        requirements = extract_language_requirements(
            "Fluent in English",
            DEFAULT_LANGUAGE_CONFIG,
        )

        self.assertEqual(len(requirements), 1)
        self.assertEqual(requirements[0].name, "English")
        self.assertEqual(requirements[0].level, "C1")

    def test_default_config_extracts_fluent_language_skills(self) -> None:
        requirements = extract_language_requirements(
            "Fluent English skills, in both speech and writing",
            DEFAULT_LANGUAGE_CONFIG,
        )

        self.assertEqual(len(requirements), 1)
        self.assertEqual(requirements[0].name, "English")
        self.assertEqual(requirements[0].level, "C1")

    def test_named_groups_preserve_plus_and_normalize_language_name(self) -> None:
        requirements = extract_language_requirements(
            "english: B2+",
            DEFAULT_LANGUAGE_CONFIG,
        )

        self.assertEqual(len(requirements), 1)
        self.assertEqual(requirements[0].name, "English")
        self.assertEqual(requirements[0].level, "B2+")
        self.assertEqual(requirements[0].level_rank, 4)

    def test_explicit_level_range_uses_the_lowest_accepted_level(self) -> None:
        requirements = extract_language_requirements(
            "Advanced proficiency in English (B2+/C1)",
            DEFAULT_LANGUAGE_CONFIG,
        )

        self.assertEqual(len(requirements), 1)
        self.assertEqual(requirements[0].name, "English")
        self.assertEqual(requirements[0].level, "B2+")
        self.assertEqual(requirements[0].level_rank, 4)

    def test_explicit_level_range_overrides_implied_level(self) -> None:
        requirements = extract_language_requirements(
            "Advanced proficiency in English (C1/B2)",
            DEFAULT_LANGUAGE_CONFIG,
        )

        self.assertEqual(len(requirements), 1)
        self.assertEqual(requirements[0].level, "B2")

    def test_explicit_b2_range_passes_content_filter(self) -> None:
        result = decide_content("Advanced proficiency in English (B2+/C1)")

        self.assertEqual(result["content_decision"], "analyze")

    def test_explicit_skills_level_overrides_fluent_wording(self) -> None:
        text = "Fluent English skills (written and spoken) at a B2+ level or higher"

        requirements = extract_language_requirements(
            text,
            DEFAULT_LANGUAGE_CONFIG,
        )
        result = decide_content(text)

        self.assertEqual(len(requirements), 1)
        self.assertEqual(requirements[0].name, "English")
        self.assertEqual(requirements[0].level, "B2+")
        self.assertEqual(result["content_decision"], "analyze")

    def test_explicit_communication_skills_level_overrides_fluent(self) -> None:
        requirements = extract_language_requirements(
            "Fluent English communication skills at a B2+ level",
            DEFAULT_LANGUAGE_CONFIG,
        )

        self.assertEqual(len(requirements), 1)
        self.assertEqual(requirements[0].level, "B2+")

    def test_intermediate_advanced_is_left_to_agent(self) -> None:
        requirements = extract_language_requirements(
            "Intermediate-advanced English level.",
            DEFAULT_LANGUAGE_CONFIG,
        )

        self.assertEqual(requirements, [])

    def test_advanced_level_of_language_is_c1(self) -> None:
        requirements = extract_language_requirements(
            "Advanced level of English.",
            DEFAULT_LANGUAGE_CONFIG,
        )

        self.assertEqual(len(requirements), 1)
        self.assertEqual(requirements[0].level, "C1")

    def test_strong_communication_skills_in_language_are_b2(self) -> None:
        requirements = extract_language_requirements(
            "Strong communication skills in English - both spoken and written.",
            DEFAULT_LANGUAGE_CONFIG,
        )

        self.assertEqual(len(requirements), 1)
        self.assertEqual(requirements[0].level, "B2")

    def test_fluent_business_language_is_c1(self) -> None:
        requirements = extract_language_requirements(
            "Fluent business English.",
            DEFAULT_LANGUAGE_CONFIG,
        )

        self.assertEqual(len(requirements), 1)
        self.assertEqual(requirements[0].level, "C1")

    def test_localized_advanced_level_applies_to_language_list(self) -> None:
        requirements = extract_language_requirements(
            "Język angielski i polski na poziomie zaawansowanym.",
            DEFAULT_LANGUAGE_CONFIG,
        )

        self.assertCountEqual(
            [(item.name, item.level) for item in requirements],
            [("English", "C1"), ("Polish", "C1")],
        )

    def test_localized_fluent_language_list_is_c1(self) -> None:
        requirements = extract_language_requirements(
            "Biegle posługujesz się językiem polskim oraz angielskim.",
            DEFAULT_LANGUAGE_CONFIG,
        )

        self.assertEqual(
            [(item.name, item.level) for item in requirements],
            [("Polish", "C1"), ("English", "C1")],
        )

    def test_ukrainian_name_for_english_is_normalized(self) -> None:
        requirements = extract_language_requirements(
            "Сильна письмова англійська.",
            DEFAULT_LANGUAGE_CONFIG,
        )

        self.assertEqual(len(requirements), 1)
        self.assertEqual(requirements[0].name, "English")
        self.assertEqual(requirements[0].level, "B2")

    def test_polish_very_good_language_wording_is_b2(self) -> None:
        requirements = extract_language_requirements(
            "Bardzo dobra znajomość języka angielskiego w mowie i piśmie.",
            DEFAULT_LANGUAGE_CONFIG,
        )

        self.assertEqual(len(requirements), 1)
        self.assertEqual(requirements[0].name, "English")
        self.assertEqual(requirements[0].level, "B2")

    def test_explicit_c1_skills_level_remains_blocked(self) -> None:
        result = decide_content("Fluent English skills at a C1 level")

        self.assertEqual(result["content_decision"], "skip")
        self.assertEqual(result["content_languages"], ["English C1"])

    def test_programming_language_is_not_a_human_language_match(self) -> None:
        requirements = extract_language_requirements(
            "Fluent in Java. Fluent Java skills.",
            DEFAULT_LANGUAGE_CONFIG,
        )

        self.assertEqual(requirements, [])

    def test_code_owned_template_extracts_explicit_language_and_level(self) -> None:
        requirements = extract_language_requirements(
            "English: C1 Advanced",
            DEFAULT_LANGUAGE_CONFIG,
        )

        self.assertEqual(len(requirements), 1)
        self.assertEqual(requirements[0].name, "English")
        self.assertEqual(requirements[0].level, "C1")
        self.assertEqual(requirements[0].level_rank, 5)
        self.assertEqual(requirements[0].original, "English: C1 Advanced")
        self.assertIn("{language}", requirements[0].pattern)
        self.assertIn("{level}", requirements[0].pattern)

    def test_rules_file_is_reloaded_for_each_extractor(self) -> None:
        with TemporaryDirectory() as temp_directory:
            rules_path = Path(temp_directory) / "language_rules.py"
            original_rules = DEFAULT_LANGUAGE_RULES.read_text(encoding="utf-8")
            rules_path.write_text(original_rules, encoding="utf-8")

            before = extract_language_requirements(
                "Fluent in English",
                DEFAULT_LANGUAGE_CONFIG,
                rules_path,
            )
            rules_path.write_text(
                original_rules.replace(
                    r'r"\bfluent\s+in\s+{language}"',
                    r'r"\beloquent\s+in\s+{language}"',
                ),
                encoding="utf-8",
            )
            after = extract_language_requirements(
                "Fluent in English",
                DEFAULT_LANGUAGE_CONFIG,
                rules_path,
            )

        self.assertEqual(len(before), 1)
        self.assertEqual(after, [])

    def test_policy_can_change_an_implied_level_without_changing_regex(self) -> None:
        with TemporaryDirectory() as temp_directory:
            config_path = Path(temp_directory) / "languages.ini"
            write_language_policy(config_path, fluent="B2")

            requirements = extract_language_requirements(
                "Fluent in English",
                config_path,
            )

        self.assertEqual(len(requirements), 1)
        self.assertEqual(requirements[0].name, "English")
        self.assertEqual(requirements[0].level, "B2")
        self.assertEqual(requirements[0].level_rank, 4)
        self.assertEqual(requirements[0].original, "Fluent in English")
        self.assertIn("fluent", requirements[0].pattern)

    def test_optional_context_is_not_extracted(self) -> None:
        requirements = extract_language_requirements(
            "English: C1 preferred",
            DEFAULT_LANGUAGE_CONFIG,
        )

        self.assertEqual(requirements, [])

    def test_content_filter_compares_extracted_level_with_resume(self) -> None:
        with TemporaryDirectory() as temp_directory:
            root = Path(temp_directory)
            resume_path = root / "resume.ini"
            write_resume(resume_path)

            result = decide_content(
                "English: C1 Advanced",
                title="Senior Java Backend Developer",
                resume_path=resume_path,
            )

        self.assertEqual(result["content_decision"], "skip")
        self.assertEqual(result["content_rule"], "hard_language_requirement")
        self.assertEqual(result["content_languages"], ["English C1"])
        self.assertEqual(result["content_match"], "English: C1 Advanced")

    def test_sufficient_resume_level_passes(self) -> None:
        with TemporaryDirectory() as temp_directory:
            root = Path(temp_directory)
            resume_path = root / "resume.ini"
            write_resume(resume_path)

            result = decide_content(
                "Russian: C1",
                resume_path=resume_path,
            )

        self.assertEqual(result["content_decision"], "analyze")


if __name__ == "__main__":
    unittest.main()

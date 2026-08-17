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
    load_language_requirement_extractor,
)
from collector.filtering.linkedin_filter import DEFAULT_LANGUAGE_CONFIG
from collector.filtering.linkedin_filter import decide_content


LANGUAGE_TEMPLATE = r"\b{language}\s*:\s*{level}\b"
IMPLIED_C1_TEMPLATE = r"\bfluent\s+in\s+{language}\b"


def write_language_config(
    path: Path,
    templates: list[str],
    implied_c1_templates: list[str] | None = None,
) -> None:
    template_values = "\n".join(f"    {template}" for template in templates)
    implied_c1_values = "\n".join(
        f"    {template}" for template in (implied_c1_templates or [])
    )
    path.write_text(
        """
[filters]
enabled = on

[languages]
values =
    English
    Russian

[cefr_levels]
values =
    A1
    A2
    B1
    B2
    C1
    C2

[requirement_templates]
values =
"""
        + template_values
        + """

[implied_level_templates]
C1 =
"""
        + implied_c1_values
        + """

[optional_signals]
values =
    \\bpreferred\\b
    \\boptional\\b
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

        self.assertEqual(len(extractor.patterns), 8)

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

    def test_named_groups_preserve_plus_and_normalize_language_name(self) -> None:
        requirements = extract_language_requirements(
            "english: B2+",
            DEFAULT_LANGUAGE_CONFIG,
        )

        self.assertEqual(len(requirements), 1)
        self.assertEqual(requirements[0].name, "English")
        self.assertEqual(requirements[0].level, "B2+")
        self.assertEqual(requirements[0].level_rank, 4)

    def test_programming_language_is_not_a_human_language_match(self) -> None:
        requirements = extract_language_requirements(
            "Fluent in Java",
            DEFAULT_LANGUAGE_CONFIG,
        )

        self.assertEqual(requirements, [])

    def test_template_extracts_explicit_language_and_cefr_level(self) -> None:
        with TemporaryDirectory() as temp_directory:
            config_path = Path(temp_directory) / "languages.ini"
            write_language_config(config_path, [LANGUAGE_TEMPLATE])

            requirements = extract_language_requirements(
                "English: C1 Advanced",
                config_path,
            )

        self.assertEqual(len(requirements), 1)
        self.assertEqual(requirements[0].name, "English")
        self.assertEqual(requirements[0].level, "C1")
        self.assertEqual(requirements[0].level_rank, 5)
        self.assertEqual(requirements[0].original, "English: C1 Advanced")
        self.assertEqual(requirements[0].pattern, LANGUAGE_TEMPLATE)

    def test_template_can_assign_an_implied_level(self) -> None:
        with TemporaryDirectory() as temp_directory:
            config_path = Path(temp_directory) / "languages.ini"
            write_language_config(
                config_path,
                [],
                implied_c1_templates=[IMPLIED_C1_TEMPLATE],
            )

            requirements = extract_language_requirements(
                "Fluent in English",
                config_path,
            )

        self.assertEqual(len(requirements), 1)
        self.assertEqual(requirements[0].name, "English")
        self.assertEqual(requirements[0].level, "C1")
        self.assertEqual(requirements[0].level_rank, 5)
        self.assertEqual(requirements[0].original, "Fluent in English")
        self.assertEqual(requirements[0].pattern, IMPLIED_C1_TEMPLATE)

    def test_optional_context_is_not_extracted(self) -> None:
        with TemporaryDirectory() as temp_directory:
            config_path = Path(temp_directory) / "languages.ini"
            write_language_config(config_path, [LANGUAGE_TEMPLATE])

            requirements = extract_language_requirements(
                "English: C1 preferred",
                config_path,
            )

        self.assertEqual(requirements, [])

    def test_content_filter_compares_extracted_level_with_resume(self) -> None:
        with TemporaryDirectory() as temp_directory:
            root = Path(temp_directory)
            config_path = root / "languages.ini"
            resume_path = root / "resume.ini"
            write_language_config(config_path, [LANGUAGE_TEMPLATE])
            write_resume(resume_path)

            result = decide_content(
                "English: C1 Advanced",
                title="Senior Java Backend Developer",
                language_config_path=config_path,
                resume_path=resume_path,
            )

        self.assertEqual(result["content_decision"], "skip")
        self.assertEqual(result["content_rule"], "hard_language_requirement")
        self.assertEqual(result["content_languages"], ["English C1"])
        self.assertEqual(result["content_match"], "English: C1 Advanced")

    def test_sufficient_resume_level_passes(self) -> None:
        with TemporaryDirectory() as temp_directory:
            root = Path(temp_directory)
            config_path = root / "languages.ini"
            resume_path = root / "resume.ini"
            write_language_config(config_path, [LANGUAGE_TEMPLATE])
            write_resume(resume_path)

            result = decide_content(
                "Russian: C1",
                language_config_path=config_path,
                resume_path=resume_path,
            )

        self.assertEqual(result["content_decision"], "analyze")


if __name__ == "__main__":
    unittest.main()

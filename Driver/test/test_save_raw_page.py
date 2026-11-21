from pathlib import Path
import sys
import unittest


ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from collector.save_raw_page import validate_content


class RawContentValidationTest(unittest.TestCase):
    def test_guest_job_description_is_valid_raw(self) -> None:
        validate_content(
            "linkedin",
            '<div class="show-more-less-html__markup">Description</div>',
        )


if __name__ == "__main__":
    unittest.main()

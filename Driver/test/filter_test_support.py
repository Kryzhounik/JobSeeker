"""Explicit, disposable inputs for filter and collector tests."""
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

from collector.filtering import linkedin_filter as filtering
from analyzer.candidate_fit import filter as fit

LANGUAGE_POLICY = '''[ranges]
beginner=A1
elementary=A2
pre_intermediate=A2
intermediate=B1
upper_intermediate=B2
professional=B2
professional_working_proficiency=B2
full_professional_proficiency=C1
advanced=C1
native=C2
bilingual=C2
communication_skills=B1-B2
communicative=B1-B2
good=B1-B2
very_good=B2-C1
strong=B2-C1
proficient=B2-C1
excellent=B2-C1
fluent=B2-C1
near_native=C1-C2
required=B1
[level_aliases]
Intermediate=intermediate
Upper-Intermediate=upper_intermediate
Upper Intermediate=upper_intermediate
zaawansowany=advanced
zaawansowanym=advanced
Native=native
Near-Native=near_native
'''


class FilterTestCase(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        super().setUpClass()
        temporary_root = Path(__file__).resolve().parents[2] / 'tmp/tests'
        temporary_root.mkdir(parents=True, exist_ok=True)
        directory = TemporaryDirectory(dir=temporary_root)
        cls.addClassCleanup(directory.cleanup)
        cls.test_root = Path(directory.name)
        cls.test_profile = cls.test_root / 'Data/Profile'
        cls.preview_path = cls.test_profile / 'Driver/collector/linkedin_preview_filter.ini'
        cls.content_path = cls.preview_path.with_name('linkedin_content_filter.ini')
        cls.resume_path = cls.test_profile / 'resume.ini'
        cls.fit_path = cls.test_profile / 'Driver/analyzer/filter.ini'
        cls.language_path = cls.test_root / 'language_levels.ini'
        cls.write(cls.language_path, LANGUAGE_POLICY)
        cls.write(cls.preview_path, '[filters]\nenabled=on\n[title]\nblocked_terms=Python, C++, PHP, Frontend\n[decision]\nwhen_unsure=open\n[age]\nmax_days=14\n')
        cls.write(cls.content_path, '''[pass_words]
values=Java
[blocked_technologies]
values=ClickUp, SAP Signavio, Camunda, Microsoft Dynamics, Microsoft Fabric, D365, C, Python, Go, Golang, JavaScript, JS, ElectronJS, Electron.js, Vue.js, VueJS, Nuxt.js, NuxtJS, HTML, CSS, C++, React, TypeScript, Node.js, NodeJS, PHP, C#, FastAPI, .NET, Rust, Ruby, PyTorch, PySpark, Spark, Drupal, Laravel, WordPress, Linux kernel, Haskell, Unity, ABAP, SAP ABAP, Pulumi, Terraform, UiPath
''')
        cls.write(cls.resume_path, '''[languages]
English=B2
Russian=C2
Ukrainian=A2
[technology_levels]
Java=5
Kotlin=3
Scala=2
[locations]
Moldova=100
Ukraine=100
Georgia=100
Serbia=100
[relocation]
Moldova=100
Ukraine=100
Georgia=100
Serbia=100
''')
        cls.write(cls.fit_path, '[filters]\nlanguages=on\nprogramming_languages=on\nremote=location\nrelocation=location\nremote_relocation_mode=any\n')

    def setUp(self):
        super().setUp()
        directory = TemporaryDirectory(dir=self.test_root)
        self.addCleanup(directory.cleanup)
        self.database_path = Path(directory.name) / "jobs.sqlite"

    @staticmethod
    def write(path, text):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding='utf-8')

    def VacancyFilter(self, **kwargs):
        defaults = dict(preview_config_path=self.preview_path, content_config_path=self.content_path,
                        language_config_path=self.language_path,
                        resume_path=self.resume_path, db_path=self.database_path)
        return filtering.VacancyFilter(**(defaults | kwargs))

    def decide_content(self, text, config_path=None, **kwargs):
        defaults = dict(content_config_path=config_path or self.content_path)
        title = kwargs.pop('title', '')
        return filtering.decide_content(
            text, title=title, vacancy_filter=self.VacancyFilter(**(defaults | kwargs)))

    def filter_vacancy(self, vacancy):
        return filtering.filter_vacancy(vacancy, vacancy_filter=self.VacancyFilter())

    def filter_job_json(self, record, **kwargs):
        return fit.filter_job_json(record, **(dict(resume_path=self.resume_path, filter_path=self.fit_path) | kwargs))

    def decide_preview(self, preview, config_path=None, db_path=None):
        database = db_path or self.database_path
        vf = self.VacancyFilter(preview_config_path=config_path or self.preview_path, db_path=database)
        return filtering.decide_preview(preview, db_path=database, vacancy_filter=vf)

    def apply_preview_decision(self, payload, config_path=None, db_path=None):
        database = db_path or self.database_path
        vf = self.VacancyFilter(preview_config_path=config_path or self.preview_path, db_path=database)
        return filtering.apply_preview_decision(
            payload, config_path or self.preview_path, database, vacancy_filter=vf)

    def CollectorService(self, driver_root, db_path, raw_root):
        from collector.service import CollectorService
        return CollectorService(self.test_root / 'Driver', db_path, raw_root,
                                vacancy_filter=self.VacancyFilter(db_path=db_path))

    def init_session(self, driver_root, db_path, raw_root):
        from collector.java_linkedin import python_bridge
        python_bridge.init_session(driver_root, db_path, raw_root,
                                  vacancy_filter=self.VacancyFilter(db_path=Path(db_path)))

    def collect_refilter_transitions(self, db_path):
        from Tools import filter_database
        return filter_database.collect_refilter_transitions(
            db_path, vacancy_filter=self.VacancyFilter(db_path=db_path))

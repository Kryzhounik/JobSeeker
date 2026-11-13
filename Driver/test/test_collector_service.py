from contextlib import closing
from pathlib import Path
import json
import sqlite3
import sys
import tempfile
import unittest
from unittest.mock import Mock, patch

DRIVER_ROOT = Path(__file__).resolve().parents[1]
if str(DRIVER_ROOT) not in sys.path:
    sys.path.insert(0, str(DRIVER_ROOT))

from collector.service import CollectorService
from collector.linkedin_preparation import LinkedInPreparation
from collector.java_linkedin import python_bridge


class CollectorServiceTest(unittest.TestCase):
    def test_shared_processing_preserves_actual_justjoin_fields_without_linkedin_preparation(self):
        fixture = DRIVER_ROOT / 'collector/java_linkedin/src/test/resources/justjoin/offer.json'
        fields = json.loads(fixture.read_text(encoding='utf-8'))
        fields.pop('body')
        with tempfile.TemporaryDirectory() as directory:
            database = Path(directory) / 'jobs.sqlite'
            service = CollectorService(DRIVER_ROOT, database, Path(directory) / 'raw')
            preview = {'source': 'justjoin', 'job_id': fields['slug'], 'source_url': fields['url'],
                       'title': 'Java Developer', 'company': fields['companyName'],
                       'location': fields['city'], 'workplace': fields['workplaceType']}
            self.assertEqual('open', service.decide_preview(preview)['preview_decision'])
            service.save_raw(preview, json.dumps(fields), 'json', 'script')
            result = service.process_vacancy({'preview': preview, 'description': 'Build backend services in Java.',
                                              'easyApply': None, 'fields': fields})
            self.assertEqual('raw_saved', result['status'])
            self.assertEqual('justjoin', result['source'])
            self.assertEqual([{'code': 'en', 'level': 'B2'}], result['fields']['languages'])
            self.assertEqual(5, result['fields']['requiredSkills'][0]['level'])
            self.assertEqual(fields['niceToHaveSkills'], result['fields']['niceToHaveSkills'])
            self.assertEqual('duplicate_source_job_id', service.decide_preview(preview)['preview_rule'])
            with closing(sqlite3.connect(database)) as connection:
                self.assertEqual(('justjoin', fields['slug'], 'CLEANED'), connection.execute(
                    'SELECT source, source_job_id, processing_status FROM source_jobs').fetchone())
                self.assertEqual(('Build backend services in Java.',), connection.execute(
                    'SELECT readable_text FROM source_job_texts').fetchone())

    def test_registration_check_is_scoped_to_the_source(self):
        with tempfile.TemporaryDirectory() as directory:
            service = CollectorService(DRIVER_ROOT, Path(directory) / 'jobs.sqlite', Path(directory) / 'raw')
            linkedin = {'source': 'linkedin', 'job_id': '1234',
                        'source_url': 'https://www.linkedin.com/jobs/view/1234/', 'title': 'Java Engineer'}
            service.save_raw(linkedin, '<h2>About the job</h2><p>Java services</p>', 'html', 'playwright')
            service.process_vacancy({'preview': linkedin, 'description': 'Build Java services.'})
            justjoin = {**linkedin, 'source': 'justjoin', 'source_url': 'https://justjoin.it/job-offer/1234'}
            self.assertEqual('open', service.decide_preview(justjoin)['preview_decision'])
            self.assertEqual('duplicate_source_job_id', service.decide_preview(linkedin)['preview_rule'])

    def test_native_service_accepts_python_data_and_callable_without_bridge(self):
        with tempfile.TemporaryDirectory() as directory:
            database = Path(directory) / 'jobs.sqlite'
            service = CollectorService(DRIVER_ROOT, database, Path(directory) / 'raw')
            service.start_run('native-run', {})
            preview = {'job_id': '8123456789',
                       'source_url': 'https://www.linkedin.com/jobs/view/8123456789/',
                       'title': 'Java Developer', 'company': 'Example'}
            cleaner = Mock(return_value='Build backend services in Java.')
            result = LinkedInPreparation(service).process_html(preview,
                '<h2>About the job</h2><p>Build backend services in Java.</p>', True, cleaner)
            self.assertIsInstance(result, dict)
            self.assertEqual('raw_saved', result['status'])
            cleaner.assert_called_once()
            with closing(sqlite3.connect(database)) as connection:
                self.assertEqual(('Build backend services in Java.',), connection.execute(
                    'SELECT readable_text FROM source_job_texts').fetchone())
                self.assertEqual(('CLEANED', 'native-run'), connection.execute(
                    'SELECT processing_status, collection_run_id FROM source_jobs').fetchone())
                self.assertEqual((1,), connection.execute('SELECT easy_apply FROM source_job_texts').fetchone())

    def test_bridge_only_converts_data_and_adapts_java_cleaner(self):
        service = Mock()
        preparation = Mock()
        preparation.process_html.return_value = {'status': 'raw_saved', 'reason': ''}
        cleaner = Mock()
        cleaner.cleanVacancy.return_value = 'Cleaned text'
        preview = {'job_id': '123', 'title': 'Java Developer'}
        with patch.object(python_bridge, '_session', service), \
                patch.object(python_bridge, 'LinkedInPreparation', return_value=preparation):
            result = python_bridge.process_html(json.dumps(preview), 'HTML', True, cleaner)
        self.assertEqual({'status': 'raw_saved', 'reason': ''}, json.loads(result))
        args = preparation.process_html.call_args.args
        self.assertEqual((preview, 'HTML', True), args[:3])
        self.assertEqual('Cleaned text', args[3]('Original text'))
        cleaner.cleanVacancy.assert_called_once_with('Original text')

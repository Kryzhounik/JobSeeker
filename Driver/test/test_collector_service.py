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
from collector.java_linkedin import python_bridge


class CollectorServiceTest(unittest.TestCase):
    def test_native_service_accepts_python_data_and_callable_without_bridge(self):
        with tempfile.TemporaryDirectory() as directory:
            database = Path(directory) / 'jobs.sqlite'
            service = CollectorService(DRIVER_ROOT, database, Path(directory) / 'raw')
            service.start_run('native-run', {})
            preview = {'job_id': '8123456789',
                       'source_url': 'https://www.linkedin.com/jobs/view/8123456789/',
                       'title': 'Java Developer', 'company': 'Example'}
            cleaner = Mock(return_value='Build backend services in Java.')
            result = service.process_html(preview,
                '<h2>About the job</h2><p>Build backend services in Java.</p>', True, cleaner)
            self.assertIsInstance(result, dict)
            self.assertEqual('raw_saved', result['status'])
            cleaner.assert_called_once()
            self.assertEqual('Build backend services in Java.', service.semantic_snapshot(
                'linkedin', '8123456789')['text'])
            with closing(sqlite3.connect(database)) as connection:
                self.assertEqual(('CLEANED', 'native-run'), connection.execute(
                    'SELECT processing_status, collection_run_id FROM source_jobs').fetchone())
                self.assertEqual((1,), connection.execute('SELECT easy_apply FROM source_job_texts').fetchone())

    def test_bridge_only_converts_data_and_adapts_java_cleaner(self):
        service = Mock()
        service.process_html.return_value = {'status': 'raw_saved', 'reason': ''}
        cleaner = Mock()
        cleaner.cleanVacancy.return_value = 'Cleaned text'
        preview = {'job_id': '123', 'title': 'Java Developer'}
        with patch.object(python_bridge, '_session', service):
            result = python_bridge.process_html(json.dumps(preview), 'HTML', True, cleaner)
        self.assertEqual({'status': 'raw_saved', 'reason': ''}, json.loads(result))
        args = service.process_html.call_args.args
        self.assertEqual((preview, 'HTML', True), args[:3])
        self.assertEqual('Cleaned text', args[3]('Original text'))
        cleaner.cleanVacancy.assert_called_once_with('Original text')

"""Integration checks with a temporary DB and a fake metered Proxy response.
Run after packaging: python Applier/tests/test_cover_letter.py
"""
import json
import os
from pathlib import Path
import shutil
import sqlite3
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
JAVA = str(Path(os.environ['JAVA_HOME']) / 'bin/java.exe') if os.environ.get('JAVA_HOME') else shutil.which('java')
LETTER = 'Dear team,\nRelevant experience — thank you.\n'

class CoverLetterCheck(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        for directory in ('Driver/collector/java_linkedin', 'Driver/codex_proxy', 'Data/applier', 'Applier/prompts'):
            (self.root / directory).mkdir(parents=True)
        (self.root/'Driver/collector/java_linkedin/runtime.properties').write_text('python.executable='+Path(sys.executable).as_posix()+'\ndatabase.path=Data/custom jobs.sqlite\n', encoding='utf-8')
        with sqlite3.connect(self.root/'Data/custom jobs.sqlite') as db:
            db.executescript((ROOT/'Driver/db/schema.sql').read_text(encoding='utf-8'))
            db.execute("INSERT INTO companies(id,name) VALUES (5,'Fixture company')")
            db.execute("INSERT INTO source_jobs(id,source,source_job_id,processing_status) VALUES (17,'linkedin','999','RAW')")
            db.execute("INSERT INTO source_job_texts(source_job_ref,title,company_id,readable_text) VALUES (17,'Fixture title',5,?)", ('FULL DESCRIPTION '+ 'details '*500,))
            db.execute("INSERT INTO jobs(id,source_job_ref) VALUES (42,17)")
        db.close()
        (self.root/'Driver/db/migrations').mkdir(parents=True)
        shutil.copyfile(ROOT/'Driver/db/migrations/044_cover_letters.sql', self.root/'Driver/db/migrations/044_cover_letters.sql')
        shutil.copyfile(ROOT/'Applier/cover_letter_job.py', self.root/'Applier/cover_letter_job.py')
        shutil.copyfile(ROOT/'Applier/cover_letter.schema.json', self.root/'Applier/cover_letter.schema.json')
        shutil.copyfile(ROOT/'Applier/prompts/cover_letter_prompt.md', self.root/'Applier/prompts/cover_letter_prompt.md')
        self.contexts = {'resume.txt':'FULL RESUME '+ 'experience '*500, 'candidate_context.md':'EXTRA CONTEXT'}
        for name,text in self.contexts.items():
            (self.root/'Data/applier'/name).write_text(text,encoding='utf-8')
        stub = r'''import json,sys
from pathlib import Path
args=sys.argv[1:]
get=lambda key: args[args.index(key)+1]
contexts=[args[i+1] for i,x in enumerate(args) if x=='--context']
assert [Path(p).name for p in contexts]==['resume.txt','candidate_context.md']
assert '--allow-browser-tools' not in args and '--model' not in args
assert get('--reasoning-effort')=='xhigh'
assert '--json-result' in args and get('--target')=='42'
assert Path(get('--db')).is_absolute()
assert Path(get('--db')) == (Path.cwd()/'Data/custom jobs.sqlite')
assert Path(get('--instruction')).read_text(encoding='utf-8')==Path('Applier/prompts/cover_letter_prompt.md').read_text(encoding='utf-8')
data=json.loads(Path(get('--input')).read_text(encoding='utf-8'))
assert data=={'job_title':'Fixture title','company':'Fixture company','job_description':'FULL DESCRIPTION '+'details '*500}
assert Path(contexts[0]).read_text(encoding='utf-8')=='FULL RESUME '+'experience '*500
assert Path(contexts[1]).read_text(encoding='utf-8')=='EXTRA CONTEXT'
assert json.loads(Path(get('--output-schema')).read_text())['required']==['letter']
Path('proxy-called').write_text('yes')
print('proxy diagnostic',file=sys.stderr)
print(json.dumps({'result':json.dumps({'letter':LETTER})}))
'''.replace('LETTER',repr(LETTER))
        (self.root/'Driver/codex_proxy/metrics_proxy.py').write_text(stub,encoding='utf-8')

    def cli(self,*args):
        return subprocess.run([JAVA,'-jar',str(ROOT/'Applier/applier.jar'),*args],cwd=self.root,capture_output=True,encoding='utf-8')

    def test_text_and_file_cli(self):
        result=self.cli('cover-letter','42')
        self.assertEqual(result.returncode,0,result.stderr)
        self.assertEqual(result.stdout,LETTER+'\n')
        self.assertNotIn('proxy diagnostic',result.stdout)
        result=self.cli('cover-letter','42','letter with spaces.txt')
        self.assertEqual(result.returncode,0,result.stderr)
        output=self.root/'letter with spaces.txt'
        self.assertEqual(result.stdout.strip(),str(output))
        self.assertEqual(output.read_bytes(),LETTER.encode('utf-8'))
        self.assertEqual([(r[0],r[1]) for r in self.saved_letters()],[(42,LETTER),(42,LETTER)])

    def saved_letters(self):
        from contextlib import closing
        with closing(sqlite3.connect(self.root/'Data/custom jobs.sqlite')) as db:
            return db.execute('SELECT job_id, letter_text, created_at FROM cover_letters ORDER BY id').fetchall()

    def test_storage_migration_history_and_failed_file(self):
        from contextlib import closing
        with closing(sqlite3.connect(self.root/'Data/custom jobs.sqlite')) as db:
            db.execute('DROP TABLE cover_letters')
            db.commit()
        for _ in range(2):
            result=self.cli('cover-letter','42')
            self.assertEqual(result.returncode,0,result.stderr)
        rows=self.saved_letters()
        self.assertEqual([(r[0],r[1]) for r in rows],[(42,LETTER),(42,LETTER)])
        self.assertTrue(all(r[2] for r in rows))
        result=self.cli('cover-letter','42','missing-parent/letter.txt')
        self.assertNotEqual(result.returncode,0)
        self.assertEqual(result.stdout,'')
        self.assertEqual(len(self.saved_letters()),3)

    def test_job_deletion_cascades_letters(self):
        from contextlib import closing
        for migrated in (False, True):
            with self.subTest(migrated=migrated):
                with closing(sqlite3.connect(self.root/'Data/custom jobs.sqlite')) as db:
                    db.execute('PRAGMA foreign_keys = ON')
                    if migrated:
                        db.execute('DROP TABLE cover_letters')
                        db.executescript((ROOT/'Driver/db/migrations/044_cover_letters.sql').read_text(encoding='utf-8'))
                    db.execute('INSERT OR IGNORE INTO jobs(id,source_job_ref) VALUES (42,17)')
                    db.commit()
                for _ in range(2):
                    result=self.cli('cover-letter','42')
                    self.assertEqual(result.returncode,0,result.stderr)
                with closing(sqlite3.connect(self.root/'Data/custom jobs.sqlite')) as db:
                    db.execute('PRAGMA foreign_keys = ON')
                    db.execute('DELETE FROM jobs WHERE id = 42')
                    db.commit()
                    self.assertEqual(db.execute('SELECT count(*) FROM cover_letters').fetchone()[0],0)
                    self.assertEqual(db.execute('SELECT count(*) FROM jobs').fetchone()[0],0)

    def test_missing_id_and_invalid_arguments(self):
        result=self.cli('cover-letter','999')
        self.assertNotEqual(result.returncode,0)
        self.assertEqual(result.stdout,'')
        self.assertIn('Stored vacancy',result.stderr)
        self.assertFalse((self.root/'proxy-called').exists())
        self.assertEqual(self.saved_letters(),[])
        for args in [('cover-letter',),('cover-letter','0'),('cover-letter','x'),('cover-letter','42','a','b')]:
            result=self.cli(*args)
            self.assertEqual(result.returncode,2,result.stderr)
            self.assertEqual(result.stdout,'')

    def test_java_api_and_handler_return_without_printing(self):
        source=self.root/'ApiCheck.java'
        source.write_text('''import com.seeker.applier.*;
import java.nio.file.*;
public class ApiCheck {
 public static void main(String[] args) throws Exception {
  var api=new ApplierApi(Path.of(""));
  String text=api.coverLetter(42);
  Path output=api.coverLetter(42,Path.of("api.txt"));
  if(!text.equals(Files.readString(output))) throw new AssertionError();
  if(!new ApplierCli(api).handle(new String[]{"cover-letter","42"}).equals(text)) throw new AssertionError();
  System.err.println("API check passed");
 }
}
''',encoding='utf-8')
        cp=str(ROOT/'Applier/applier.jar')+os.pathsep+str(ROOT/'libs/*')
        result=subprocess.run([JAVA,'--class-path',cp,str(source)],cwd=self.root,capture_output=True,encoding='utf-8')
        self.assertEqual(result.returncode,0,result.stderr)
        self.assertEqual(result.stdout,'')
        self.assertIn('API check passed',result.stderr)

if __name__=='__main__':
    unittest.main()

"""Read a saved letter or vacancy, or save a generated letter by internal jobs.id."""
from contextlib import closing
import json
import sqlite3
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'Driver'))
from db.migrate import migrate_database

database = Path(sys.argv[1]).resolve()
if not database.is_file():
    sys.exit('Database not found')
migrate_database(database)

if len(sys.argv) == 4 and sys.argv[3] == '--save':
    sys.stdin.reconfigure(encoding='utf-8')
    letter = sys.stdin.read()
    with closing(sqlite3.connect(database.as_uri() + '?mode=rw', uri=True)) as connection:
        connection.execute('PRAGMA foreign_keys = ON')
        connection.execute('INSERT INTO cover_letters (job_id, letter_text) VALUES (?, ?)',
                           (int(sys.argv[2]), letter))
        connection.commit()
    sys.exit(0)

with closing(sqlite3.connect(database.as_uri() + '?mode=ro', uri=True)) as connection:
    saved = connection.execute('SELECT letter_text FROM cover_letters WHERE job_id = ?',
                               (int(sys.argv[2]),)).fetchone()
    if saved:
        print(json.dumps({'letter': saved[0]}, ensure_ascii=True))
        sys.exit(0)
    row = connection.execute("""SELECT t.title, c.name, t.readable_text
        FROM jobs j JOIN source_job_texts t ON t.source_job_ref = j.source_job_ref
        LEFT JOIN companies c ON c.id = t.company_id WHERE j.id = ?""", (int(sys.argv[2]),)).fetchone()
if not row or not row[2] or not row[2].strip():
    sys.exit('Stored vacancy or cleaned description is missing')
print(json.dumps(dict(zip(('job_title', 'company', 'job_description'), row)), ensure_ascii=True))

# Google Drive Database Sync

Purpose: move the current local job data between machines without explaining
paths every time.

This is not the main vacancy workflow and not a schema-migration procedure.
Git carries code and schema changes. This tool instruction only transfers the
current data file:

```text
Data/jobs.sqlite
```

## Default Meaning

When the user says any of these without extra details:

```text
dump DB
save DB
upload DB
share DB
sync DB
залей базу
слей базу
сохрани базу
дампни базу
поделись базой
```

export the default database to Google Drive.

When the user says any of these without extra details:

```text
download DB
restore DB
fetch DB
pull DB
достань базу
выкачай базу
скачай базу
забери базу
восстанови базу
```

import the default database from Google Drive.

Do not ask where the database is or how to name the transfer file unless the
standard path is missing or Google Drive access fails.

## Export

Export means:

1. Use the project root as the working directory.
2. Create a zip named exactly:

   ```text
   Seeker_DB_latest.zip
   ```

3. The zip must contain:

   ```text
   jobs.sqlite
   manifest.json
   ```

4. `jobs.sqlite` is copied from:

   ```text
   Data/jobs.sqlite
   ```

5. `manifest.json` must contain at least:

   ```json
   {
     "project": "JobSeeker",
     "kind": "sqlite-db",
     "database": "jobs.sqlite"
   }
   ```

6. Upload `Seeker_DB_latest.zip` to Google Drive. If a Drive file with this
   exact name already exists and the connector can update it, update that file.
   Otherwise upload a new file with the same name and report the link.

## Import

Import means:

1. Find `Seeker_DB_latest.zip` in Google Drive.
2. Download it.
3. Extract `jobs.sqlite`.
4. Overwrite:

   ```text
   Data/jobs.sqlite
   ```

5. Report that the local DB was replaced.

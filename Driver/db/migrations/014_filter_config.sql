-- Runtime switches for the pre-agent vacancy filters.

CREATE TABLE IF NOT EXISTS config (
    "key" INTEGER PRIMARY KEY AUTOINCREMENT,
    config_name TEXT NOT NULL COLLATE NOCASE UNIQUE CHECK (
        length(trim(config_name)) > 0
    ),
    value TEXT NOT NULL DEFAULT '1'
);

INSERT OR IGNORE INTO config (config_name, value) VALUES
    ('company_filter', '1'),
    ('title_filter', '1'),
    ('language_filter', '1'),
    ('technology_filter', '1');

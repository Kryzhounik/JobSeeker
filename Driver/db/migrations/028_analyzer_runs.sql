CREATE TABLE IF NOT EXISTS analyzer_runs (
    run_id TEXT PRIMARY KEY,
    started_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    parallel_agents INTEGER NOT NULL CHECK (parallel_agents > 0),
    vacancies_per_agent INTEGER NOT NULL CHECK (vacancies_per_agent > 0)
);

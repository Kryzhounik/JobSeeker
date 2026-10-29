package com.seeker.orchestrator;

import com.seeker.collector.linkedin.python.PythonRuntime;
import com.seeker.codex.CodexClient;
import com.seeker.codex.UsageLimitExceededException;
import com.seeker.python.JpyRuntime;
import org.jpy.PyModule;
import org.jpy.PyObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "seeker.jpy.integration", matches = "true")
class JpyOrchestratorBridgeIntegrationTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void importsOrchestratorBridgeThroughSharedRuntime() {
        startRuntime();
        Path database = temporaryDirectory.resolve("jobs.sqlite");

        try (PyModule bridge = PyModule.importModule("orchestrator.python_bridge");
             PyObject ignored = bridge.callMethod(
                     "load_statuses",
                     "linkedin",
                     "[]",
                     database.toString()
             )) {
            // Import and call through jpy are the integration boundary under test.
        }

        assertTrue(Files.isRegularFile(database));
    }

    @Test
    void sharedCodexClientTranslatesThePythonLimitThroughJpy() throws Exception {
        Files.writeString(temporaryDirectory.resolve("codex_client_fixture.py"), """
                from datetime import datetime, timezone
                from codex_proxy.errors import UsageLimitExceededError

                def echo(value):
                    return value

                def limited():
                    raise UsageLimitExceededError("Quota exhausted", datetime(2026, 10, 22, tzinfo=timezone.utc))

                def broken():
                    raise ValueError("Unrelated failure")
                """);
        startRuntime();
        try (PyModule module = PyModule.importModule("codex_client_fixture")) {
            assertEquals("thread-1", CodexClient.call(module, "echo", "thread-1"));
            UsageLimitExceededException error = assertThrows(UsageLimitExceededException.class,
                    () -> CodexClient.call(module, "limited"));
            assertEquals(Instant.parse("2026-10-22T00:00:00Z"), error.getRetryAt());
            assertThrows(RuntimeException.class, () -> CodexClient.call(module, "broken"));
            assertEquals("thread-2", CodexClient.call(module, "echo", "thread-2"));
        }
    }

    @Test
    void threeJpyGroupsPersistIndependentFactsAndMetricsConcurrently() throws Exception {
        Files.writeString(temporaryDirectory.resolve("parallel_facts_fixture.py"), """
                from contextlib import closing
                import json
                from pathlib import Path
                import re
                import sqlite3
                import subprocess
                import sys
                from threading import Barrier, Lock
                from codex_proxy.backend import Result
                from codex_proxy import metrics_proxy
                from db.job_registry import insert_source_job
                from db.migrate import migrate_database
                from db.readable_text import save_collected_job
                from orchestrator import python_bridge

                barrier = Barrier(3)
                lock = Lock()
                active = maximum = 0
                original_call = metrics_proxy.codex_cli.call
                original_root = python_bridge.DATA_ROOT

                def fake_call(*, prompt, thread_id=None, **kwargs):
                    global active, maximum
                    with lock:
                        active += 1
                        maximum = max(maximum, active)
                    try:
                        if not thread_id:
                            barrier.wait(timeout=10)
                        # Real child-process waiting exercises GIL release through JPy.
                        subprocess.run([sys.executable, "-c", "import time; time.sleep(0.05)"],
                                       check=True, capture_output=True)
                        job_id = re.search(r"parallel-job-(\\d+)", prompt).group(1)
                        analysis = {
                            "added_at": "2026-10-29", "title": "parallel-job-" + job_id,
                            "company": "Example", "location": "Moldova",
                            "remote_type": "remote", "remote_scope": "Moldova",
                            "relocation": "NO", "seniority": "senior", "role": "backend",
                            "salary": "", "summary": "Backend role", "notes": "",
                            "languages": [], "technologies": [],
                        }
                        turns = 2 if thread_id else 1
                        return Result([], 0, thread_id or "facts-" + job_id,
                                      json.dumps(analysis), {
                                          "input_tokens": 100 * turns,
                                          "cached_input_tokens": 0,
                                          "output_tokens": 10 * turns,
                                          "reasoning_output_tokens": 0,
                                      }, [])
                    finally:
                        with lock:
                            active -= 1

                def prepare(root):
                    global database
                    root = Path(root)
                    database = root / "jobs.sqlite"
                    migrate_database(database)
                    with closing(sqlite3.connect(database)) as connection:
                        for job in range(1, 7):
                            job_id = str(job)
                            insert_source_job(connection, "linkedin", job_id, "RELEVANT")
                            save_collected_job(connection, "linkedin", job_id,
                                               "parallel-job-" + job_id,
                                               source_url="https://example.com/" + job_id,
                                               title="parallel-job-" + job_id, company="Example")
                        connection.commit()
                    python_bridge.start_analysis("parallel-run", 3, 2, str(database))
                    python_bridge.DATA_ROOT = root
                    metrics_proxy.codex_cli.call = fake_call
                    return "ready"

                def extract(job_id, target, thread_id):
                    return python_bridge.run_job_facts("parallel-run", "linkedin", job_id,
                                                       target, thread_id, str(database))

                def verify():
                    assert maximum == 3, maximum
                    assert active == 0
                    with closing(sqlite3.connect(database)) as connection:
                        assert connection.execute("SELECT count(*) FROM source_jobs WHERE processing_status='ANALYZED'").fetchone()[0] == 6
                        assert connection.execute("SELECT count(*),sum(status='success'),sum(input_tokens) FROM codex_invocations").fetchone() == (6, 6, 600)
                        assert connection.execute("SELECT invocation_count,success_count FROM codex_runs WHERE run_id='parallel-run'").fetchone() == (6, 6)
                        assert connection.execute("SELECT parallel_agents,vacancies_per_agent FROM analyzer_runs WHERE run_id='parallel-run'").fetchone() == (3, 2)
                    for job in range(1, 7):
                        path = python_bridge.DATA_ROOT / "analyzed/linkedin" / (str(job) + ".json")
                        assert json.loads(path.read_text(encoding="utf-8"))["title"] == "parallel-job-" + str(job)
                    return "verified"

                def restore():
                    metrics_proxy.codex_cli.call = original_call
                    python_bridge.DATA_ROOT = original_root
                """);
        startRuntime();
        try (PyModule fixture = PyModule.importModule("parallel_facts_fixture")) {
            try {
                assertEquals("ready", CodexClient.call(fixture, "prepare", temporaryDirectory.toString()));
                try (var executor = Executors.newFixedThreadPool(3)) {
                    List<Future<String>> groups = new ArrayList<>();
                    for (int start = 1; start <= 5; start += 2) {
                        int first = start;
                        groups.add(executor.submit(() -> {
                            try (PyModule worker = PyModule.importModule("parallel_facts_fixture")) {
                                String target = "facts-group-" + first;
                                String threadId = CodexClient.call(worker, "extract", Integer.toString(first), target, "");
                                return CodexClient.call(worker, "extract", Integer.toString(first + 1), target, threadId);
                            }
                        }));
                    }
                    for (int group = 0; group < groups.size(); group++) {
                        assertEquals("facts-" + (group * 2 + 1), groups.get(group).get());
                    }
                }
                assertEquals("verified", CodexClient.call(fixture, "verify"));
            } finally {
                try (PyObject ignored = fixture.callMethod("restore")) { }
            }
        }
    }

    private void startRuntime() {
        Path projectRoot = Path.of(required("seeker.project.root"));
        PythonRuntime runtime = new PythonRuntime(
                Path.of(required("seeker.python.library")),
                Path.of(required("seeker.jpy.library")),
                Path.of(required("seeker.jdl.library")),
                Path.of(required("seeker.python.home")),
                Path.of(required("seeker.python.executable"))
        );
        JpyRuntime.ensureStarted(
                runtime.pythonLibrary(),
                runtime.jpyLibrary(),
                runtime.jdlLibrary(),
                runtime.pythonHome(),
                runtime.pythonExecutable(),
                projectRoot.resolve("Driver"), temporaryDirectory
        );
    }

    private String required(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing system property: " + name);
        }
        return value;
    }
}

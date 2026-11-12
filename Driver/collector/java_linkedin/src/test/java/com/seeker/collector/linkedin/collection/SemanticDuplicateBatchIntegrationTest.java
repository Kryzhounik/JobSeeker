package com.seeker.collector.linkedin.collection;

import com.seeker.collector.linkedin.python.PythonRuntime;
import com.seeker.python.JpyRuntime;
import com.sun.net.httpserver.HttpServer;
import org.jpy.PyModule;
import org.jpy.PyObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "seeker.jpy.integration", matches = "true")
class SemanticDuplicateBatchIntegrationTest {
    @TempDir Path root;

    @Test void processesSavedBatchThroughModelAndPersistsOnlyConfirmedLinks() throws Exception {
        Path project = Path.of(System.getProperty("seeker.project.root"));
        var runtime = PythonRuntime.load(project.resolve("Driver/collector/java_linkedin/runtime.properties"));
        JpyRuntime.ensureStarted(runtime.pythonLibrary(),runtime.jpyLibrary(),runtime.jdlLibrary(),
                runtime.pythonHome(),runtime.pythonExecutable(),project.resolve("Driver"));
        Path database = root.resolve("jobs.sqlite");
        try (var pathlib = PyModule.importModule("pathlib");
             var dbPath = pathlib.callMethod("Path", database.toString());
             var migrate = PyModule.importModule("db.migrate");
             var ignored = migrate.callMethod("migrate_database", dbPath)) { }
        try (var sqlite = PyModule.importModule("sqlite3");
             var connection = sqlite.callMethod("connect",database.toString())) {
            try (var ignored = connection.callMethod("executescript", """
                    INSERT INTO linkedin_collection_runs(run_id,status,collection_status) VALUES ('batch','complete','complete');
                    INSERT INTO companies(id,name) VALUES (1,'Example');
                    INSERT INTO source_jobs(id,source,source_job_id,processing_status,collection_run_id)
                    VALUES (1,'linkedin','one','CLEANED','batch'), (2,'linkedin','two','CLEANED','batch'),
                           (3,'linkedin','three','CLEANED','batch'), (4,'linkedin','four','CLEANED','batch');
                    INSERT INTO source_job_texts(source_job_ref,title,company_id,readable_text)
                    VALUES (1,'Java Engineer',1,'Original'),(2,' Java  engineer ',1,'Duplicate'),
                           (3,'Java Engineer',1,'Invalid answer'),(4,'Java Engineer',1,'Different');
                    """)) { }
            finally { try (var ignored = connection.callMethod("close")) { } }
        }
        write("Driver/collector/config/linkedin.properties", "");
        write("Data/Profile/Driver/collector/linkedin.properties", "");
        String runtimeConfig = Files.readString(project.resolve("Driver/collector/java_linkedin/runtime.properties"));
        write("Driver/collector/java_linkedin/runtime.properties", runtimeConfig
                + "\ndatabase.path=" + database.toString().replace('\\','/')
                + "\nraw.directory=raw\nprofile.directory=browser\n");
        write("EasyReasoning/prompts/compare-vacancy-descriptions.md",
                Files.readString(project.resolve("EasyReasoning/prompts/compare-vacancy-descriptions.md")));
        List<String> requests = new ArrayList<>();
        var model = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        model.createContext("/api/version", exchange -> {
            byte[] response = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,response.length);
            try (var out = exchange.getResponseBody()) { out.write(response); }
        });
        model.createContext("/api/chat", exchange -> {
            String request = new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
            String answer;
            if (request.contains("\"keep_alive\":\"0\"")) answer = "";
            else {
                requests.add(request);
                answer = request.contains("Invalid answer") ? "Unexpected"
                        : request.contains("Duplicate") ? "NO SUBSTANTIVE DIFFERENCES" : "SUBSTANTIVE DIFFERENCES";
            }
            byte[] response = ("{\"done\":true,\"message\":{\"role\":\"assistant\",\"content\":\""
                    + answer + "\"}}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.sendResponseHeaders(200,response.length);
            try (var out = exchange.getResponseBody()) { out.write(response); }
        });
        model.start();
        write("EasyReasoning/easy-reasoning.properties", "spring.ai.ollama.base-url=http://127.0.0.1:"
                + model.getAddress().getPort() + "\n");
        try { new SemanticDuplicateBatch(root).processBatch("batch"); }
        finally { model.stop(0); }
        assertEquals(4, requests.size());
        try (var sqlite = PyModule.importModule("sqlite3");
             var connection = sqlite.callMethod("connect",database.toString())) {
            try (var cursor = connection.callMethod("execute",
                    "SELECT id,semantic_duplicate_of,processing_status FROM source_jobs ORDER BY id");
                 PyObject rows = cursor.callMethod("fetchall")) {
                assertEquals("[(1, None, 'CLEANED'), (2, 1, 'CLEANED'), (3, None, 'CLEANED'), (4, None, 'CLEANED')]", rows.repr());
            } finally { try (var ignored = connection.callMethod("close")) { } }
        }
    }

    private void write(String path, String content) throws Exception {
        Path file = root.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file,content,StandardCharsets.UTF_8);
    }
}

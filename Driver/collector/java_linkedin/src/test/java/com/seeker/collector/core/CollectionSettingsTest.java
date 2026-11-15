package com.seeker.collector.core;

import com.seeker.collector.common.*;
import com.seeker.collector.linkedin.config.CollectorConfig;
import com.seeker.collector.justjoin.JustJoinConfig;
import com.seeker.tools.api.ProgressSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;

class CollectionSettingsTest {
    @TempDir Path root;
    Path linkedinFile;
    Path justjoinFile;
    Path selection;

    @BeforeEach void setup() throws Exception {
        Path collector = root.resolve("Driver/collector");
        Files.createDirectories(collector.resolve("config"));
        Files.createDirectories(collector.resolve("java_linkedin"));
        Files.createDirectories(root.resolve("Tools"));
        Path profile = Files.createDirectories(root.resolve("Data/Profile/Driver/collector"));
        linkedinFile = profile.resolve("linkedin.properties");
        justjoinFile = profile.resolve("justjoin.properties");
        selection = collector.resolve("config/collector.properties");
        Files.writeString(selection, "sources=linkedin,justjoin\nraw.directory=raw\n");
        Files.writeString(collector.resolve("config/linkedin.properties"), "delaySeconds=7\npageTimeoutSeconds=40\n");
        Files.writeString(collector.resolve("config/justjoin.properties"), "itemsCount=12\ndelaySeconds=3\n");
        Files.writeString(linkedinFile, "keywords=Java from file\nlocations=Poland:105072130\nlimit=4\n");
        Files.writeString(justjoinFile, "mainTech=java\nlanguages=en\nlimit=2\n");
        Files.writeString(collector.resolve("java_linkedin/runtime.properties"), "profile.directory=unused\n");
        Files.writeString(root.resolve("Tools/runtime.properties"), """
                database.path=jobs.sqlite
                python.library=unused-python.dll
                jpy.library=unused-jpy.dll
                jdl.library=unused-jdl.dll
                python.home=unused-python
                python.executable=unused-python.exe
                """);
    }

    @Test void snapshotsOwnEveryListAndRequireOnlySelectedSourceSettings() {
        var sources = new ArrayList<>(List.of("linkedin", "justjoin"));
        var locations = new ArrayList<>(List.of("Poland"));
        var experience = new ArrayList<>(List.of("senior"));
        var remoteBroad = new ArrayList<>(List.of("remote"));
        var workplace = new ArrayList<>(List.of("hybrid"));
        var jobTypes = new ArrayList<>(List.of("contract"));
        var languages = new ArrayList<>(List.of("en"));
        var levels = new ArrayList<>(List.of("mid"));
        var snapshot = new CollectionSettings(sources,
                new CollectionSettings.LinkedIn("Java", locations, experience, remoteBroad, workplace, jobTypes, "week", "", 5),
                new CollectionSettings.JustJoin("java", "all-locations", "14", languages, levels, "DESC", "published", 0));
        for (var list : List.of(sources, locations, experience, remoteBroad, workplace, jobTypes, languages, levels)) list.clear();
        assertEquals(List.of("linkedin", "justjoin"), snapshot.sources());
        assertEquals(List.of("Poland"), snapshot.linkedin().locations());
        assertEquals(List.of("senior"), snapshot.linkedin().experience());
        assertEquals(List.of("remote"), snapshot.linkedin().remoteBroadWorkplace());
        assertEquals(List.of("hybrid"), snapshot.linkedin().workplace());
        assertEquals(List.of("contract"), snapshot.linkedin().jobTypes());
        assertEquals(List.of("en"), snapshot.justjoin().languages());
        assertEquals(List.of("mid"), snapshot.justjoin().experienceLevels());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.justjoin().languages().add("uk"));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.linkedin().locations().clear());
        assertNotNull(new CollectionSettings(List.of("linkedin"), linkedin(1), null));
        assertNotNull(new CollectionSettings(List.of("justjoin"), null, justjoin(0)));
        assertThrows(NullPointerException.class, () -> new CollectionSettings(List.of("linkedin"), null, justjoin(0)));
        assertThrows(IllegalArgumentException.class, () -> new CollectionSettings(List.of("unknown"), linkedin(1), justjoin(0)));
    }

    @Test void disabledBrokenSourceDoesNotBlockEditing() throws Exception {
        Files.writeString(selection, "sources=linkedin\nraw.directory=raw\n");
        Files.writeString(justjoinFile, "mainTech=java\npublishedDays=oops\n");
        assertNull(CollectionSettingsLoader.loadForEditing(root).justjoin());
        Files.delete(justjoinFile);
        assertNotNull(CollectionSettingsLoader.loadForEditing(root).linkedin());
        Files.writeString(selection, "sources=justjoin\n");
        assertThrows(java.io.IOException.class, () -> CollectionSettingsLoader.loadForEditing(root));
    }

    @Test void publicationDaysAreValidatedInModel() {
        for (String days : List.of("oops", "-1", "2147483648"))
            assertThrows(IllegalArgumentException.class, () -> new CollectionSettings.JustJoin(
                    "java", "all-locations", days, List.of(), List.of(), "DESC", "published", 0));
        for (String days : List.of("", "0", "14"))
            assertNotNull(new CollectionSettings.JustJoin("java", "all-locations", days,
                    List.of(), List.of(), "DESC", "published", 0));
    }

    @Test void editorSavePreservesOtherEntriesAndDisabledSourcePreferences() throws Exception {
        Files.writeString(linkedinFile, "keywords=Java\nlimit=4\ncustom=Сохранить\n");
        var saved = CollectionSettingsLoader.loadForEditing(root);
        assertNotNull(saved.linkedin());
        assertNotNull(saved.justjoin());
        String disabledBefore = Files.readString(justjoinFile);
        CollectionSettingsLoader.save(root,
                new CollectionSettings(List.of("linkedin"), linkedin(19), justjoin(9)));
        assertEquals(disabledBefore, Files.readString(justjoinFile));
        assertEquals("raw", PropertySettings.load(selection).getProperty("raw.directory"));
        assertEquals("Сохранить", PropertySettings.load(linkedinFile).getProperty("custom"));
        var reopened = CollectionSettingsLoader.loadForEditing(root);
        assertEquals(List.of("linkedin"), reopened.sources());
        assertEquals(19, reopened.linkedin().limit());
        assertEquals(2, reopened.justjoin().limit());
        assertEquals(7, CollectorConfig.load(root.resolve("Driver/collector/config/linkedin.properties")).delaySeconds());
        // Editing an in-memory run snapshot does not write any files.
        assertNotEquals(saved.linkedin(), reopened.linkedin());
        assertEquals(4, saved.linkedin().limit());
    }

    @Test void editorReloadsBothSitesAndKeepsSourceOrder() throws Exception {
        CollectionSettingsLoader.save(root, new CollectionSettings(List.of("justjoin", "linkedin"), linkedin(8), justjoin(0)));
        var loaded = CollectionSettingsLoader.loadForEditing(root);
        assertEquals(List.of("justjoin", "linkedin"), loaded.sources());
        assertEquals(linkedin(8), loaded.linkedin());
        assertEquals(justjoin(0), loaded.justjoin());
        Files.writeString(justjoinFile, "mainTech=python\nlimit=6\n");
        assertEquals("python", CollectionSettingsLoader.loadForEditing(root).justjoin().mainTech());
        assertEquals(6, CollectionSettingsLoader.loadForEditing(root).justjoin().limit());
    }

    @Test void explicitRunsStayIndependentAndNextDefaultRunReadsChangedPreferences() throws Exception {
        var api = new CollectorApi(root);
        var first = new CollectionSettings(List.of("linkedin", "justjoin"), linkedin(5), justjoin(0));
        var second = new CollectionSettings(List.of("linkedin", "justjoin"), linkedin(9), justjoin(3));
        assertEquals("0 / 4", capture(api, null).get("linkedin accepted / limit"));
        Files.writeString(linkedinFile, "keywords=Changed file query\nlimit=17\n");
        Files.writeString(justjoinFile, "mainTech=python\nlimit=11\n");
        var before = configContents();
        var firstFields = capture(api, first);
        var secondFields = capture(api, second);
        assertEquals("0 / 5", firstFields.get("linkedin accepted / limit"));
        assertEquals("0 / unlimited", firstFields.get("justjoin accepted / limit"));
        assertEquals("0 / 9", secondFields.get("linkedin accepted / limit"));
        assertEquals("0 / 3", secondFields.get("justjoin accepted / limit"));
        var defaults = capture(api, null);
        assertEquals("0 / 17", defaults.get("linkedin accepted / limit"));
        assertEquals("0 / 11", defaults.get("justjoin accepted / limit"));
        var loaded = CollectionSettingsLoader.load(ProjectPaths.fromProjectRoot(root), null);
        assertEquals("Changed file query", loaded.linkedin().keywords());
        assertEquals("python", loaded.justjoin().mainTech());
        assertEquals(before, configContents());
    }

    @Test void explicitSnapshotIgnoresBrokenPersonalFilesAndKeepsTechnicalSettings() throws Exception {
        var snapshot = new CollectionSettings(List.of("linkedin", "justjoin"), linkedin(6), justjoin(0));
        Files.writeString(linkedinFile, "keywords=wrong\nlimit=invalid\ndelaySeconds=999\n");
        Files.writeString(justjoinFile, "mainTech=wrong\nlimit=invalid\nitemsCount=999\n");
        var before = configContents();
        var paths = ProjectPaths.fromProjectRoot(root);
        var linkedin = CollectorConfig.load(paths.configPath(), snapshot.linkedin());
        var justjoin = JustJoinConfig.load(root, snapshot.justjoin());
        assertSame(snapshot.linkedin(), linkedin.search());
        assertSame(snapshot.justjoin(), justjoin.search());
        assertEquals(7, linkedin.delaySeconds());
        assertEquals(40, linkedin.pageTimeoutSeconds());
        assertEquals(3, justjoin.delaySeconds());
        assertEquals(12, justjoin.itemsCount());
        assertEquals("0 / 6", capture(new CollectorApi(root), snapshot).get("linkedin accepted / limit"));
        assertThrows(NumberFormatException.class, () -> CollectionSettingsLoader.load(paths, null));
        assertEquals(before, configContents());
    }

    @Test void legacySelectionLoadsOnlyItsOwnPreferencesAndUsesSameLimitValidation() throws Exception {
        var paths = ProjectPaths.fromProjectRoot(root);
        Files.delete(linkedinFile);
        assertNull(CollectionSettingsLoader.load(paths, "justjoin").linkedin());
        capture(new CollectorApi(root), new CollectionSettings(List.of("justjoin"), null, justjoin(0)));
        for (int invalid : List.of(0, -1)) {
            assertThrows(IllegalArgumentException.class, () -> linkedin(invalid));
            Files.writeString(linkedinFile, "limit=" + invalid);
            assertThrows(IllegalArgumentException.class, () -> CollectionSettingsLoader.load(paths, "linkedin"));
        }
        assertThrows(IllegalArgumentException.class, () -> justjoin(-1));
        Files.writeString(justjoinFile, "mainTech=java\nlimit=-1\n");
        assertThrows(IllegalArgumentException.class, () -> CollectionSettingsLoader.load(paths, "justjoin"));
        assertEquals(0, justjoin(0).limit());
    }

    private Map<String, String> capture(CollectorApi api, CollectionSettings snapshot) {
        var captured = new ArrayList<ProgressSnapshot>();
        var stop = new RuntimeException("Stop before Python or browser startup");
        Consumer<ProgressSnapshot> progress = update -> { captured.add(update); throw stop; };
        assertSame(stop, assertThrows(RuntimeException.class, () -> {
            if (snapshot == null) api.collectBatch(progress); else api.collectBatch(snapshot, progress);
        }));
        return captured.getFirst().fields();
    }

    private Map<Path, String> configContents() throws Exception {
        var contents = new LinkedHashMap<Path, String>();
        for (Path file : List.of(selection, linkedinFile, justjoinFile,
                root.resolve("Driver/collector/config/linkedin.properties"), root.resolve("Driver/collector/config/justjoin.properties")))
            contents.put(file, Files.readString(file));
        return contents;
    }

    private CollectionSettings.LinkedIn linkedin(int limit) {
        return new CollectionSettings.LinkedIn("Java override", List.of("Ukraine:102264497"), List.of("senior"),
                List.of("remote"), List.of("remote"), List.of("contract"), "week", "", limit);
    }

    private CollectionSettings.JustJoin justjoin(int limit) {
        return new CollectionSettings.JustJoin("kotlin", "all-locations", "7", List.of("en"), List.of("senior"), "DESC", "published", limit);
    }
}

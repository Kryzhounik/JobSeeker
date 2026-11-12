package com.seeker.python;

import org.jpy.PyLib;
import org.jpy.PyLibInitializer;
import org.jpy.PyModule;
import org.jpy.PyObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/** Process-wide access to the CPython runtime embedded in the JVM by jpy. */
public final class JpyRuntime {
    private static final Set<Path> MODULE_PATHS = new LinkedHashSet<>();

    private JpyRuntime() {
    }

    public static synchronized void ensureStarted(
            Path pythonLibrary,
            Path jpyLibrary,
            Path jdlLibrary,
            Path pythonHome,
            Path pythonExecutable,
            Path... modulePaths
    ) {
        Path resolvedPythonLibrary = requiredFile(pythonLibrary, "CPython library");
        Path resolvedJpyLibrary = requiredFile(jpyLibrary, "jpy library");
        Path resolvedJdlLibrary = requiredFile(jdlLibrary, "jdl library");

        if (!PyLibInitializer.isPyLibInitialized()) {
            PyLibInitializer.initPyLib(
                    resolvedPythonLibrary.toString(),
                    resolvedJpyLibrary.toString(),
                    resolvedJdlLibrary.toString()
            );
        }

        Path[] resolvedModulePaths = new Path[modulePaths.length];
        for (int index = 0; index < modulePaths.length; index++) {
            resolvedModulePaths[index] = absolute(modulePaths[index]);
        }

        if (!PyLib.isPythonRunning()) {
            if (pythonExecutable != null
                    && !PyLib.setProgramName(absolute(pythonExecutable).toString())) {
                throw new IllegalStateException(
                        "jpy rejected Python executable: " + pythonExecutable
                );
            }
            if (pythonHome != null
                    && !PyLib.setPythonHome(absolute(pythonHome).toString())) {
                throw new IllegalStateException(
                        "jpy rejected Python home: " + pythonHome
                );
            }
            PyLib.startPython(paths(resolvedModulePaths));
            MODULE_PATHS.addAll(Arrays.asList(resolvedModulePaths));
            return;
        }

        for (Path modulePath : resolvedModulePaths) {
            if (MODULE_PATHS.add(modulePath)) {
                try (PyObject ignored = PyModule.extendSysPath(
                        modulePath.toString(),
                        true
                )) {
                    // The interpreter is shared; each caller contributes its import roots.
                }
            }
        }
    }

    private static String[] paths(Path[] values) {
        String[] paths = new String[values.length];
        for (int index = 0; index < values.length; index++) {
            paths[index] = values[index].toString();
        }
        return paths;
    }

    private static Path requiredFile(Path value, String label) {
        if (value == null) {
            throw new IllegalArgumentException(label + " is required");
        }
        Path resolved = absolute(value);
        if (!Files.isRegularFile(resolved)) {
            throw new IllegalArgumentException(label + " not found: " + resolved);
        }
        return resolved;
    }

    private static Path absolute(Path value) {
        return value.toAbsolutePath().normalize();
    }
}

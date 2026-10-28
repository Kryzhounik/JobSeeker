package com.seeker.tools.api;

import java.util.Map;

/**
 * Complete, immutable progress state for one operation; replaces the previous snapshot.
 *
 * <p>Stage names the major phase; action names the current activity within it.
 * Progress is the display-ready summary for a compact badge (normally stage and counters).
 * All labels, field values and details are prepared by the backend for display.
 * Implementations must defensively copy fields, preserve their iteration order,
 * and expose an unmodifiable map. Published values must not change afterwards.
 * Modules may implement this interface to encapsulate their own calculations.
 */
public interface ProgressSnapshot {
    String runId();

    String stage();

    String action();

    String progress();

    Map<String, String> fields();

    String details();
}

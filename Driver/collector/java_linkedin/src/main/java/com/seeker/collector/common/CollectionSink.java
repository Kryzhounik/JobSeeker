package com.seeker.collector.common;

import java.util.Map;

/** A source reports outcomes; the core owns accepted scope and counters. */
public interface CollectionSink {
    void record(ProcessResult result);
    int acceptedCount();
    Map<String, Integer> outcomes();
}

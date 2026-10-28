package com.seeker.guifx;

record CollectionRunDto(String runId, String startedAt, String finishedAt,
                        String status, String stage, String collectionStatus,
                        String collectionFinishedAt, int acceptedCount,
                        String stopReason, String message) { }

package com.seeker.guifx;

record RefilterCandidate(int sourceJobRef, String source, String sourceJobId,
                         String previousStatus, String action, Integer id, String title,
                         Integer fit, String sourceUrl, String original, String matched,
                         String rule, String reason) { }

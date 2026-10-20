package com.seeker.guifx;

record CollectedJobDto(int sourceJobRef, String source, String sourceJobId,
                       String processingStatus, String collectionMethod, String sourceUrl,
                       String title, String company, String collectedLocation,
                       String collectedWorkplace, String collectedSalary, String readableText) { }

package com.seeker.guifx;

record JobRecord(
        int jobId,
        String score,
        String fit,
        String interest,
        String status,
        String remoteScope,
        String relocation,
        String location,
        String companyDisplay,
        String title,
        String role,
        String seniority,
        String language,
        String salary,
        String addedAt,
        String reasonCode,
        String reason,
        String companyId,
        String applicationId,
        String sourceUrl,
        boolean easyApply,
        JobRecord duplicateParent,
        Integer contextForJobId
) {
    JobRecord asParentFor(int childId) {
        return new JobRecord(jobId, score, fit, interest, status, remoteScope, relocation,
                location, companyDisplay, title, role, seniority, language, salary, addedAt,
                reasonCode, reason, companyId, applicationId, sourceUrl, easyApply, null, childId);
    }
}

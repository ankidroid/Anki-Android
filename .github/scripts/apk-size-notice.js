"use strict";

// SPDX-License-Identifier: GPL-3.0-or-later

// Preference and parsing for the play-release APK size posted as a workflow notice.
// Callers list emulator runs and check-run annotations; this module stays free of I/O.

const NOTICE_TITLE = "play-release-apk-bytes";
const PLAY_RELEASE_SIZE_JOB_NAME =
    /^Android Emulator Test \(36, x86_64, google_apis, [^,\s]+, 0\)$/;

function isCompletedSuccess(run) {
    return (
        Boolean(run) &&
        String(run.status).toLowerCase() === "completed" &&
        String(run.conclusion).toLowerCase() === "success"
    );
}

function timestamp(value) {
    const parsed = Date.parse(value || "");
    return Number.isFinite(parsed) ? parsed : Number.NEGATIVE_INFINITY;
}

function byMostRecentlyCreated(left, right) {
    const createdDelta = timestamp(right.created_at) - timestamp(left.created_at);
    if (createdDelta !== 0) return createdDelta;
    return (Number(right.id) || 0) - (Number(left.id) || 0);
}

/**
 * Successful emulator runs for headSha, merge_group first.
 * Within an event, the most recently created run comes first.
 * Cancelled, failed, and in-progress runs are omitted.
 * Callers walk this list until one run has a play-release size notice.
 */
function orderBaselineRuns(runs, headSha) {
    let pool = Array.isArray(runs) ? runs.filter(isCompletedSuccess) : [];
    if (headSha) {
        pool = pool.filter(run => run.head_sha === headSha);
    }
    const mergeGroups = pool.filter(run => run.event === "merge_group").sort(byMostRecentlyCreated);
    const others = pool.filter(run => run.event !== "merge_group").sort(byMostRecentlyCreated);
    return mergeGroups.concat(others);
}

/** Preferred baseline run, or null when every run is ineligible. */
function selectBaselineRun(runs, headSha) {
    return orderBaselineRuns(runs, headSha)[0] ?? null;
}

function isPlayReleaseSizeJob(name) {
    return PLAY_RELEASE_SIZE_JOB_NAME.test(String(name ?? ""));
}

/** Latest successful size-cell job, or the latest matching job if none succeeded. */
function selectPlayReleaseSizeJob(jobs) {
    const matches = (Array.isArray(jobs) ? jobs : []).filter(job =>
        isPlayReleaseSizeJob(job && job.name),
    );
    if (matches.length === 0) return null;
    const successful = matches.filter(job => String(job.conclusion).toLowerCase() === "success");
    const candidates = successful.length > 0 ? successful : matches;
    return candidates.slice().sort((left, right) => {
        const completedDelta =
            timestamp(right.completed_at || right.started_at) -
            timestamp(left.completed_at || left.started_at);
        if (completedDelta !== 0) return completedDelta;
        return (Number(right.id) || 0) - (Number(left.id) || 0);
    })[0];
}

function positiveInteger(value) {
    const text = String(value ?? "").trim();
    if (!/^[1-9]\d*$/.test(text)) return null;
    const bytes = Number(text);
    if (!Number.isSafeInteger(bytes)) return null;
    return bytes;
}

/**
 * Read the single play-release-apk-bytes notice.
 * ok false when the notice is missing, repeated, or not a positive integer.
 */
function parsePlayReleaseApkBytes(annotations) {
    const notices = (Array.isArray(annotations) ? annotations : []).filter(annotation => {
        if (!annotation || annotation.title !== NOTICE_TITLE) return false;
        return annotation.annotation_level == null || annotation.annotation_level === "notice";
    });
    if (notices.length === 0) return { ok: false, reason: "missing" };
    if (notices.length > 1) return { ok: false, reason: "ambiguous" };
    const bytes = positiveInteger(notices[0].message);
    if (bytes == null) return { ok: false, reason: "not-positive-integer" };
    return { ok: true, bytes };
}

module.exports = {
    NOTICE_TITLE,
    isPlayReleaseSizeJob,
    orderBaselineRuns,
    parsePlayReleaseApkBytes,
    selectBaselineRun,
    selectPlayReleaseSizeJob,
};

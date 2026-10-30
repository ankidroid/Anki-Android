"use strict";

// SPDX-License-Identifier: GPL-3.0-or-later

// Preference and parsing for the play-release APK size posted as a workflow notice.
// Callers list emulator runs and check-run annotations; this module stays free of I/O.
//
// The emulator job posts one notice per run:
//
//     ::notice title=play-release-apk-build::<built commit> <first parent> <bytes>
//
// <built commit> is the commit actions/checkout gave the job. It is not always the run's
// head_sha: a pull_request run builds GitHub's synthetic merge commit (refs/pull/N/merge),
// whose first parent is the base-branch tip the PR was merged with for testing. That first
// parent is the commit the PR APK must be compared against.
//
// push and merge_group runs build head_sha itself, so a baseline is only taken from those
// events, and only when the notice confirms that the built commit is head_sha.

const NOTICE_TITLE = "play-release-apk-build";
const COMMENT_AUTHOR = "github-actions[bot]";
const BASELINE_EVENTS = ["merge_group", "push"];
const PLAY_RELEASE_SIZE_JOB_NAME =
    /^Android Emulator Test \(36, x86_64, google_apis, [^,\s]+, 0\)$/;
const SHA = /^[0-9a-f]{40}$/;

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
 * Successful merge_group and push emulator runs for headSha, merge_group first.
 * Within an event, the most recently created run comes first.
 * Cancelled, failed, and in-progress runs are omitted, as are pull_request runs:
 * those build a synthetic merge commit rather than head_sha.
 * Callers walk this list until one run has a usable play-release build notice.
 */
function orderBaselineRuns(runs, headSha) {
    let pool = Array.isArray(runs)
        ? runs.filter(run => isCompletedSuccess(run) && BASELINE_EVENTS.includes(run.event))
        : [];
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
 * Read the single play-release-apk-build notice.
 * ok false when the notice is missing, repeated, or not "<sha> <sha> <positive integer>".
 */
function parsePlayReleaseApkBuild(annotations) {
    const notices = (Array.isArray(annotations) ? annotations : []).filter(annotation => {
        if (!annotation || annotation.title !== NOTICE_TITLE) return false;
        return annotation.annotation_level == null || annotation.annotation_level === "notice";
    });
    if (notices.length === 0) return { ok: false, reason: "missing" };
    if (notices.length > 1) return { ok: false, reason: "ambiguous" };
    const fields = String(notices[0].message ?? "")
        .trim()
        .split(/\s+/);
    if (fields.length !== 3) return { ok: false, reason: "malformed" };
    const [commitSha, baseSha, size] = fields;
    if (!SHA.test(commitSha) || !SHA.test(baseSha)) return { ok: false, reason: "malformed" };
    const bytes = positiveInteger(size);
    if (bytes == null) return { ok: false, reason: "not-positive-integer" };
    return { ok: true, commitSha, baseSha, bytes };
}

/**
 * The comparison comment this workflow posted earlier, or null.
 * Anyone can write the marker into a comment, so only the Actions bot's comments count.
 */
function selectComparisonComment(comments, marker) {
    return (
        (Array.isArray(comments) ? comments : []).find(
            comment => comment?.user?.login === COMMENT_AUTHOR && comment.body?.includes(marker),
        ) ?? null
    );
}

module.exports = {
    NOTICE_TITLE,
    isPlayReleaseSizeJob,
    orderBaselineRuns,
    parsePlayReleaseApkBuild,
    selectBaselineRun,
    selectComparisonComment,
    selectPlayReleaseSizeJob,
};

"use strict";

// SPDX-License-Identifier: GPL-3.0-or-later

const assert = require("node:assert/strict");
const test = require("node:test");
const {
    isPlayReleaseSizeJob,
    orderBaselineRuns,
    parsePlayReleaseApkBytes,
    selectBaselineRun,
    selectPlayReleaseSizeJob,
} = require("./apk-size-notice");

const HEAD = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

function run(overrides) {
    return {
        id: 1,
        event: "push",
        status: "completed",
        conclusion: "success",
        created_at: "2026-01-01T00:00:00Z",
        head_sha: HEAD,
        html_url: "https://github.com/example/repo/actions/runs/1",
        ...overrides,
    };
}

test("prefers a successful merge_group run over a successful push run for the same SHA", () => {
    const selected = selectBaselineRun(
        [
            run({
                id: 2,
                event: "push",
                created_at: "2026-02-01T00:00:00Z",
                html_url: "https://example.test/push",
            }),
            run({
                id: 1,
                event: "merge_group",
                created_at: "2026-01-01T00:00:00Z",
                html_url: "https://example.test/merge-group",
            }),
        ],
        HEAD,
    );
    assert.equal(selected.id, 1);
    assert.equal(selected.event, "merge_group");
});

test("ignores a cancelled push when a successful merge_group exists", () => {
    const selected = selectBaselineRun(
        [
            run({
                id: 10,
                event: "push",
                conclusion: "cancelled",
                created_at: "2026-03-01T00:00:00Z",
            }),
            run({ id: 11, event: "merge_group", created_at: "2026-01-01T00:00:00Z" }),
        ],
        HEAD,
    );
    assert.equal(selected.id, 11);
    assert.equal(selected.event, "merge_group");
});

test("chooses a successful push when no merge_group success exists", () => {
    const selected = selectBaselineRun(
        [
            run({
                id: 4,
                event: "merge_group",
                conclusion: "failure",
                created_at: "2026-04-01T00:00:00Z",
            }),
            run({ id: 5, event: "push", created_at: "2026-01-15T00:00:00Z" }),
        ],
        HEAD,
    );
    assert.equal(selected.id, 5);
    assert.equal(selected.event, "push");
});

test("returns a miss when every run is cancelled or failed", () => {
    const selected = selectBaselineRun(
        [
            run({ id: 1, event: "push", conclusion: "cancelled" }),
            run({ id: 2, event: "merge_group", conclusion: "failure" }),
            run({ id: 3, event: "push", status: "completed", conclusion: "cancelled" }),
        ],
        HEAD,
    );
    assert.equal(selected, null);
});

test("ignores in-progress runs", () => {
    const selected = selectBaselineRun(
        [
            run({
                id: 8,
                event: "merge_group",
                status: "in_progress",
                conclusion: null,
                created_at: "2026-05-01T00:00:00Z",
            }),
            run({ id: 9, event: "push", created_at: "2026-01-01T00:00:00Z" }),
        ],
        HEAD,
    );
    assert.equal(selected.id, 9);
});

test("uses the most recently created run when several successes share an event", () => {
    const ordered = orderBaselineRuns(
        [
            run({ id: 1, event: "merge_group", created_at: "2026-01-01T00:00:00Z" }),
            run({ id: 3, event: "push", created_at: "2026-06-01T00:00:00Z" }),
            run({
                id: 4,
                event: "push",
                conclusion: "cancelled",
                created_at: "2026-07-01T00:00:00Z",
            }),
            run({ id: 2, event: "merge_group", created_at: "2026-03-01T00:00:00Z" }),
        ],
        HEAD,
    );
    assert.deepEqual(
        ordered.map(candidate => candidate.id),
        [2, 1, 3],
    );
    assert.equal(
        selectBaselineRun(
            [
                run({ id: 1, event: "merge_group", created_at: "2026-01-01T00:00:00Z" }),
                run({ id: 3, event: "push", created_at: "2026-06-01T00:00:00Z" }),
                run({ id: 2, event: "merge_group", created_at: "2026-03-01T00:00:00Z" }),
            ],
            HEAD,
        ).id,
        2,
    );
});

test("ignores successful runs for a different SHA", () => {
    const selected = selectBaselineRun(
        [
            run({
                id: 1,
                event: "merge_group",
                head_sha: "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
            }),
            run({ id: 2, event: "push", head_sha: HEAD }),
        ],
        HEAD,
    );
    assert.equal(selected.id, 2);
});

test("parses a matching notice title", () => {
    const parsed = parsePlayReleaseApkBytes([
        { title: "other", message: "1", annotation_level: "notice" },
        { title: "play-release-apk-bytes", message: " 48291023 ", annotation_level: "notice" },
    ]);
    assert.deepEqual(parsed, { ok: true, bytes: 48291023 });
});

test("rejects a missing or non-numeric notice", () => {
    assert.deepEqual(parsePlayReleaseApkBytes([]), { ok: false, reason: "missing" });
    assert.deepEqual(
        parsePlayReleaseApkBytes([
            { title: "apk-size", message: "12", annotation_level: "notice" },
        ]),
        { ok: false, reason: "missing" },
    );
    assert.equal(parsePlayReleaseApkBytes(null).ok, false);
    for (const message of ["nope", "", "0", "-5", "1.5", "00", "12 bytes"]) {
        const parsed = parsePlayReleaseApkBytes([
            { title: "play-release-apk-bytes", message, annotation_level: "notice" },
        ]);
        assert.equal(parsed.ok, false, message);
        assert.equal(parsed.reason, "not-positive-integer", message);
    }
});

test("recognizes only the play-release size matrix cell", () => {
    const sizeJob = "Android Emulator Test (36, x86_64, google_apis, 600, 0)";
    assert.equal(isPlayReleaseSizeJob(sizeJob), true);
    assert.equal(
        isPlayReleaseSizeJob("Android Emulator Test (36, x86_64, google_apis_ps16k, 600, 0)"),
        false,
    );
    assert.equal(
        isPlayReleaseSizeJob("Android Emulator Test (37.0, x86_64, google_apis, 600, 0)"),
        false,
    );
    assert.equal(
        isPlayReleaseSizeJob("Android Emulator Test (36, x86_64, google_apis, 600, 1)"),
        false,
    );
    const selected = selectPlayReleaseSizeJob([
        {
            id: 1,
            name: "Android Emulator Test (36, x86_64, google_apis, 600, 1)",
            conclusion: "success",
        },
        { id: 2, name: sizeJob, conclusion: "success", completed_at: "2026-01-01T00:00:00Z" },
    ]);
    assert.equal(selected.id, 2);
});

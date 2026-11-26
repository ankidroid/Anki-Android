// SPDX-License-Identifier: GPL-3.0-or-later

// Run with: node --test .github/wait_for_screenshot_baseline.test.js
const test = require("node:test");
const assert = require("node:assert/strict");

const waitForScreenshotBaseline = require("./wait_for_screenshot_baseline.js");

const REPO = { owner: "ankidroid", repo: "Anki-Android" };

// Build a fake `github` whose listWorkflowRuns answers from a list of
// recorded store runs, honouring the same filters the real API applies.
function fakeGithub(storeRuns) {
    const calls = [];
    return {
        calls,
        rest: {
            actions: {
                async listWorkflowRuns(params) {
                    calls.push(params);
                    const matches = storeRuns.filter(
                        run =>
                            run.branch === params.branch &&
                            (params.head_sha === undefined || run.head_sha === params.head_sha) &&
                            run.event === params.event &&
                            run.status === params.status,
                    );
                    const workflow_runs = matches.slice(0, params.per_page ?? matches.length);
                    return { data: { workflow_runs } };
                },
            },
        },
    };
}

const core = { info() {} };
const noSleep = () => Promise.resolve();

function prContext(base) {
    return { ...REPO, payload: { pull_request: { base } } };
}

test("returns the baseline recorded for a PR base on main", async () => {
    const github = fakeGithub([
        { id: 101, branch: "main", head_sha: "basesha", event: "push", status: "success" },
    ]);
    const runId = await waitForScreenshotBaseline({
        github,
        context: prContext({ sha: "basesha", ref: "main" }),
        core,
        sleep: noSleep,
    });
    assert.equal(runId, 101);
});

test("waits until the base baseline finishes recording", async () => {
    let attempts = 0;
    const github = {
        rest: {
            actions: {
                async listWorkflowRuns() {
                    attempts++;
                    // The store run is still recording for the first two polls.
                    if (attempts < 3) return { data: { workflow_runs: [] } };
                    return { data: { workflow_runs: [{ id: 202 }] } };
                },
            },
        },
    };
    const runId = await waitForScreenshotBaseline({
        github,
        context: prContext({ sha: "basesha", ref: "main" }),
        core,
        sleep: noSleep,
    });
    assert.equal(runId, 202);
    assert.equal(attempts, 3);
});

// Regression test for stacked PRs: the base branch is another feature branch,
// which never stores a screenshot baseline (screenshot_store.yml only records
// on pushes to main / dependency-updates). The compare job must still find a
// baseline to compare against instead of timing out after twenty minutes.
test("falls back to the latest main baseline for a stacked PR", async () => {
    const github = fakeGithub([
        { id: 303, branch: "main", head_sha: "mainsha", event: "push", status: "success" },
    ]);
    const runId = await waitForScreenshotBaseline({
        github,
        context: prContext({ sha: "stackedsha", ref: "fix/some-feature-branch" }),
        core,
        sleep: noSleep,
    });
    assert.equal(runId, 303);
});

test("throws when no baseline exists even on main", async () => {
    const github = fakeGithub([]);
    await assert.rejects(
        waitForScreenshotBaseline({
            github,
            context: prContext({ sha: "stackedsha", ref: "fix/some-feature-branch" }),
            core,
            sleep: noSleep,
        }),
        /No screenshot baseline/,
    );
});

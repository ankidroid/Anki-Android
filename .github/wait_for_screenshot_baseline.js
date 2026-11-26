// SPDX-License-Identifier: GPL-3.0-or-later

// Branches whose pushes record a screenshot baseline (see screenshot_store.yml).
// A PR based on any other branch (a stacked PR) never gets a baseline for its
// exact base commit, so we fall back to the latest main baseline instead.
const BASELINE_BRANCHES = ["main", "dependency-updates"];

module.exports = async function waitForScreenshotBaseline({
    github,
    context,
    core,
    sleep = delay => new Promise(resolve => setTimeout(resolve, delay)),
}) {
    const base = context.payload.pull_request?.base || {
        sha: context.sha,
        ref: context.ref.replace(/^refs\/heads\//, ""),
    };

    // The comparison checkout includes this base commit. A previous successful
    // baseline can incorrectly attribute changes on main to an unrelated PR, so
    // for PRs targeting a baseline branch we pin to the base commit's own run.
    // The matching store run may still be recording, so wait up to twenty minutes.
    if (BASELINE_BRANCHES.includes(base.ref)) {
        for (let attempt = 0; attempt < 40; attempt++) {
            const {
                data: { workflow_runs: runs },
            } = await github.rest.actions.listWorkflowRuns({
                ...context.repo,
                workflow_id: "screenshot_store.yml",
                branch: base.ref,
                head_sha: base.sha,
                event: "push",
                status: "success",
                per_page: 1,
            });
            if (runs.length) {
                core.info(`Using screenshot baseline from ${base.sha}: run ${runs[0].id}`);
                return runs[0].id;
            }
            core.info(`Waiting for a successful screenshot baseline for ${base.sha}`);
            await sleep(30_000);
        }
        throw new Error(`No successful screenshot baseline for ${base.sha} after twenty minutes`);
    }

    // Stacked PR: the base branch never stores a baseline for base.sha, so
    // compare against the most recent main baseline rather than timing out.
    const {
        data: { workflow_runs: runs },
    } = await github.rest.actions.listWorkflowRuns({
        ...context.repo,
        workflow_id: "screenshot_store.yml",
        branch: "main",
        event: "push",
        status: "success",
        per_page: 1,
    });
    if (runs.length) {
        core.info(
            `Base ${base.ref} (${base.sha}) stores no baseline; using latest main baseline: run ${runs[0].id}`,
        );
        return runs[0].id;
    }
    throw new Error(`No screenshot baseline available for base ${base.ref} (${base.sha})`);
};

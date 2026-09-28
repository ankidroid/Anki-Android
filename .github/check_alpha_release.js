// SPDX-License-Identifier: GPL-3.0-or-later

const fs = require("node:fs");

module.exports = async function checkAlphaRelease({ github, context, core }) {
    const metadata = JSON.parse(
        fs.readFileSync("AnkiDroid/build/outputs/release-metadata.json", "utf8"),
    );
    if (metadata.releaseType !== "alpha") {
        core.info("Skipping scheduled release: main is not on an alpha version.");
        return false;
    }

    const releases = await github.paginate(github.rest.repos.listReleases, {
        ...context.repo,
        per_page: 100,
    });
    // Use calendar days to check, so last Monday's release doesn't block this release.
    const day = 24 * 60 * 60 * 1000;
    const today = Math.floor(Date.now() / day);
    const recentRelease = releases.find(
        release => !release.draft && today - Math.floor(Date.parse(release.published_at) / day) < 7,
    );
    if (recentRelease) {
        core.info(
            `Skipping scheduled release: ${recentRelease.tag_name} was published fewer than seven UTC calendar days ago.`,
        );
        return false;
    }

    const latestAlpha = releases
        .filter(release => !release.draft && /^v\d+\.\d+\.\d+alpha\d+$/.test(release.tag_name))
        .sort((a, b) => Date.parse(b.published_at) - Date.parse(a.published_at))[0];
    if (!latestAlpha) {
        core.info("Skipping scheduled release: no published alpha to compare against.");
        return false;
    }

    const { data: comparison } = await github.rest.repos.compareCommitsWithBasehead({
        ...context.repo,
        basehead: `${latestAlpha.tag_name}...${context.sha}`,
    });
    if (comparison.status !== "ahead" || comparison.ahead_by === 0) {
        core.info(
            `Skipping scheduled release: no new commits descending from ${latestAlpha.tag_name}.`,
        );
        return false;
    }

    core.info(
        `Publishing an alpha: ${comparison.ahead_by} new commits since ${latestAlpha.tag_name}.`,
    );
    return true;
};

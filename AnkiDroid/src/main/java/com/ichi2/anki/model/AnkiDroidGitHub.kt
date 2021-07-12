/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.model

import android.annotation.SuppressLint
import android.net.Uri
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.R
import com.ichi2.anki.model.AnkiDroidGitHub.GitHubIssue.GitHubIssueLink
import java.net.URLEncoder

class AnkiDroidGitHub {

    companion object {
        const val GITHUB_URL_MAX_LENGTH = 8192
        const val GITHUB_END_CODE_BLOCK = "\n```"

        @JvmStatic
        fun getIssueLink(id: Int): GitHubIssueLink =
            GitHubIssueLink(
                url = "$repoIssueTracker/$id",
                type = GitHubIssueLink.LinkType.KNOWN_ISSUE
            )

        @JvmStatic
        fun getIssueLink(issue: GitHubIssue): GitHubIssueLink {
            return GitHubIssueLink(url = getIssueUrl(issue), GitHubIssueLink.LinkType.NEW_ISSUE)
        }

        private fun getIssueUrl(issue: GitHubIssue): String {
            fun enc(s: String) = urlEncode(s)
            val ret = "$repoIssueTracker/new?template=${enc(issue.template)}&title=${enc(issue.title)}&body=${enc(issue.body)}"

            // trim the body if it's too long
            if (ret.length >= GITHUB_URL_MAX_LENGTH) {
                var ending = "..."

                // special case for ending in a code block
                if (issue.body.endsWith(GITHUB_END_CODE_BLOCK)) {
                    ending += enc(GITHUB_END_CODE_BLOCK)
                }

                return ret.substring(0, GITHUB_URL_MAX_LENGTH - ending.length - 5) + ending
            }

            return ret
        }

        // https://stackoverflow.com/questions/2678551/when-to-encode-space-to-plus-or-20
        private fun urlEncode(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

        private val repoIssueTracker; get() = AnkiDroidApp.getInstance().getString(R.string.link_issue_tracker)
    }

    @SuppressLint("NonPublicNonStaticFieldName")
    data class GitHubIssue(val title: String, val body: String, val template: String) {
        companion object {
            fun gitHubBug(title: String, body: String) = GitHubIssue(title, body, "issue_template.md")
        }

        data class GitHubIssueLink(val url: String, val type: LinkType) {
            fun asUrl(): Uri? = Uri.parse(url)

            enum class LinkType {
                NEW_ISSUE,
                KNOWN_ISSUE
            }
        }
    }
}

/****************************************************************************************
 *                                                                                      *
 * Copyright (c) 2021 Shridhar Goel <shridhar.goel@gmail.com>                           *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.anki

import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.github.appintro.AppIntro
import com.github.appintro.AppIntroFragment
import com.github.appintro.AppIntroPageTransformerType

/**
 * App introduction for new users.
 */
class IntroductionActivity : AppIntro() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTransformer(AppIntroPageTransformerType.Zoom)

        val welcomeSlide = IntroductionResources(
            R.string.collection_load_welcome_request_permissions_title,
            R.string.introduction_desc,
            R.drawable.ankidroid_logo
        )

        val decksSlide = IntroductionResources(
            R.string.decks_intro,
            R.string.decks_intro_desc,
            R.drawable.decks
        )

        val cardsSlide = IntroductionResources(
            R.string.create_cards_intro,
            R.string.create_cards_intro_desc,
            R.drawable.create_cards
        )

        val premadeDecksSlide = IntroductionResources(
            R.string.pre_made_decks,
            R.string.pre_made_decks_desc,
            R.drawable.premade_decks
        )

        val reviewerSlide = IntroductionResources(
            R.string.study_time,
            R.string.study_desc,
            R.drawable.review
        )

        val statisticsSlide = IntroductionResources(
            R.string.detailed_statistics,
            R.string.detailed_statistics_desc,
            R.drawable.statistics
        )

        val nightModeSlide = IntroductionResources(
            R.string.night_mode,
            R.string.night_mode_desc,
            R.drawable.night_mode
        )

        val slidesList = listOf(welcomeSlide, decksSlide, cardsSlide, premadeDecksSlide, reviewerSlide, statisticsSlide, nightModeSlide)
        slidesList.forEach {
            insertSlide(it)
        }
    }

    override fun onSkipPressed(currentFragment: Fragment?) {
        super.onSkipPressed(currentFragment)
        finish()
    }

    override fun onDonePressed(currentFragment: Fragment?) {
        super.onDonePressed(currentFragment)
        finish()
    }

    /**
     * Insert a slide to be shown in the introduction
     * resources.mTitle -> Title of the slide
     * resources.mDescription -> Description of the slide
     * resources.mImage -> Image of the slide
     */
    private fun insertSlide(resources: IntroductionResources) {
        addSlide(
            AppIntroFragment.newInstance(
                title = this.getString(resources.mTitle),
                description = this.getString(resources.mDescription),
                backgroundColor = ContextCompat.getColor(this, R.color.material_blue_500),
                imageDrawable = resources.mImage
            )
        )
    }

    data class IntroductionResources(
        val mTitle: Int,
        val mDescription: Int,
        val mImage: Int
    )
}

// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.reviewer

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.core.view.updateLayoutParams
import anki.scheduler.CardAnswer.Rating
import com.ichi2.anki.databinding.ViewAnswerAreaBinding

class AnswerAreaView : FrameLayout {
    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    private val binding = ViewAnswerAreaBinding.inflate(LayoutInflater.from(context), this)

    fun setButtonListeners(
        onRatingClicked: (Rating) -> Unit,
        onShowAnswerClicked: () -> Unit,
    ) {
        binding.showAnswerButton.setOnClickListener { onShowAnswerClicked() }
        binding.againButton.setOnClickListener { onRatingClicked(Rating.AGAIN) }
        binding.hardButton.setOnClickListener { onRatingClicked(Rating.HARD) }
        binding.goodButton.setOnClickListener { onRatingClicked(Rating.GOOD) }
        binding.easyButton.setOnClickListener { onRatingClicked(Rating.EASY) }
    }

    fun setNextTimes(times: AnswerButtonsNextTime?) {
        binding.againButton.setNextTime(times?.again)
        binding.hardButton.setNextTime(times?.hard)
        binding.goodButton.setNextTime(times?.good)
        binding.easyButton.setNextTime(times?.easy)
    }

    fun setRelativeHeight(buttonsHeightPercentage: Int) {
        if (buttonsHeightPercentage <= 100) return
        binding.answerButtonsLayout.post {
            binding.answerButtonsLayout.updateLayoutParams {
                height = binding.answerButtonsLayout.measuredHeight * buttonsHeightPercentage / 100
            }
        }
    }

    fun setAnswerState(isAnswerShown: Boolean) {
        if (isAnswerShown) {
            binding.showAnswerButton.visibility = INVISIBLE
            binding.answerButtonsLayout.visibility = VISIBLE
        } else {
            binding.showAnswerButton.visibility = VISIBLE
            binding.answerButtonsLayout.visibility = INVISIBLE
        }
    }

    fun hideHardAndEasyButtons() {
        binding.hardButton.visibility = GONE
        binding.easyButton.visibility = GONE
    }
}

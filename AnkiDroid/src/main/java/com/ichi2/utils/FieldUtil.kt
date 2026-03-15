package com.ichi2.utils

import androidx.annotation.StringRes
import com.ichi2.anki.R
import com.ichi2.utils.FieldUtil.NO_POSITION
import org.jetbrains.annotations.Contract
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

object FieldUtil {
    /**
     * Cleans the input field or explain why it's rejected
     * @param nameList the list of existing field names
     * @param position the position of the field or [NO_POSITION] to add to the end
     * @param newName the input
     * @return the result UniqueNameResult.Success which contains the unique name or UniqueNameResult.Failure which contains string resource id of the reason why it's rejected
     *
     */
    fun uniqueName(
        nameList: List<String>,
        position: Int = NO_POSITION,
        newName: String,
    ): UniqueNameResult {
        var input =
            newName
                .replace("[\\n\\r{}:\"]".toRegex(), "")
        // The number of #, ^, /, space, tab, starting the input
        var offset = 0
        while (offset < input.length) {
            if (!listOf('#', '^', '/', ' ', '\t').contains(input[offset])) {
                break
            }
            offset++
        }
        input = input.substring(offset).trim()
        if (input.isEmpty()) {
            return UniqueNameResult.Failure.EmptyName
        }
        val otherFields = nameList.filterIndexed { index, _ -> index != position }
        if (otherFields.any { it == input }) {
            return UniqueNameResult.Failure.DuplicateName
        }
        return UniqueNameResult.Success(input)
    }

    sealed class UniqueNameResult {
        data class Success(
            /**
             * The unique name of the field
             */
            val name: String,
        ) : UniqueNameResult()

        sealed class Failure(
            /**
             * The string resource id of the reason why the name is rejected
             */
            @StringRes val resId: Int,
        ) : UniqueNameResult() {
            object EmptyName : Failure(R.string.toast_empty_name)

            object DuplicateName : Failure(R.string.toast_duplicate_field)
        }

        @OptIn(ExperimentalContracts::class)
        @Contract
        fun fold(
            onSuccess: (String) -> Unit,
            onFailure: (resId: Int) -> Unit,
        ) {
            contract {
                callsInPlace(onSuccess, InvocationKind.AT_MOST_ONCE)
                callsInPlace(onFailure, InvocationKind.AT_MOST_ONCE)
            }
            when (this) {
                is Success -> onSuccess(name)
                is Failure -> onFailure(resId)
            }
        }
    }

    const val NO_POSITION = -1
}

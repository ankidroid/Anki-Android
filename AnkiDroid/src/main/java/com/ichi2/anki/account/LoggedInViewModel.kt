package com.ichi2.anki.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.settings.Prefs
import kotlinx.coroutines.launch

class LoggedInViewModel : ViewModel() {
    val username: String?
        get() = Prefs.username

    /**
     * Handles the logic for logging out the user.
     */
    fun onLogoutClicked() {
        viewModelScope.launch {
            Prefs.hkey = null
            Prefs.username = null
            Prefs.currentSyncUri = null

            withCol {
                media.forceResync()
            }
        }
    }
}

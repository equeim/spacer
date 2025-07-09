// SPDX-FileCopyrightText: 2022-2025 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import org.equeim.spacer.getDonkiNotificationsRepositoryInstance

class MainScreenViewModel(application: Application) : AndroidViewModel(application) {
    val numberOfUnreadNotifications: StateFlow<Int> = getDonkiNotificationsRepositoryInstance(application)
        .getNumberOfUnreadNotifications()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)
}

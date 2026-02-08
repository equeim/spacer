// SPDX-FileCopyrightText: 2022-2025 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui.screens

import android.os.ParcelUuid
import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.ViewModelStoreOwner
import kotlinx.parcelize.Parcelize
import org.equeim.spacer.utils.ExtendedViewModelStoreNavEntryDecorator
import java.util.UUID

interface Destination : Parcelable {
    @Composable
    fun Content(navController: NavController)
}

@Stable
class NavController private constructor(
    initialBackStack: List<BackStackEntry>,
    private val viewModelStoreDecorator: ExtendedViewModelStoreNavEntryDecorator<BackStackEntry>
) {
    constructor(
        initialDestination: Destination,
        viewModelStoreDecorator: ExtendedViewModelStoreNavEntryDecorator<BackStackEntry>
    ) : this(listOf(BackStackEntry(initialDestination)), viewModelStoreDecorator)

    private val _backStack = mutableStateListOf<BackStackEntry>().apply { addAll(initialBackStack) }
    val backStack: List<BackStackEntry> by ::_backStack

    fun navigateTo(destination: Destination) {
        _backStack.add(BackStackEntry(destination))
    }

    fun popBackStack() {
        _backStack.removeLastOrNull()
    }

    fun popUpTo(destination: Destination) {
        val index = _backStack.indexOfLast { it.destination == destination }
        if (index in 0..<_backStack.lastIndex) {
            _backStack.removeRange(index + 1, _backStack.lastIndex)
        }
    }

    @Composable
    fun viewModelStoreOwnerForDestination(destination: Destination): ViewModelStoreOwner {
        val backStackEntry = _backStack.findLast { it.destination == destination }
        checkNotNull(backStackEntry) { "Destination $destination does not exist on the back stack" }
        return viewModelStoreDecorator.viewModelStoreOwnerForKey(backStackEntry.contentKey)
    }

    @Parcelize
    data class BackStackEntry(
        val destination: Destination,
        private val uuid: ParcelUuid = ParcelUuid(UUID.randomUUID())
    ) : Parcelable {
        val contentKey: Any get() = uuid
    }

    companion object {
        fun Saver(viewModelStoreDecorator: ExtendedViewModelStoreNavEntryDecorator<BackStackEntry>) =
            Saver<NavController, List<BackStackEntry>>(
                save = { it.backStack },
                restore = { NavController(it, viewModelStoreDecorator) }
            )
    }
}

@Composable
fun rememberNavController(
    initialDestination: Destination,
    viewModelStoreDecorator: ExtendedViewModelStoreNavEntryDecorator<NavController.BackStackEntry>
): NavController {
    return rememberSaveable(saver = NavController.Saver(viewModelStoreDecorator)) {
        NavController(
            initialDestination,
            viewModelStoreDecorator
        )
    }
}

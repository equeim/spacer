// SPDX-FileCopyrightText: 2022-2023 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.donki

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Suppress("PropertyName")
interface CoroutineDispatchers {
    val Main: CoroutineDispatcher
    val MainImmediate: CoroutineDispatcher
    val Default: CoroutineDispatcher
    val IO: CoroutineDispatcher
}

fun CoroutineDispatchers(): CoroutineDispatchers = StandardCoroutineDispatchers

private object StandardCoroutineDispatchers : CoroutineDispatchers {
    override val Main: CoroutineDispatcher
        get() = Dispatchers.Main
    override val MainImmediate: CoroutineDispatcher
        get() = Dispatchers.Main.immediate
    override val Default: CoroutineDispatcher
        get() = Dispatchers.Default
    override val IO: CoroutineDispatcher
        get() = Dispatchers.IO
}

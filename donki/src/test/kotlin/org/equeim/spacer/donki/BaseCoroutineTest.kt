// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

@file:OptIn(ExperimentalCoroutinesApi::class)

package org.equeim.spacer.donki

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import java.util.concurrent.Executor

abstract class BaseCoroutineTest : BaseTest() {
    protected val coroutineDispatchers: CoroutineDispatchers = TestCoroutineDispatchers(
        StandardTestDispatcher()
    ).also { Dispatchers.setMain(it.Main) }
    protected val testExecutor: Executor by lazy { coroutineDispatchers.Default.asExecutor() }

    override fun after() {
        Dispatchers.resetMain()
        super.after()
    }
}

private class TestCoroutineDispatchers(private val testDispatcher: TestDispatcher) : CoroutineDispatchers {
    override val Main: CoroutineDispatcher
        get() = testDispatcher
    override val MainImmediate: CoroutineDispatcher
        get() = testDispatcher
    override val Default: CoroutineDispatcher
        get() = testDispatcher
    override val IO: CoroutineDispatcher
        get() = testDispatcher
}

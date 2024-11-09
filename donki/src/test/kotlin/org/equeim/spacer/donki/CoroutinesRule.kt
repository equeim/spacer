// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.donki

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.ExternalResource
import java.util.concurrent.Executor

@OptIn(ExperimentalCoroutinesApi::class)
class CoroutinesRule : ExternalResource() {
    val testDispatcher = StandardTestDispatcher()
    val coroutineDispatchers: CoroutineDispatchers = TestCoroutineDispatchers(testDispatcher)
    val testExecutor: Executor by lazy { coroutineDispatchers.Default.asExecutor() }

    init {
        Dispatchers.setMain(coroutineDispatchers.Default)
    }

    override fun after() {
        Dispatchers.resetMain()
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
}
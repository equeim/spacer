// SPDX-FileCopyrightText: 2022 Alexey Rochev
//
// SPDX-License-Identifier: MIT

@file:OptIn(ExperimentalCoroutinesApi::class)

package org.equeim.spacer.donki

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

abstract class BaseCoroutineTest : BaseTest() {
    protected lateinit var coroutineDispatchers: CoroutineDispatchers

    override fun before() {
        super.before()
        val testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        coroutineDispatchers = TestCoroutineDispatchers(testDispatcher)
    }

    override fun after() {
        super.after()
        Dispatchers.resetMain()
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

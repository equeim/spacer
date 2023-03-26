// SPDX-FileCopyrightText: 2022-2023 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.donki

import android.util.Log
import androidx.annotation.CallSuper
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

abstract class BaseTest {
    @BeforeTest
    @CallSuper
    open fun before() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } answers { println("D ${args[0]}: ${args[1]}"); 0 }
        every { Log.e(any(), any(), any()) } answers {
            println("E ${args[0]}: ${args[1]}")
            arg<Throwable>(2).printStackTrace(System.out)
            0
        }
        every { Log.e(any(), any()) } answers { println("E ${args[0]}: ${args[1]}"); 0 }
    }

    @AfterTest
    @CallSuper
    open fun after() {
        unmockkStatic(Log::class)
    }
}

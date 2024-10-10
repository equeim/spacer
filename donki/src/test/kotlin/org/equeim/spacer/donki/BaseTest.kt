// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.donki

import androidx.annotation.CallSuper
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

abstract class BaseTest {
    @BeforeTest
    @CallSuper
    open fun before() {
        mockkLog()
    }

    @AfterTest
    @CallSuper
    open fun after() {
        unmockkLog()
    }
}

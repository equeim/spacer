// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.donki

import android.util.Log
import io.mockk.Answer
import io.mockk.Call
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.rules.ExternalResource

class MockkLogRule : ExternalResource() {
    private var mocked = true

    override fun before() {
        mockkStatic(Log::class)

        every { Log.v(any(), any()) } answers LogAnswer
        every { Log.v(any(), any(), any()) } answers LogAnswer

        every { Log.d(any(), any()) } answers LogAnswer
        every { Log.d(any(), any(), any()) } answers LogAnswer

        every { Log.i(any(), any()) } answers LogAnswer
        every { Log.i(any(), any(), any()) } answers LogAnswer

        every { Log.w(any(), any<String>()) } answers LogAnswer
        every { Log.w(any(), any<Throwable>()) } answers LogAnswer
        every { Log.w(any(), any(), any()) } answers LogAnswer

        every { Log.e(any(), any()) } answers LogAnswer
        every { Log.e(any(), any(), any()) } answers LogAnswer

        every { Log.wtf(any(), any<String>()) } answers LogAnswer
        every { Log.wtf(any(), any<Throwable>()) } answers LogAnswer
        every { Log.wtf(any(), any(), any()) } answers LogAnswer

        every { Log.println(any(), any(), any()) } answers LogAnswer
    }

    override fun after() {
        if (mocked) {
            unmockkStatic(Log::class)
        }
    }

    private object LogAnswer : Answer<Int> {
        override fun answer(call: Call): Int {
            val args = call.invocation.args.toMutableList()

            val priority: String
            val tag: String

            when (val arg = args.removeFirst()) {
                is Int -> {
                    priority = when (args[0] as Int) {
                        Log.VERBOSE -> "V"
                        Log.DEBUG -> "D"
                        Log.INFO -> "I"
                        Log.WARN -> "W"
                        Log.ERROR -> "E"
                        Log.ASSERT -> "ASSERT"
                        else -> throw IllegalArgumentException()
                    }
                    tag = args.removeFirst() as String
                }
                is String -> {
                    priority = call.invocation.method.name.uppercase()
                    tag = arg
                }
                else -> throw IllegalArgumentException()
            }

            val message: String
            val throwable: Throwable?

            when (val arg = args.removeFirst()) {
                is String -> {
                    message = arg
                    throwable = args.removeFirstOrNull() as Throwable?
                }
                is Throwable -> {
                    message = ""
                    throwable = arg
                }
                else -> throw IllegalArgumentException()
            }

            print(priority)
            print('/')
            print(tag)
            print(": ")
            println(message)
            throwable?.printStackTrace()

            return 0
        }
    }
}

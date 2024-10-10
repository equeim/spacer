// SPDX-FileCopyrightText: 2022-2024 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.ui.utils

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel

fun AndroidViewModel.getString(@StringRes resId: Int): String =
    getApplication<Application>().getString(resId)

fun AndroidViewModel.getString(@StringRes resId: Int, vararg formatArgs: Any): String =
    getApplication<Application>().getString(resId, *formatArgs)

// SPDX-FileCopyrightText: 2022-2025 Alexey Rochev
//
// SPDX-License-Identifier: MIT

package org.equeim.spacer.donki.data.common

enum class NeedToRefreshState {
    DontNeedToRefresh,
    HaveWeeksThatNeedRefreshButAllCachedRecently,
    HaveWeeksThatNeedRefreshNow,
}

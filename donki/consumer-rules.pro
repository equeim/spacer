# SPDX-FileCopyrightText: 2022-2023 Alexey Rochev
#
# SPDX-License-Identifier: CC0-1.0

# R8 removes classes if their only usage in code is return type of Retrofit service interface
-keep class * extends org.equeim.spacer.donki.data.model.Event

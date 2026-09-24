/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.compose.ui.window

import androidx.compose.ui.ExperimentalMediaQueryApi
import androidx.compose.ui.UiMediaScope
import androidx.compose.ui.UiMediaScope.PointerPrecision
import androidx.compose.ui.platform.SkikoMediaScope

/**
 * Tracks the highest-precision pointing device available to the browser using the
 * `any-pointer` media feature and exposes it as [UiMediaScope.pointerPrecision].
 *
 * CSS has no equivalent of [PointerPrecision.Blunt], so it is never reported.
 *
 * See https://developer.mozilla.org/en-US/docs/Web/CSS/@media/any-pointer
 */
@ExperimentalMediaQueryApi
internal class PointerPrecisionObserver {
    private val fineListener = object : MediaQueryListener("(any-pointer: fine)") {
        override fun onChange(matches: Boolean) = update()
    }

    private val coarseListener = object : MediaQueryListener("(any-pointer: coarse)") {
        override fun onChange(matches: Boolean) = update()
    }

    private val _mediaScope = SkikoMediaScope(resolvePointerPrecision())

    val mediaScope: UiMediaScope get() = _mediaScope

    private fun resolvePointerPrecision(): PointerPrecision = when {
        fineListener.matches() == MediaQueryStatus.MATCH -> PointerPrecision.Fine
        coarseListener.matches() == MediaQueryStatus.MATCH -> PointerPrecision.Coarse
        else -> PointerPrecision.None
    }

    // A device change usually flips both queries, so re-read both instead of trusting the event.
    private fun update() {
        _mediaScope.pointerPrecision = resolvePointerPrecision()
    }

    fun dispose() {
        fineListener.dispose()
        coarseListener.dispose()
    }
}

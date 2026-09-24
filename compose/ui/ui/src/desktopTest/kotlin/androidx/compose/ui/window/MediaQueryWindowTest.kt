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
import androidx.compose.ui.UiMediaScope.PointerPrecision
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.mediaQuery
import com.google.common.truth.Truth.assertThat
import java.awt.Dimension
import kotlin.test.Test

@OptIn(ExperimentalMediaQueryApi::class)
class MediaQueryWindowTest {
    @Test
    fun pointerPrecisionIsFine() = runApplicationTest {
        var pointerPrecision: PointerPrecision? = null

        val window = ComposeWindow()
        try {
            window.setContent {
                pointerPrecision = mediaQuery { this.pointerPrecision }
            }
            window.size = Dimension(100, 100)
            window.isVisible = true

            awaitIdle()

            assertThat(pointerPrecision).isEqualTo(PointerPrecision.Fine)
        } finally {
            window.dispose()
        }
    }
}

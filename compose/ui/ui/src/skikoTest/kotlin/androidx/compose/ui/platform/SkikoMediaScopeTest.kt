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

package androidx.compose.ui.platform

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalMediaQueryApi
import androidx.compose.ui.SkikoComposeTestBase
import androidx.compose.ui.UiMediaScope
import androidx.compose.ui.UiMediaScope.PointerPrecision
import androidx.compose.ui.mediaQuery
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.use
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalMediaQueryApi::class)
class SkikoMediaScopeTest : SkikoComposeTestBase() {

    @Test
    fun defaultPlatformContextReportsNoPointer() = runTest(StandardTestDispatcher()) {
        assertEquals(PointerPrecision.None, readPointerPrecision(PlatformContext.Empty()))
    }

    @Test
    fun mediaQueryReadsPlatformPointerPrecision() = runTest(StandardTestDispatcher()) {
        val mediaScope = SkikoMediaScope(PointerPrecision.Fine)
        val platformContext = object : PlatformContext by PlatformContext.Empty() {
            override val mediaScope: UiMediaScope = mediaScope
        }

        assertEquals(PointerPrecision.Fine, readPointerPrecision(platformContext))
    }

    @Test
    fun mediaQueryRecomposesWhenPointerPrecisionChanges() = runTest(StandardTestDispatcher()) {
        val mediaScope = SkikoMediaScope(PointerPrecision.Coarse)
        val platformContext = object : PlatformContext by PlatformContext.Empty() {
            override val mediaScope: UiMediaScope = mediaScope
        }
        val frameRecomposer = FrameRecomposer(coroutineContext)
        var pointerPrecision: PointerPrecision? = null

        CanvasLayersComposeScene(
            frameRecomposer = frameRecomposer,
            size = IntSize(100, 100),
            platformContext = platformContext,
        ).use { scene ->
            scene.setContent {
                pointerPrecision = mediaQuery { this.pointerPrecision }
            }
            assertEquals(PointerPrecision.Coarse, pointerPrecision)

            mediaScope.pointerPrecision = PointerPrecision.Fine
            Snapshot.sendApplyNotifications()
            frameRecomposer.performFrame(0)
            assertEquals(PointerPrecision.Fine, pointerPrecision)
        }
        frameRecomposer.close()
    }

    private fun kotlinx.coroutines.test.TestScope.readPointerPrecision(
        platformContext: PlatformContext,
    ): PointerPrecision? {
        val frameRecomposer = FrameRecomposer(coroutineContext)
        var pointerPrecision: PointerPrecision? = null
        CanvasLayersComposeScene(
            frameRecomposer = frameRecomposer,
            size = IntSize(100, 100),
            platformContext = platformContext,
        ).use { scene ->
            scene.setContent {
                pointerPrecision = mediaQuery { this.pointerPrecision }
            }
        }
        frameRecomposer.close()
        return pointerPrecision
    }
}

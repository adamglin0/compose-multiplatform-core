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
import kotlin.test.Test
import kotlin.test.assertEquals
import platform.Foundation.NSNotificationCenter
import platform.GameController.GCMouseDidConnectNotification
import platform.GameController.GCMouseDidDisconnectNotification

@OptIn(ExperimentalMediaQueryApi::class)
class PointerPrecisionListenerTest {
    private val notificationCenter = NSNotificationCenter()
    private var hasMouse = false

    private fun createListener(isAppOnMac: Boolean = false) = PointerPrecisionListener(
        notificationCenter = notificationCenter,
        hasConnectedMouse = { hasMouse },
        isAppOnMac = isAppOnMac,
    )

    private fun postMouseNotification(connected: Boolean) {
        notificationCenter.postNotificationName(
            if (connected) GCMouseDidConnectNotification else GCMouseDidDisconnectNotification,
            `object` = null
        )
    }

    @Test
    fun touchscreenIsCoarse() {
        val listener = createListener()
        assertEquals(PointerPrecision.Coarse, listener.mediaScope.pointerPrecision)
        listener.dispose()
    }

    @Test
    fun connectedMouseIsFine() {
        hasMouse = true
        val listener = createListener()
        assertEquals(PointerPrecision.Fine, listener.mediaScope.pointerPrecision)
        listener.dispose()
    }

    @Test
    fun appOnMacIsFine() {
        val listener = createListener(isAppOnMac = true)
        assertEquals(PointerPrecision.Fine, listener.mediaScope.pointerPrecision)
        listener.dispose()
    }

    @Test
    fun followsMouseConnection() {
        val listener = createListener()

        hasMouse = true
        postMouseNotification(connected = true)
        assertEquals(PointerPrecision.Fine, listener.mediaScope.pointerPrecision)

        hasMouse = false
        postMouseNotification(connected = false)
        assertEquals(PointerPrecision.Coarse, listener.mediaScope.pointerPrecision)

        listener.dispose()
    }

    @Test
    fun observedPrecisePointerIsSticky() {
        val listener = createListener()

        listener.onPrecisePointerObserved()
        assertEquals(PointerPrecision.Fine, listener.mediaScope.pointerPrecision)

        postMouseNotification(connected = false)
        assertEquals(PointerPrecision.Fine, listener.mediaScope.pointerPrecision)

        listener.dispose()
    }

    @Test
    fun stopsUpdatingAfterDispose() {
        val listener = createListener()
        listener.dispose()

        hasMouse = true
        postMouseNotification(connected = true)
        assertEquals(PointerPrecision.Coarse, listener.mediaScope.pointerPrecision)
    }
}

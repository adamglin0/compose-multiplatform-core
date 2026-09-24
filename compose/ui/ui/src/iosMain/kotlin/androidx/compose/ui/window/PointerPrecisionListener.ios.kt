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
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ObjCAction
import platform.Foundation.NSNotification
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSSelectorFromString
import platform.Foundation.NSThread
import platform.Foundation.isiOSAppOnMac
import platform.GameController.GCMouse
import platform.GameController.GCMouseDidConnectNotification
import platform.GameController.GCMouseDidDisconnectNotification
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * Tracks the highest-precision pointing device available on iOS and exposes it as
 * [UiMediaScope.pointerPrecision].
 *
 * The touchscreen is always available, so the baseline is [PointerPrecision.Coarse]. The
 * precision is [PointerPrecision.Fine] while a mouse or a trackpad is connected, when the app
 * runs on a Mac, or once a precise pointer (such as Apple Pencil) has been observed. Apple Pencil
 * can't be detected until it's used, and its detachment can't be detected at all, so an observed
 * precise pointer is remembered for the lifetime of the listener.
 */
@ExperimentalMediaQueryApi
internal class PointerPrecisionListener(
    /**
     * [NSNotificationCenter] to listen to, can be customized for tests purposes
     */
    private val notificationCenter: NSNotificationCenter = NSNotificationCenter.defaultCenter,
    private val hasConnectedMouse: () -> Boolean = { GCMouse.mice().isNotEmpty() },
    private val isAppOnMac: Boolean = NSProcessInfo.processInfo.isiOSAppOnMac(),
) : NSObject() {
    private var isPrecisePointerObserved = false

    private val _mediaScope = SkikoMediaScope(resolvePointerPrecision())

    val mediaScope: UiMediaScope get() = _mediaScope

    init {
        notificationCenter.addObserver(
            observer = this,
            selector = NSSelectorFromString(::mouseDidChange.name + ":"),
            name = GCMouseDidConnectNotification,
            `object` = null
        )

        notificationCenter.addObserver(
            observer = this,
            selector = NSSelectorFromString(::mouseDidChange.name + ":"),
            name = GCMouseDidDisconnectNotification,
            `object` = null
        )
    }

    /**
     * GameController posts mouse notifications on the main queue. Hop there just in case, so the
     * state is always updated on the UI thread.
     */
    @OptIn(BetaInteropApi::class)
    @ObjCAction
    fun mouseDidChange(notification: NSNotification) {
        if (NSThread.isMainThread) {
            update()
        } else {
            dispatch_async(dispatch_get_main_queue()) { update() }
        }
    }

    /**
     * Must be called when an event from a precise pointing device, such as Apple Pencil or
     * a trackpad, is received.
     */
    fun onPrecisePointerObserved() {
        if (isPrecisePointerObserved) return
        isPrecisePointerObserved = true
        update()
    }

    private fun resolvePointerPrecision(): PointerPrecision =
        if (isAppOnMac || isPrecisePointerObserved || hasConnectedMouse()) {
            PointerPrecision.Fine
        } else {
            PointerPrecision.Coarse
        }

    private fun update() {
        _mediaScope.pointerPrecision = resolvePointerPrecision()
    }

    /**
     * Deregister from [NSNotificationCenter]
     */
    fun dispose() {
        notificationCenter.removeObserver(observer = this, name = GCMouseDidConnectNotification, `object` = null)
        notificationCenter.removeObserver(observer = this, name = GCMouseDidDisconnectNotification, `object` = null)
    }
}

/**
 * [PointerPrecisionListener] shared by all Compose scenes, so that the root and its layers report
 * the same value. Pointing devices are tracked per device, so it intentionally lives as long as
 * the process and is never disposed.
 */
@ExperimentalMediaQueryApi
internal val SharedPointerPrecisionListener: PointerPrecisionListener by lazy {
    PointerPrecisionListener()
}

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

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalMediaQueryApi
import androidx.compose.ui.UiMediaScope
import androidx.compose.ui.UiMediaScope.KeyboardKind
import androidx.compose.ui.UiMediaScope.PointerPrecision
import androidx.compose.ui.UiMediaScope.Posture
import androidx.compose.ui.UiMediaScope.ViewingDistance
import androidx.compose.ui.unit.Dp

/**
 * [UiMediaScope] shared by skiko platforms.
 *
 * Only [pointerPrecision] is backed by snapshot state and can be updated by the platform.
 * The remaining properties report neutral values.
 */
@Stable
@ExperimentalMediaQueryApi
internal class SkikoMediaScope(
    pointerPrecision: PointerPrecision = PointerPrecision.None,
) : UiMediaScope {
    override var pointerPrecision: PointerPrecision by mutableStateOf(pointerPrecision)

    override val windowPosture: Posture
        get() = Posture.Flat
    override val windowWidth: Dp
        get() = Dp.Unspecified
    override val windowHeight: Dp
        get() = Dp.Unspecified
    override val keyboardKind: KeyboardKind
        get() = KeyboardKind.None
    override val hasMicrophone: Boolean
        get() = false
    override val hasCamera: Boolean
        get() = false
    override val viewingDistance: ViewingDistance
        get() = ViewingDistance.Near
}

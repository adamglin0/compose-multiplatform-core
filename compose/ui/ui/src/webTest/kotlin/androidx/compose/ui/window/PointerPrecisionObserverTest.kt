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
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.js
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalMediaQueryApi::class)
class PointerPrecisionObserverTest {

    @BeforeTest
    fun setup() {
        installPointerMediaMock()
    }

    @AfterTest
    fun cleanup() {
        uninstallPointerMediaMock()
    }

    @Test
    fun finePointerIsPreferred() {
        setPointerMedia(fine = true, coarse = true)
        val observer = PointerPrecisionObserver()
        assertEquals(PointerPrecision.Fine, observer.mediaScope.pointerPrecision)
        observer.dispose()
    }

    @Test
    fun coarsePointerOnly() {
        setPointerMedia(fine = false, coarse = true)
        val observer = PointerPrecisionObserver()
        assertEquals(PointerPrecision.Coarse, observer.mediaScope.pointerPrecision)
        observer.dispose()
    }

    @Test
    fun noPointer() {
        setPointerMedia(fine = false, coarse = false)
        val observer = PointerPrecisionObserver()
        assertEquals(PointerPrecision.None, observer.mediaScope.pointerPrecision)
        observer.dispose()
    }

    @Test
    fun updatesWhenPointerDevicesChange() {
        setPointerMedia(fine = false, coarse = true)
        val observer = PointerPrecisionObserver()
        assertEquals(PointerPrecision.Coarse, observer.mediaScope.pointerPrecision)

        setPointerMedia(fine = true, coarse = true)
        assertEquals(PointerPrecision.Fine, observer.mediaScope.pointerPrecision)

        setPointerMedia(fine = false, coarse = false)
        assertEquals(PointerPrecision.None, observer.mediaScope.pointerPrecision)

        observer.dispose()
    }

    @Test
    fun stopsUpdatingAfterDispose() {
        setPointerMedia(fine = false, coarse = true)
        val observer = PointerPrecisionObserver()
        observer.dispose()

        setPointerMedia(fine = true, coarse = true)
        assertEquals(PointerPrecision.Coarse, observer.mediaScope.pointerPrecision)
    }
}

/**
 * Replaces `window.matchMedia` for `any-pointer` queries with [MediaQueryList]s whose `matches`
 * is controlled by [setPointerMedia]. Other queries are delegated to the original.
 */
@OptIn(ExperimentalWasmJsInterop::class)
private fun installPointerMediaMock(): Unit = js(
    """(function() {
        var originalMatchMedia = window.matchMedia;
        var mock = { fine: false, coarse: false, lists: [], original: originalMatchMedia };
        window._pointerMediaMock = mock;
        window.matchMedia = function(query) {
            var key = null;
            if (query === '(any-pointer: fine)') key = 'fine';
            if (query === '(any-pointer: coarse)') key = 'coarse';
            if (key === null) return originalMatchMedia.call(window, query);
            // Wrap a real MediaQueryList: Kotlin/Wasm checks the type of the returned object.
            var list = originalMatchMedia.call(window, query);
            Object.defineProperty(list, 'matches', { get: function() { return mock[key]; } });
            mock.lists.push(list);
            return list;
        };
    })()"""
)

@OptIn(ExperimentalWasmJsInterop::class)
private fun setPointerMedia(fine: Boolean, coarse: Boolean): Unit = js(
    """(function() {
        var mock = window._pointerMediaMock;
        mock.fine = fine;
        mock.coarse = coarse;
        mock.lists.forEach(function(list) {
            list.dispatchEvent(new MediaQueryListEvent('change', { matches: list.matches, media: list.media }));
        });
    })()"""
)

@OptIn(ExperimentalWasmJsInterop::class)
private fun uninstallPointerMediaMock(): Unit = js(
    """(function() {
        var mock = window._pointerMediaMock;
        if (mock) {
            window.matchMedia = mock.original;
            delete window._pointerMediaMock;
        }
    })()"""
)

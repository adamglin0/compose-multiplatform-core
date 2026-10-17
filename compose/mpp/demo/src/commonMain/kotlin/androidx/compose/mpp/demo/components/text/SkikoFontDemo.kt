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

@file:OptIn(ExperimentalTextApi::class)

package androidx.compose.mpp.demo.components.text

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Checkbox
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Slider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.SkikoFont
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Data
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.Typeface

private const val DemoFontFile = "RobotoFlex-VariableFont.ttf"

private val DemoWeights = listOf(FontWeight.Light, FontWeight.Normal, FontWeight.Bold)

private const val SampleText =
    "Compose Multiplatform is a declarative framework for sharing UI code across " +
        "multiple platforms with Kotlin."

/**
 * Shows [FontLoadingStrategy.Async] [SkikoFont]s: text is drawn with the fallback font first and
 * reflows once the simulated load completes. All weights come from one variable font file, so
 * they share one [SkikoFont.identity] and the file is loaded only once.
 */
@Composable
fun SkikoFontDemo() {
    var loadDelayMillis by remember { mutableStateOf(2_000f) }
    var failLoad by remember { mutableStateOf(false) }
    // Loaded typefaces stay cached, so each run uses a new identity to load the font again.
    var run by remember { mutableIntStateOf(0) }
    val fontFamily = remember(run) {
        val typefaceLoader =
            DelayedResourceTypefaceLoader(loadDelayMillis.roundToLong(), failLoad)
        FontFamily(
            DemoWeights.map { weight ->
                DemoFont(
                    identity = "$DemoFontFile#$run",
                    weight = weight,
                    style = FontStyle.Normal,
                    typefaceLoader = typefaceLoader,
                )
            }
        )
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Load delay: ${loadDelayMillis.roundToLong()} ms (applies to the next load)")
        Slider(
            value = loadDelayMillis,
            onValueChange = { loadDelayMillis = it },
            valueRange = 0f..10_000f,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = failLoad, onCheckedChange = { failLoad = it })
            Text("Fail the next load (text keeps the fallback font)")
        }
        Button(onClick = { run++ }) {
            Text("Load again")
        }
        Divider()
        Spacer(Modifier.height(8.dp))
        for (weight in DemoWeights) {
            Text("Weight ${weight.weight}", style = MaterialTheme.typography.caption)
            Text(
                SampleText,
                style = TextStyle(fontFamily = fontFamily, fontWeight = weight, fontSize = 24.sp),
            )
        }
    }
}

/** Loads the bundled font file after [delayMillis], imitating a slow network download. */
private class DelayedResourceTypefaceLoader(
    private val delayMillis: Long,
    private val fail: Boolean,
) : SkikoFont.TypefaceLoader {

    override fun loadBlocking(font: SkikoFont): Typeface? = null

    override suspend fun awaitLoad(font: SkikoFont): Typeface? {
        delay(delayMillis)
        if (fail) return null
        val bytes = loadResource(DemoFontFile) ?: return null
        return withContext(Dispatchers.Default) {
            val data = Data.makeFromBytes(bytes)
            try {
                FontMgr.default.makeFromData(data)
            } finally {
                data.close()
            }
        }
    }
}

private class DemoFont(
    override val identity: String,
    override val weight: FontWeight,
    override val style: FontStyle,
    typefaceLoader: SkikoFont.TypefaceLoader,
) : SkikoFont(
    FontLoadingStrategy.Async,
    typefaceLoader,
    FontVariation.Settings(weight, style),
)

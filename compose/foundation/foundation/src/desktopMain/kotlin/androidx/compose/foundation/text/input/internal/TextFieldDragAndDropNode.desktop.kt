/*
 * Copyright 2025 The Android Open Source Project
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

@file:OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)

package androidx.compose.foundation.text.input.internal

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.content.MediaType
import androidx.compose.foundation.content.hasMediaType
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTargetModifierNode
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.ClipMetadata
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DropTargetDragEvent
import java.awt.dnd.DropTargetDropEvent

internal actual fun textFieldDragAndDropNode(
    hintMediaTypes: () -> Set<MediaType>,
    onDrop: (clipEntry: ClipEntry, clipMetadata: ClipMetadata) -> Boolean,
    dragAndDropRequestPermission: (DragAndDropEvent) -> Unit,
    onStarted: ((event: DragAndDropEvent) -> Unit)?,
    onEntered: ((event: DragAndDropEvent) -> Unit)?,
    onMoved: ((position: Offset) -> Unit)?,
    onChanged: ((event: DragAndDropEvent) -> Unit)?,
    onExited: ((event: DragAndDropEvent) -> Unit)?,
    onEnded: ((event: DragAndDropEvent) -> Unit)?,
): DragAndDropTargetModifierNode {
    lateinit var node: DragAndDropTargetModifierNode
    node =
        DragAndDropTargetModifierNode(
            shouldStartDragAndDrop = { dragAndDropEvent ->
                // If there's a contentReceiver modifier wrapping around this TextField, initially
                // all dragging items should be accepted for drop. This is expected to be met by the
                // caller of [textFieldDragAndDropNode] function.
                val dataFlavors = dragAndDropEvent.dataFlavors
                hintMediaTypes().any {
                    it == MediaType.All || dataFlavors.hasMediaType(it)
                }
            },
            target =
                object : DragAndDropTarget {
                    override fun onDrop(event: DragAndDropEvent): Boolean {
                        dragAndDropRequestPermission(event)
                        val clipEntry = ClipEntry(event.awtTransferable)
                        return onDrop.invoke(clipEntry, clipEntry.clipMetadata)
                    }

                    override fun onStarted(event: DragAndDropEvent) =
                        onStarted?.invoke(event) ?: Unit

                    override fun onEntered(event: DragAndDropEvent) =
                        onEntered?.invoke(event) ?: Unit

                    override fun onMoved(event: DragAndDropEvent) {
                        val position = event.positionInRootOrNull(node) ?: return
                        onMoved?.invoke(position)
                    }

                    override fun onExited(event: DragAndDropEvent) = onExited?.invoke(event) ?: Unit

                    override fun onChanged(event: DragAndDropEvent) =
                        onChanged?.invoke(event) ?: Unit

                    override fun onEnded(event: DragAndDropEvent) = onEnded?.invoke(event) ?: Unit
                },
        )
    return node
}

/**
 * The [DataFlavor]s the dragged content is currently available in. Unlike reading the transferable
 * itself, this can be queried at any point of a drag session.
 */
private val DragAndDropEvent.dataFlavors: List<DataFlavor>
    get() =
        when (val nativeEvent = nativeEvent) {
            is DropTargetDragEvent -> nativeEvent.currentDataFlavors.toList()
            is DropTargetDropEvent -> nativeEvent.currentDataFlavors.toList()
            else -> emptyList()
        }

/**
 * The position of the pointer in the coordinates of the Compose root, in pixels, or null if it
 * cannot be determined.
 *
 * AWT reports the position in its own scale-independent units, relative to the component that holds
 * the drop target, which is the Compose root container. [dropTargetNode] supplies the density to
 * convert them to pixels.
 */
private fun DragAndDropEvent.positionInRootOrNull(
    dropTargetNode: DragAndDropTargetModifierNode
): Offset? {
    if (!dropTargetNode.node.isAttached) return null
    val location =
        when (val nativeEvent = nativeEvent) {
            is DropTargetDragEvent -> nativeEvent.location
            is DropTargetDropEvent -> nativeEvent.location
            else -> return null
        }
    val density = dropTargetNode.requireDensity().density
    return Offset(location.x * density, location.y * density)
}

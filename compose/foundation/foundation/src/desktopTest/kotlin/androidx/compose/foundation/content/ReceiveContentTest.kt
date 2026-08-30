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

@file:OptIn(
    ExperimentalComposeUiApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalTestApi::class,
    InternalComposeUiApi::class,
)

package androidx.compose.foundation.content

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.FakeClipboard
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTransferAction
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import java.awt.Point
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetContext
import java.awt.dnd.DropTargetDragEvent
import java.awt.dnd.DropTargetDropEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReceiveContentTest {

    @Test
    fun droppedContentIsDeliveredToReceiver() = runDesktopComposeUiTest {
        var received: TransferableContent? = null
        setContent {
            Box(Modifier.size(100.dp).contentReceiver {
                received = it
                null
            })
        }

        val session = DragAndDropSession(this, StringSelection("Hello"))
        session.enter(Offset(50f, 50f))
        assertTrue(session.drop(Offset(50f, 50f)))

        val content = requireNotNull(received)
        assertEquals(TransferableContent.Source.DragAndDrop, content.source)
        assertEquals("Hello", content.clipEntry.readPlainText())
        assertTrue(content.hasMediaType(MediaType.Text))
        assertFalse(content.hasMediaType(MediaType.Image))
    }

    @Test
    fun unconsumedContentIsReportedAsNotHandled() = runDesktopComposeUiTest {
        setContent { Box(Modifier.size(100.dp).contentReceiver { it }) }

        val session = DragAndDropSession(this, StringSelection("Hello"))
        session.enter(Offset(50f, 50f))
        assertFalse(session.drop(Offset(50f, 50f)))
    }

    @Test
    fun contentNotConsumedByChildIsPassedToParent() = runDesktopComposeUiTest {
        var parentReceived: TransferableContent? = null
        var childReceived: TransferableContent? = null
        setContent {
            Box(
                Modifier.size(200.dp).contentReceiver {
                    parentReceived = it
                    null
                }
            ) {
                Box(
                    Modifier.size(100.dp).align(Alignment.BottomEnd).contentReceiver {
                        childReceived = it
                        it
                    }
                )
            }
        }

        val session = DragAndDropSession(this, StringSelection("Hello"))
        session.enter(Offset(150f, 150f))
        assertTrue(session.drop(Offset(150f, 150f)))

        assertEquals("Hello", childReceived?.clipEntry?.readPlainText())
        assertEquals(childReceived, parentReceived)
    }

    @Test
    fun contentConsumedByChildIsNotPassedToParent() = runDesktopComposeUiTest {
        var parentReceived: TransferableContent? = null
        setContent {
            Box(Modifier.size(200.dp).contentReceiver { parentReceived = it; null }) {
                Box(Modifier.size(100.dp).align(Alignment.BottomEnd).contentReceiver { null })
            }
        }

        val session = DragAndDropSession(this, StringSelection("Hello"))
        session.enter(Offset(150f, 150f))
        assertTrue(session.drop(Offset(150f, 150f)))

        assertNull(parentReceived)
    }

    /**
     * Moving the dragged item from a [contentReceiver] to a nested one must not report an exit from
     * the outer receiver, since the outer one still receives whatever the inner one leaves over.
     */
    @Test
    fun nestedReceiversShareTheirDragArea() = runDesktopComposeUiTest {
        val events = mutableListOf<String>()
        setContent {
            Box(Modifier.size(200.dp).contentReceiver(TrackingListener("outer", events))) {
                Box(
                    Modifier.size(100.dp)
                        .align(Alignment.BottomEnd)
                        .contentReceiver(TrackingListener("inner", events))
                )
            }
        }

        val session = DragAndDropSession(this, StringSelection("Hello"))
        session.enter(Offset(50f, 50f))
        session.move(Offset(150f, 150f))
        session.move(Offset(50f, 50f))
        session.exit()

        // Both receivers learn about the session as a whole, in an unspecified order.
        assertEquals(setOf("outer start", "inner start"), events.take(2).toSet())
        assertEquals(
            listOf("outer enter", "inner enter", "inner exit", "outer exit"),
            events.subList(2, events.size - 2),
        )
        assertEquals(setOf("outer end", "inner end"), events.takeLast(2).toSet())
    }

    @Test
    fun pastedContentIsDeliveredToReceiver() = runDesktopComposeUiTest {
        var received: TransferableContent? = null
        val state = TextFieldState()
        setContent {
            CompositionLocalProvider(
                LocalClipboard provides FakeClipboard(ClipEntry(StringSelection("Hello")))
            ) {
                Box(Modifier.contentReceiver { received = it; null }) {
                    BasicTextField(state = state, modifier = Modifier.testTag(TextFieldTag))
                }
            }
        }

        onNodeWithTag(TextFieldTag).performSemanticsAction(SemanticsActions.PasteText)
        waitForIdle()

        val content = requireNotNull(received)
        assertEquals(TransferableContent.Source.Clipboard, content.source)
        assertEquals("Hello", content.clipEntry.readPlainText())
        // The receiver consumed everything, so nothing is left for the text field to insert.
        assertEquals("", state.text.toString())
    }

    @Test
    fun pastedContentNotConsumedByReceiverIsInsertedInTextField() = runDesktopComposeUiTest {
        val state = TextFieldState()
        setContent {
            CompositionLocalProvider(
                LocalClipboard provides FakeClipboard(ClipEntry(StringSelection("Hello")))
            ) {
                Box(Modifier.contentReceiver { it }) {
                    BasicTextField(state = state, modifier = Modifier.testTag(TextFieldTag))
                }
            }
        }

        onNodeWithTag(TextFieldTag).performSemanticsAction(SemanticsActions.PasteText)
        waitForIdle()

        assertEquals("Hello", state.text.toString())
    }

    @Test
    fun droppedTextIsInsertedInTextField() = runDesktopComposeUiTest {
        val state = TextFieldState()
        setContent {
            BasicTextField(
                state = state,
                modifier = Modifier.size(200.dp, 50.dp).testTag(TextFieldTag),
            )
        }

        val session = DragAndDropSession(this, StringSelection("Hello"))
        session.enter(Offset(10f, 10f))
        assertTrue(session.drop(Offset(10f, 10f)))
        waitForIdle()

        assertEquals("Hello", state.text.toString())
    }

    private companion object {
        const val TextFieldTag = "textField"
    }
}

private class TrackingListener(
    private val name: String,
    private val events: MutableList<String>,
) : ReceiveContentListener {
    override fun onDragStart() {
        events += "$name start"
    }

    override fun onDragEnd() {
        events += "$name end"
    }

    override fun onDragEnter() {
        events += "$name enter"
    }

    override fun onDragExit() {
        events += "$name exit"
    }

    override fun onReceive(transferableContent: TransferableContent): TransferableContent? {
        events += "$name receive"
        return null
    }
}

/**
 * Drives a drag and drop session over a [DesktopComposeUiTest] the same way
 * `AwtDragAndDropManager` drives it from AWT drop target events.
 */
private class DragAndDropSession(
    private val test: DesktopComposeUiTest,
    private val transferable: Transferable,
) {
    private val dropTargetContext: DropTargetContext = DropTarget().dropTargetContext

    private val rootNode
        get() = test.scene.rootDragAndDropNode

    /** Starts the session and moves the dragged item to [position]. */
    fun enter(position: Offset) = test.runOnIdle {
        val event = dragEvent(position)
        assertTrue(rootNode.acceptDragAndDropTransfer(event), "the drag was not accepted")
        rootNode.onStarted(event)
        rootNode.onEntered(event)
        rootNode.onMoved(event)
    }

    fun move(position: Offset) = test.runOnIdle { rootNode.onMoved(dragEvent(position)) }

    fun drop(position: Offset): Boolean = test.runOnIdle {
        val event = dropEvent(position)
        val accepted = rootNode.onDrop(event)
        rootNode.onEnded(event)
        accepted
    }

    /** Ends the session by dragging the item out of the window. */
    fun exit() = test.runOnIdle {
        val event = dragEvent(Offset.Zero)
        rootNode.onExited(event)
        rootNode.onEnded(event)
    }

    private fun dragEvent(position: Offset) =
        DragAndDropEvent(
            DragAndDropTransferAction.Copy,
            TestDragEvent(dropTargetContext, position.toAwtPoint(), transferable),
            position,
        )

    private fun dropEvent(position: Offset) =
        DragAndDropEvent(
            DragAndDropTransferAction.Copy,
            TestDropEvent(dropTargetContext, position.toAwtPoint(), transferable),
            position,
        )

    private fun Offset.toAwtPoint() = Point(x.toInt(), y.toInt())
}

private class TestDragEvent(
    context: DropTargetContext,
    location: Point,
    private val transferable: Transferable,
) :
    DropTargetDragEvent(
        context,
        location,
        DnDConstants.ACTION_COPY,
        DnDConstants.ACTION_COPY,
    ) {
    override fun getTransferable(): Transferable = transferable

    override fun getCurrentDataFlavors(): Array<DataFlavor> = transferable.transferDataFlavors
}

private class TestDropEvent(
    context: DropTargetContext,
    location: Point,
    private val transferable: Transferable,
) :
    DropTargetDropEvent(
        context,
        location,
        DnDConstants.ACTION_COPY,
        DnDConstants.ACTION_COPY,
    ) {
    override fun getTransferable(): Transferable = transferable

    override fun getCurrentDataFlavors(): Array<DataFlavor> = transferable.transferDataFlavors
}

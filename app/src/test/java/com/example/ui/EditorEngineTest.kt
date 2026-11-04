package com.example.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.example.ui.editor.EditHistory
import com.example.ui.editor.MarkdownEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class EditorEngineTest {
    @Test fun toggleBold_wrapsAndUnwraps() {
        val v = TextFieldValue("مرحبا بك", TextRange(0, 5))
        val b = MarkdownEngine.toggleInline(v, "**")
        assertEquals("**مرحبا** بك", b.text)
        assertEquals("مرحبا بك", MarkdownEngine.toggleInline(b, "**").text)
    }

    @Test fun linePrefix_numberedAndToggleOff() {
        val v = TextFieldValue("أ\nب", TextRange(0, 3))
        val n = MarkdownEngine.toggleLinePrefix(v, "1. ")
        assertEquals("1. أ\n2. ب", n.text)
        assertEquals("أ\nب", MarkdownEngine.toggleLinePrefix(n.copy(selection = TextRange(0, n.text.length)), "1. ").text)
    }

    @Test fun smartEnter_continuesAndEndsLists() {
        val old = TextFieldValue("- أول", TextRange(5))
        val typed = TextFieldValue("- أول\n", TextRange(6))
        val cont = MarkdownEngine.continueList(old, typed)!!
        assertEquals("- أول\n- ", cont.text)
        val end = MarkdownEngine.continueList(cont, TextFieldValue(cont.text + "\n", TextRange(cont.text.length + 1)))!!
        assertEquals("- أول\n", end.text)
        assertEquals("2. ", MarkdownEngine.continueList(TextFieldValue("1. a"), TextFieldValue("1. a\n", TextRange(5)))!!.text.substringAfter("\n"))
        assertNull(MarkdownEngine.continueList(TextFieldValue("x"), TextFieldValue("x\n", TextRange(2))))
    }

    @Test fun plainPreview_stripsMarkers() {
        assertEquals("عنوان\nنص عريض ورابط", MarkdownEngine.plain("## عنوان\nنص **عريض** و[رابط](https://a.b)"))
    }

    @Test fun history_undoRedo_coalescesTyping() {
        val h = EditHistory("")
        h.record("a", now = 1000); h.record("ab", now = 1100); h.record("abc", now = 5000)
        assertEquals("ab", h.undo()); assertEquals("", h.undo()); assertNull(h.undo())
        assertEquals("ab", h.redo()); assertEquals("abc", h.redo()); assertFalse(h.canRedo)
    }
}

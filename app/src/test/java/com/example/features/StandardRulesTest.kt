package com.example.features

import com.example.core.text.Digits
import com.example.ui.i18n.Strings
import com.example.ui.theme.Concept
import com.example.ui.theme.contrastRatio
import com.example.ui.theme.tabletScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StandardRulesTest {
    private val ar = Strings(true)
    private val en = Strings(false)

    @Test fun numbers_latinDigits_isolatedInArabic() {
        assertEquals("⁨1,234⁩", ar.num(1234))
        assertEquals("7", en.num(7))
        assertTrue(ar.date(0L).none { it.code in 0x0660..0x0669 })
    }

    @Test fun arabicCountForms() {
        assertEquals("ملاحظة واحدة", ar.notesCount(1))
        assertEquals("ملاحظتان", ar.notesCount(2))
        assertEquals("⁨3⁩ ملاحظات", ar.notesCount(3))
        assertEquals("⁨10⁩ ملاحظات", ar.notesCount(10))
        assertEquals("⁨11⁩ ملاحظة", ar.notesCount(11))
        assertEquals("⁨103⁩ ملاحظات", ar.notesCount(103))
        assertEquals("1 note", en.notesCount(1)); assertEquals("5 tasks", en.tasksCount(5))
    }

    @Test fun numericInput_acceptsArabicAndPersianDigits() {
        assertEquals("1234", Digits.latin("١٢٣٤"))
        assertEquals("5678", Digits.latin("۵۶۷۸"))
        assertEquals("192.168.1.5:40123", Digits.latin("١٩٢٫١٦٨٫١٫٥:٤٠١٢٣"))
        // Typing character by character keeps each partial value (no erasing "4.").
        var v = ""
        for (ch in "٤٫٥") v = Digits.latin(v + ch)
        assertEquals("4.5", v)
        assertEquals("1234", Digits.pin("١٢٣٤"))
        assertNull(Digits.pin("12a")); assertNull(Digits.pin("123456789"))
    }

    @Test fun tabletPortraitScalesToPhoneCanvas() {
        assertEquals(1f, tabletScale(390, 844))       // phone
        assertEquals(1f, tabletScale(1280, 800))      // tablet landscape: real dp, two columns
        assertEquals(800f / 560f, tabletScale(800, 1280), 0.001f)
        assertEquals(1.6f, tabletScale(1200, 1900))   // clamped
    }

    @Test fun conceptTextColors_haveAAContrast() {
        for (c in Concept.entries) {
            assertTrue("${c.name} t1", contrastRatio(c.t1, c.on) >= 4.5)
            assertTrue("${c.name} t2", contrastRatio(c.t2, c.on) >= 4.5)
        }
    }
}

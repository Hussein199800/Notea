package com.example.features

import com.example.ocr.OcrFilter
import com.example.ocr.OcrFilter.Word
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrFilterTest {
    @Test fun rejectsNoiseLikeTheUsersDocument() {
        // Shape of the bad result from the device: mixed scripts, symbols, low confidence.
        val noise = listOf(
            listOf(Word("“Wa", 31f), Word("212111151101101", 40f), Word("50l", 35f), Word("Wor", 28f)),
            listOf(Word("OPكسم", 45f), Word("(rmagere", 30f), Word("#", 50f)),
            listOf(Word("FALALA", 52f)), listOf(Word("Findيول", 41f), Word("SAD", 55f))
        )
        val r = OcrFilter.filter(noise)
        assertFalse(r.accepted); assertEquals("", r.text)
    }

    @Test fun keepsConfidentArabicLines_dropsNoisyWords() {
        val lines = listOf(
            listOf(Word("الجمهورية", 91f), Word("العربية", 93f), Word("السورية", 90f)),
            listOf(Word("نقابة", 88f), Word("المحامين", 92f), Word("x#$", 20f)),
            listOf(Word("Wa?", 25f), Word("##", 15f), Word("ok", 30f)), // noisy line: dropped
            listOf(Word("تاريخ:", 85f), Word("2026/11/05", 80f))
        )
        val r = OcrFilter.filter(lines)
        assertTrue(r.accepted)
        assertEquals("الجمهورية العربية السورية\nنقابة المحامين\nتاريخ: 2026/11/05", r.text)
    }

    @Test fun noiseRules() {
        assertTrue(OcrFilter.looksLikeNoise("OPكسم"))
        assertTrue(OcrFilter.looksLikeNoise("#$%"))
        assertFalse(OcrFilter.looksLikeNoise("المحامين"))
        assertFalse(OcrFilter.looksLikeNoise("2026/11/05"))
        assertFalse(OcrFilter.looksLikeNoise("Facebook"))
    }
}

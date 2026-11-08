package com.example.features

import com.example.BuildConfig
import com.example.core.Changelog
import com.example.core.update.UpdateRules
import com.example.core.update.UpdateRules.Asset
import com.example.core.update.UpdateRules.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UpdateAndVersionTest {
    private fun root(): File = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }

    @Test fun version_matchesInAllThreePlaces() {
        val gradle = File(root(), "app/build.gradle.kts").readText()
        val name = Regex("versionName = \"([^\"]+)\"").find(gradle)!!.groupValues[1]
        val notes = File(root(), "release-notes.md").readLines().first { it.isNotBlank() }
        assertEquals(name, Changelog.current.version)
        assertEquals("# Notea $name", notes.trim())
        assertEquals(name, BuildConfig.VERSION_NAME.removeSuffix("-debug"))
        assertEquals(UpdateRules.versionCode(name, null), BuildConfig.VERSION_CODE)
    }

    @Test fun changelog_newestFirst() {
        val vs = Changelog.releases.map { it.version }
        for (k in 0 until vs.size - 1) assertTrue("${vs[k]} > ${vs[k + 1]}", UpdateRules.isNewer(vs[k], vs[k + 1]))
        Changelog.releases.forEach { assertEquals(it.ar.size, it.en.size) }
    }

    @Test fun versionComparison() {
        assertTrue(UpdateRules.isNewer("v1.2.0", "1.1.3"))
        assertTrue(UpdateRules.isNewer("1.10.0", "1.9.9"))
        assertTrue(UpdateRules.isNewer("2.0", "1.9.9"))
        assertFalse(UpdateRules.isNewer("1.2.0", "1.2.0"))
        assertFalse(UpdateRules.isNewer("1.1.9", "1.2.0"))
        assertFalse(UpdateRules.isNewer("latest", "1.2.0"))
        assertFalse(UpdateRules.isNewer("v1.2.0", "1.2.0-debug"))
    }

    @Test fun versionCodeScheme() {
        assertEquals(102000, UpdateRules.versionCode("1.2.0", null))
        assertEquals(102001, UpdateRules.versionCode("1.2.0", "armeabi-v7a"))
        assertEquals(102002, UpdateRules.versionCode("1.2.0", "arm64-v8a"))
        assertTrue(UpdateRules.versionCode("1.2.1", null) > UpdateRules.versionCode("1.2.0", "arm64-v8a"))
        assertTrue(UpdateRules.versionCode("1.2.0", null) > 5) // upgrades over 1.1.3 (versionCode 5)
    }

    @Test fun assetChoice_prefersDeviceAbi_thenUniversal() {
        val assets = listOf(Asset("Notea.apk", "u", 1), Asset("Notea-arm64-v8a.apk", "a64", 1), Asset("Notea-armeabi-v7a.apk", "a32", 1), Asset("notes.txt", "x", 1))
        assertEquals("a64", UpdateRules.pickAsset(assets, listOf("arm64-v8a", "armeabi-v7a"))!!.url)
        assertEquals("a32", UpdateRules.pickAsset(assets, listOf("armeabi-v7a"))!!.url)
        assertEquals("u", UpdateRules.pickAsset(assets, listOf("x86_64"))!!.url)
        assertNull(UpdateRules.pickAsset(listOf(Asset("readme.md", "x", 1)), listOf("arm64-v8a")))
    }

    @Test fun verify_packageNewerSignature() {
        val sig = setOf("4c2817b2dd6d3abe919b9534d1c3249e3c5e306391000c9eec886f7620adc597")
        assertEquals(Verdict.Ok, UpdateRules.verify("com.notea.app", 102002, sig, 5, sig))
        assertEquals(Verdict.Ok, UpdateRules.verify("com.notea.app", 102002, sig.map { it.uppercase() }.toSet(), 5, sig))
        assertEquals(Verdict.WrongPackage, UpdateRules.verify("com.evil.app", 102002, sig, 5, sig))
        assertEquals(Verdict.NotNewer, UpdateRules.verify("com.notea.app", 5, sig, 5, sig))
        assertEquals(Verdict.SignatureMismatch, UpdateRules.verify("com.notea.app", 102002, setOf("00"), 5, sig))
        assertEquals(Verdict.SignatureMismatch, UpdateRules.verify("com.notea.app", 102002, emptySet(), 5, sig))
        assertEquals(Verdict.Unreadable, UpdateRules.verify(null, 0, emptySet(), 5, sig))
    }

    @Test fun diagnostics_masksSensitiveData() {
        val m = com.example.core.Diagnostics.mask("user a.b@gmail.com key=AIzaSyA1234567890abcdefghijk phone 0991234567 path /data/user/0/com.notea.app/files/x content://media/1 pin: 1234")
        assertFalse(m.contains("gmail")); assertFalse(m.contains("AIzaSyA1")); assertFalse(m.contains("0991234567"))
        assertFalse(m.contains("/data/user")); assertFalse(m.contains("media/1")); assertFalse(m.contains("1234"))
    }
}

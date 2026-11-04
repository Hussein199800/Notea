package com.example.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.data.local.ChecklistItemEntity
import com.example.data.local.NoteEntity
import com.example.data.local.NoteWithDetails
import com.example.data.local.ReminderEntity
import com.example.data.local.TagEntity
import com.example.ui.components.NoteCard
import com.example.ui.components.PreferenceGroup
import com.example.ui.components.PreferenceRow
import com.example.ui.components.SectionLabel
import com.example.ui.components.SwitchRow
import com.example.ui.i18n.LocalStrings
import com.example.ui.i18n.Strings
import com.example.ui.screens.AuthScreen
import com.example.ui.theme.NoteaTheme
import com.example.ui.viewmodel.NoteCardModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders key screens to PNG (set NOTEA_SHOTS=<dir>) — a smoke test that composition works in AR/EN, light/dark. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val rule = androidx.compose.ui.test.junit4.createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private fun card(id: String, title: String, body: String, color: Int, pinned: Boolean = false, locked: Boolean = false,
                     checklist: List<Pair<String, Boolean>> = emptyList(), tags: List<String> = emptyList(), reminder: Long? = null) =
        NoteCardModel(
            NoteWithDetails(
                NoteEntity(id = id, userId = "u", title = title, body = body, colorIndex = color, isPinned = pinned, isLocked = locked, createdAt = 0, updatedAt = 0),
                checklist.mapIndexed { i, (t, c) -> ChecklistItemEntity("$id$i", id, t, c, i) },
                tags.map { TagEntity(it, "u", it, it) }, emptyList(),
                listOfNotNull(reminder?.let { ReminderEntity("r$id", id, "u", it, 0, 0) })
            ),
            if (locked) "" else body, null, emptyMap()
        )

    @Composable
    private fun Frame(arabic: Boolean, dark: Boolean, content: @Composable () -> Unit) {
        NoteaTheme(dark) {
            CompositionLocalProvider(LocalStrings provides Strings(arabic), LocalLayoutDirection provides if (arabic) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
            }
        }
    }

    private fun save(name: String) {
        val dir = System.getenv("NOTEA_SHOTS") ?: return
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(android.graphics.Canvas(bmp)) }
        File(dir).mkdirs()
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Composable
    private fun HomeMock(arabic: Boolean) {
        val s = Strings(arabic)
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(s.allNotes, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    NoteCard(card("1", if (arabic) "خطة الأسبوع" else "Weekly plan", if (arabic) "مراجعة المشروع مع الفريق، وتحضير العرض النهائي." else "Review the project with the team and prepare the final demo.", 2, pinned = true, tags = listOf(if (arabic) "عمل" else "work")), false, onClick = {}, onLongClick = {})
                    NoteCard(card("2", if (arabic) "قائمة التسوق" else "Groceries", "", 1, checklist = listOf((if (arabic) "خبز" else "Bread") to true, (if (arabic) "حليب" else "Milk") to false, (if (arabic) "تمر" else "Dates") to false)), false, onClick = {}, onLongClick = {})
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    NoteCard(card("3", if (arabic) "حساب البنك" else "Bank", "", 0, locked = true), false, onClick = {}, onLongClick = {})
                    NoteCard(card("4", if (arabic) "أفكار" else "Ideas", if (arabic) "تطبيق ملاحظات عربي يعمل دون إنترنت ✨" else "An offline-first Arabic notes app ✨", 4, reminder = 1_800_000_000_000), false, onClick = {}, onLongClick = {})
                    NoteCard(card("5", "", if (arabic) "اقتباس: «العلم في الصغر كالنقش على الحجر»" else "Quote of the day", 5), false, onClick = {}, onLongClick = {})
                }
            }
            SectionLabel(s.security)
            PreferenceGroup {
                SwitchRow(Icons.Rounded.Lock, s.appLock, s.appLockDesc, true) {}
                PreferenceRow(Icons.Rounded.Key, s.vaultTitle, s.vaultReady)
                PreferenceRow(Icons.Rounded.Download, s.importCenter, s.importCenterDesc)
                PreferenceRow(Icons.Rounded.Backup, s.backupRestore, s.backupRestoreDesc)
            }
        }
    }

    @Test fun home_ar_light() { rule.setContent { Frame(true, false) { HomeMock(true) } }; rule.waitForIdle(); save("home_ar_light") }
    @Test fun home_ar_dark() { rule.setContent { Frame(true, true) { HomeMock(true) } }; rule.waitForIdle(); save("home_ar_dark") }
    @Test fun home_en_light() { rule.setContent { Frame(false, false) { HomeMock(false) } }; rule.waitForIdle(); save("home_en_light") }
    @Test fun auth_ar() { rule.setContent { Frame(true, false) { AuthScreen {} } }; rule.waitForIdle(); save("auth_ar") }
}

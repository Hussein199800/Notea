package com.example.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.AppContainer
import com.example.LOCAL_UID
import com.example.data.repository.ChecklistDraft
import com.example.data.repository.NoteDraft
import com.example.ui.i18n.LocalStrings
import com.example.ui.i18n.Strings
import com.example.ui.screens.TabContent
import com.example.ui.screens.TabsChrome
import com.example.ui.theme.NoteaTheme
import com.example.ui.theme.ScaledLayout
import com.example.ui.viewmodel.HomeViewModel
import com.example.ui.viewmodel.MainTab
import com.example.ui.viewmodel.SessionViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** The four tabs at the standard's sizes (320, 390, phone landscape, tablet portrait/landscape), AR/EN, light/dark. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TabsShotTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val container by lazy { AppContainer(app) }

    @org.junit.Before fun seed() {
        androidx.work.testing.WorkManagerTestInitHelper.initializeTestWorkManager(app)
        runBlocking {
            val repo = container.repository
            val name = "العمل والمشاريع الطويلة ذات الاسم الممتد"
            val work = container.db.folderDao().let { it.allIncludingDeleted(LOCAL_UID) }.firstOrNull { it.name == name && !it.deleted }
                ?: repo.createFolder(LOCAL_UID, name)
            repo.save(LOCAL_UID, NoteDraft(id = "n1", title = "خطة الأسبوع", body = "مراجعة ملف القضية رقم 245 مع نقابة المحامين", folderId = work.id, tags = listOf("عمل"),
                checklist = listOf(ChecklistDraft(text = "الاتصال بالموكل وتأكيد موعد الجلسة القادمة في المحكمة"), ChecklistDraft(text = "طباعة المذكرة", checked = true))))
            repo.save(LOCAL_UID, NoteDraft(id = "n2", title = "تسوق", body = "", tags = listOf("بيت"),
                checklist = listOf(ChecklistDraft(text = "خبز"), ChecklistDraft(text = "حليب"))))
            repo.save(LOCAL_UID, NoteDraft(id = "n3", title = "Meeting notes", body = "Q3 roadmap and budget"))
            repo.setReminder(LOCAL_UID, "n1", System.currentTimeMillis() + 86_400_000L)
        }
    }

    @Composable
    private fun Frame(arabic: Boolean, dark: Boolean, scale: Float = 1f, content: @Composable () -> Unit) {
        NoteaTheme(dark) {
            ScaledLayout(scale) {
                CompositionLocalProvider(LocalStrings provides Strings(arabic), LocalLayoutDirection provides if (arabic) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
                }
            }
        }
    }

    private fun save(name: String) {
        rule.waitForIdle()
        val dir = System.getenv("NOTEA_SHOTS") ?: return
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(android.graphics.Canvas(bmp)) }
        File(dir).mkdirs(); File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun renderAll(prefix: String, arabic: Boolean = true, dark: Boolean = false, scale: Float = 1f) {
        val session = SessionViewModel(container, LOCAL_UID)
        val home = HomeViewModel(container, LOCAL_UID)
        rule.setContent {
            Frame(arabic, dark, scale) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val rail = maxWidth >= 600.dp || maxWidth > maxHeight
                    TabsChrome(session, home, rail) { TabContent(session, home, null) { _, _ -> } }
                }
            }
        }
        for (t in MainTab.entries) {
            rule.runOnUiThread { session.selectTab(t) }
            repeat(6) { rule.mainClock.advanceTimeBy(200); Thread.sleep(150) }
            save("${prefix}_${t.name.lowercase()}")
        }
    }

    @Config(qualifiers = "w320dp-h640dp-xhdpi") @Test fun phone320_ar() = renderAll("w320_ar")
    @Config(qualifiers = "w390dp-h844dp-xxhdpi") @Test fun phone390_ar_dark() = renderAll("w390_ar_dark", dark = true)
    @Config(qualifiers = "w390dp-h844dp-xxhdpi") @Test fun phone390_en() = renderAll("w390_en", arabic = false)
    @Config(qualifiers = "w390dp-h844dp-xxhdpi") @Test fun phone390_ar_hugeText() = renderAll("w390_ar_xl", scale = 1.3f)
    @Config(qualifiers = "w844dp-h390dp-land-xxhdpi") @Test fun phoneLandscape_ar() = renderAll("land_ar")
    @Config(qualifiers = "w800dp-h1280dp-xhdpi") @Test fun tabletPortrait_ar() = renderAll("tab_port_ar")
    @Config(qualifiers = "w1280dp-h800dp-land-xhdpi") @Test fun tabletLandscape_ar_dark() = renderAll("tab_land_ar_dark", dark = true)

    @Config(qualifiers = "w390dp-h844dp-xxhdpi")
    @Test fun tasksTab_listsOpenItemsAndReminder() {
        val session = SessionViewModel(container, LOCAL_UID)
        val home = HomeViewModel(container, LOCAL_UID)
        session.selectTab(MainTab.TASKS)
        rule.setContent { Frame(true, false) { TabsChrome(session, home, false) { TabContent(session, home, null) { _, _ -> } } } }
        rule.mainClock.advanceTimeBy(800); rule.waitForIdle()
        assertTrue(rule.onAllNodesWithText("خبز", substring = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.onAllNodesWithText("خطة الأسبوع", substring = true).fetchSemanticsNodes().isNotEmpty())
        // Done items are not listed as open.
        assertTrue(rule.onAllNodesWithText("طباعة المذكرة").fetchSemanticsNodes().isEmpty())
    }

    @Config(qualifiers = "w320dp-h640dp-xhdpi")
    @Test fun longPress_selectsNotes_andTrashOffersUndo() {
        val session = SessionViewModel(container, LOCAL_UID)
        val home = HomeViewModel(container, LOCAL_UID)
        val messages = mutableListOf<com.example.ui.viewmodel.UiMessage>()
        val job = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined).launchCollect(session, messages)
        rule.setContent { Frame(true, false) { TabsChrome(session, home, false) { TabContent(session, home, null) { _, _ -> } } } }
        repeat(6) { rule.mainClock.advanceTimeBy(200); Thread.sleep(150) }
        rule.onAllNodesWithText("تسوق")[0].performTouchInput { longClick() }
        rule.waitForIdle()
        assertTrue(rule.onAllNodesWithText("المحدد", substring = true).fetchSemanticsNodes().isNotEmpty())
        save("w320_ar_selection")
        rule.onNodeWithContentDescription("نقل إلى المحذوفات").performClick()
        repeat(4) { rule.mainClock.advanceTimeBy(200); Thread.sleep(150) }
        assertTrue(messages.any { it.undo != null })
        // Undo restores the note.
        messages.first { it.undo != null }.undo!!.invoke()
        Thread.sleep(500)
        assertTrue(runBlocking { container.repository.getNote("n2")!!.note.trashedAt == null })
        job.cancel()
    }

    private fun kotlinx.coroutines.CoroutineScope.launchCollect(session: SessionViewModel, into: MutableList<com.example.ui.viewmodel.UiMessage>) =
        launch { session.messages.collect { into += it } }
}

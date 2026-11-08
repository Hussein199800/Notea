package com.example.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import com.example.AppContainer
import com.example.LOCAL_UID
import com.example.data.local.AttachmentKind
import com.example.data.local.BlockType
import com.example.data.repository.BlockDraft
import com.example.data.repository.ChecklistDraft
import com.example.data.repository.NoteDraft
import com.example.ui.i18n.LocalStrings
import com.example.ui.i18n.Strings
import com.example.ui.screens.AssistantSettingsScreen
import com.example.ui.screens.EditorScreen
import com.example.ui.screens.WelcomeScreen
import com.example.ui.theme.NoteaTheme
import com.example.ui.viewmodel.EditorViewModel
import com.example.ui.viewmodel.SessionViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders the new screens with the real container (on-device mode). NOTEA_SHOTS=<dir> saves PNGs. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1100dp-xxhdpi")
class NewScreensShotTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val container by lazy { AppContainer(app) }

    @org.junit.Before fun initWork() {
        androidx.work.testing.WorkManagerTestInitHelper.initializeTestWorkManager(app)
    }

    @Composable
    private fun Frame(arabic: Boolean = true, dark: Boolean = false, content: @Composable () -> Unit) {
        NoteaTheme(dark) {
            CompositionLocalProvider(LocalStrings provides Strings(arabic), LocalLayoutDirection provides if (arabic) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
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

    @Test fun editor_blocks_ar() {
        val repo = container.repository
        val photo = javaClass.classLoader!!.getResource("images/photo.jpg").readBytes()
        val att = runBlocking {
            repo.save(LOCAL_UID, NoteDraft(id = "shot1", title = "محاضرة التاريخ", body = "x"))
            repo.addAttachment(LOCAL_UID, "shot1", AttachmentKind.IMAGE, "page.jpg", "image/jpeg", photo.inputStream()).also {
                repo.setExtractedText(it.id, "الفصل الثالث: الحضارات القديمة في بلاد الرافدين ومصر، وأهم إنجازاتها في الكتابة والزراعة والعمارة.")
            }
        }
        runBlocking {
            repo.save(LOCAL_UID, repo.toDraft(LOCAL_UID, repo.getNote("shot1")!!)!!.copy(
                blocks = listOf(
                    BlockDraft("t1", BlockType.TEXT, "## ملخص المحاضرة\nتناولنا **نشأة الكتابة** وأثرها على تنظيم المجتمع."),
                    BlockDraft("a1", BlockType.ATTACHMENT, attachmentId = att.id),
                    BlockDraft("c1", BlockType.CHECKLIST),
                    BlockDraft("t2", BlockType.TEXT, "> المراجعة يوم الخميس")
                ),
                checklist = listOf(ChecklistDraft(text = "قراءة الفصل الثالث", checked = true, blockId = "c1"), ChecklistDraft(text = "تلخيص المصادر", blockId = "c1"))
            ))
        }
        val session = SessionViewModel(container, LOCAL_UID)
        val vm = EditorViewModel(container, LOCAL_UID, "shot1")
        rule.setContent { Frame { EditorScreen(session, vm, emptyList()) {} } }
        rule.mainClock.advanceTimeBy(1500)
        save("editor_blocks_ar")
    }

    @Test fun welcome_ar() {
        val session = SessionViewModel(container, LOCAL_UID)
        rule.setContent { Frame { WelcomeScreen(session, firstRun = true) {} } }
        save("welcome_ar")
    }

    @Test fun assistant_settings_ar() {
        val session = SessionViewModel(container, LOCAL_UID)
        rule.setContent { Frame { AssistantSettingsScreen(session) } }
        save("assistant_ar")
    }

    @Test fun transfer_en_dark() {
        val session = SessionViewModel(container, LOCAL_UID)
        val data = com.example.ui.viewmodel.DataViewModel(container, LOCAL_UID)
        rule.setContent { Frame(arabic = false, dark = true) { com.example.ui.screens.TransferScreen(session, data) } }
        save("transfer_en_dark")
    }

    private fun saveDialog(name: String) {
        rule.waitForIdle()
        val dir = System.getenv("NOTEA_SHOTS") ?: return
        val view = org.robolectric.shadows.ShadowDialog.getLatestDialog().window!!.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(android.graphics.Canvas(bmp)) }
        File(dir).mkdirs(); File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun tour_ar() {
        rule.setContent { Frame { com.example.ui.screens.TourDialog {} } }
        saveDialog("tour_ar_1")
        rule.onNodeWithText("التالي").performClick()
        saveDialog("tour_ar_2")
        org.junit.Assert.assertTrue(rule.onAllNodesWithText("الخطوة", substring = true).fetchSemanticsNodes().isNotEmpty())
    }
    @Test fun whatsNew_ar() { rule.setContent { Frame { com.example.ui.screens.WhatsNewDialog({}) {} } }; saveDialog("whatsnew_ar") }
    @Test fun releases_ar() { val session = SessionViewModel(container, LOCAL_UID); rule.setContent { Frame { com.example.ui.screens.ReleasesScreen(session) } }; save("releases_ar") }
    @Test fun diagnostics_ar_dark() {
        com.example.core.Diagnostics.clear(app)
        com.example.core.Diagnostics.log(app, "sync", "Upload incomplete for husseintaweel1@example.com; will retry")
        com.example.core.Diagnostics.log(app, "ocr", "Text recognition engine stopped on one image (skipped)", RuntimeException("SIGSEGV at /data/user/0/x"))
        val session = SessionViewModel(container, LOCAL_UID)
        rule.setContent { Frame(dark = true) { com.example.ui.screens.DiagnosticsScreen(session) } }
        save("diagnostics_ar_dark")
        // Masking applied before writing.
        org.junit.Assert.assertTrue(com.example.core.Diagnostics.read(app).none { it.contains("@example.com") || it.contains("/data/user") })
    }
    @Test fun update_ar() { val session = SessionViewModel(container, LOCAL_UID); rule.setContent { Frame { com.example.ui.screens.UpdateScreen(session) } }; save("update_ar") }
    @Test fun settings_ar() {
        val session = SessionViewModel(container, LOCAL_UID)
        rule.setContent { Frame { com.example.ui.screens.SettingsScreen(session, null, onSignOut = {}, onSignIn = null) } }
        save("settings_ar")
    }

    @Test fun textScale_persistsAcrossRestart() {
        container.settings.update { it.copy(textScale = 1.15f) }
        val reopened = com.example.core.prefs.SettingsStore(app)
        org.junit.Assert.assertEquals(1.15f, reopened.state.value.textScale)
        container.settings.update { it.copy(textScale = 1f) }
    }

    @Test fun tourShownOnceThenWhatsNewOnlyAfterUpdate() {
        val st = container.settings
        app.getSharedPreferences("arabic_notes_prefs", 0).edit().remove("tour_done").remove("whats_new_seen").putBoolean("onboarding_$LOCAL_UID", true).commit()
        val session = SessionViewModel(container, LOCAL_UID)
        session.markOnboarded()
        org.junit.Assert.assertEquals(SessionViewModel.HelpPrompt.TOUR, session.help.value)
        session.dismissHelp()
        org.junit.Assert.assertNull(session.help.value)
        session.markOnboarded()
        org.junit.Assert.assertNull(session.help.value)
        st.setLastSeenVersion("1.1.3")   // simulate an update
        session.markOnboarded()
        org.junit.Assert.assertEquals(SessionViewModel.HelpPrompt.WHATS_NEW, session.help.value)
    }

    @Test fun editor_notion_ar() {
        val repo = container.repository
        runBlocking {
            repo.save(LOCAL_UID, NoteDraft(id = "proj", title = "قضايا الموكلين", icon = "⚖️"))
            repo.save(LOCAL_UID, NoteDraft(id = "dates", title = "مواعيد الجلسات", body = "الجلسة القادمة يوم الأحد"))
            repo.save(LOCAL_UID, NoteDraft(id = "case", title = "قضية رقم 245", parentId = "proj", icon = "📁", blocks = listOf(
                BlockDraft("t1", BlockType.TEXT, "ملخص القضية. راجع [[مواعيد الجلسات]] و [[الأدلة]]"),
                BlockDraft("g1", BlockType.TOGGLE, "تفاصيل الموكل\nالاسم والعنوان ورقم الهاتف"),
                BlockDraft("k1", BlockType.CALLOUT, "لا تنسَ إحضار الوكالة الأصلية"),
                BlockDraft("t2", BlockType.TEXT, "")
            )))
            repo.save(LOCAL_UID, NoteDraft(id = "sub1", title = "المذكرات", parentId = "case", icon = "📝"))
            repo.save(LOCAL_UID, NoteDraft(id = "back", title = "متابعة", body = "مرتبطة بـ [[قضية رقم 245]]"))
        }
        val session = SessionViewModel(container, LOCAL_UID)
        val vm = EditorViewModel(container, LOCAL_UID, "case")
        rule.setContent { Frame { EditorScreen(session, vm, emptyList()) {} } }
        repeat(8) { rule.mainClock.advanceTimeBy(250); Thread.sleep(150) }
        save("editor_notion_ar")
        org.junit.Assert.assertTrue(rule.onAllNodesWithText("المذكرات", substring = true).fetchSemanticsNodes().isNotEmpty())
        org.junit.Assert.assertTrue(rule.onAllNodesWithText("متابعة", substring = true).fetchSemanticsNodes().isNotEmpty())
        // «/» at the start of a line opens the insert menu.
        rule.runOnUiThread {
            vm.focus("t2")
            vm.setText("t2", androidx.compose.ui.text.input.TextFieldValue("/", androidx.compose.ui.text.TextRange(1)))
        }
        repeat(4) { rule.mainClock.advanceTimeBy(200); Thread.sleep(100) }
        org.junit.Assert.assertNotNull(vm.trigger.value)
        save("editor_slash_ar")
        rule.runOnUiThread { vm.insertSpecial(BlockType.CALLOUT, fromSlash = true) }
        rule.waitForIdle()
        org.junit.Assert.assertNull(vm.trigger.value)
        org.junit.Assert.assertTrue(vm.state.value.blocks.none { it is com.example.ui.viewmodel.EditorBlock.Text && it.value.text.contains("/") })
    }
}

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
}

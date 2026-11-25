package com.example

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.data.local.AttachmentKind
import com.example.data.local.BlockType
import com.example.data.repository.BlockDraft
import com.example.data.repository.ChecklistDraft
import com.example.data.repository.NoteDraft
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Real Application + MainActivity, on-device mode, notes like the user's (templates + a scanned photo). */
class TestNoteaApp : NoteaApp() {
    override fun onCreate() {
        WorkManagerTestInitHelper.initializeTestWorkManager(this)
        super.onCreate()
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestNoteaApp::class, sdk = [35])
class AppStartupTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun launch_withTemplateNotesAndPhoto_doesNotCrash() {
        val app = ApplicationProvider.getApplicationContext<NoteaApp>()
        val c = app.container
        runBlocking {
            c.repository.save(LOCAL_UID, NoteDraft(id = "p", title = "مشروع: ", body = "## الهدف\n\n## المراحل\n1. \n\n## الموارد\n- ",
                checklist = listOf(ChecklistDraft(text = "تحديد النطاق"), ChecklistDraft(text = "الخطة الزمنية"))))
            c.repository.save(LOCAL_UID, NoteDraft(id = "d", title = "قرار: ", body = "## القرار\n\n## لماذا؟\n- ", tags = listOf("قرار")))
            c.repository.save(LOCAL_UID, NoteDraft(id = "m", title = "اجتماع: ", body = "**التاريخ:** \n", checklist = listOf(ChecklistDraft(text = "متابعة المهام"))))
            c.repository.save(LOCAL_UID, NoteDraft(id = "s", title = "", body = "", blocks = listOf(BlockDraft("t", BlockType.TEXT, ""))))
            val photo = javaClass.classLoader!!.getResource("images/photo.jpg").readBytes()
            c.repository.addAttachment(LOCAL_UID, "s", AttachmentKind.IMAGE, "scan.jpg", "image/jpeg", photo.inputStream())
        }
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(5_000)
        rule.waitForIdle()
        // Run every queued background job (OCR, sync, auto-backup) like the phone does after launch.
        val wm = androidx.work.WorkManager.getInstance(app)
        val infos = wm.getWorkInfosByTag(com.example.ocr.OcrWorker::class.java.name).get() +
            wm.getWorkInfosByTag(com.example.data.backup.AutoBackupWorker::class.java.name).get()
        val driver = WorkManagerTestInitHelper.getTestDriver(app)!!
        infos.forEach { runCatching { driver.setAllConstraintsMet(it.id); driver.setInitialDelayMet(it.id); driver.setPeriodDelayMet(it.id) } }
        rule.mainClock.advanceTimeBy(5_000)
        rule.waitForIdle()
    }
}

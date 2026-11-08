package com.example

import android.content.Context
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Regression test: every folder the app hands to the camera/viewers must be declared in file_paths.xml. */
@RunWith(RobolectricTestRunner::class)
class FileProviderPathsTest {
    @Test fun allSharedFoldersResolve() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val authority = ctx.packageName + ".files"
        listOf(
            File(ctx.cacheDir, "camera/scan_1.jpg"),      // document-scan camera fallback
            File(ctx.cacheDir, "camera/qr.jpg"),          // transfer QR photo
            File(ctx.cacheDir, "decrypted/a.pdf"),        // opening locked attachments
            File(ctx.filesDir, "attachments/u/a")          // opening attachments
        ).forEach { f ->
            f.parentFile?.mkdirs(); f.writeText("x")
            val uri = FileProvider.getUriForFile(ctx, authority, f)
            assertTrue(uri.toString().startsWith("content://$authority/"))
        }
    }
}

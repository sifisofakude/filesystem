package io.github.sifisofakude.filesystem

import android.content.Intent
import android.net.Uri
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
class AndroidSafFileSystemTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private lateinit var device: UiDevice
    private lateinit var fs: AndroidSafFileSystem
		private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setup() {
        device = UiDevice.getInstance(instrumentation)
        fs = AndroidSafFileSystem(context)
    }

    @Test
    fun selectedDirectorySupportsRelativePaths() {
        val root = selectSafDirectory()

        assertNotNull(root)

        fs.changeSelectedDirectory(root)

        assertEquals(
            root.toString(),
            fs.getCurrentDirectory()
        )

        assertTrue(
            fs.isSafContext("test")
        )

        assertTrue(
            fs.isRelative("test")
        )

        val directory = fs.createDirectory("saf-test")

        assertNotNull(directory)
        assertTrue(fs.exists("saf-test"))
        assertTrue(fs.isDirectory("saf-test"))
    }

    private fun selectSafDirectory(): Uri {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }

        val result = instrumentation
            .uiAutomation
            .adoptShellPermissionIdentity(
                "android.permission.WRITE_EXTERNAL_STORAGE"
            )

        try {
            val intent = Intent(context,SafPickerActivity::class.java).apply	{
            	addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            instrumentation.startActivitySync(intent)

            // We will automate DocumentsUI here.
            //
            // For the first test, pause so we can verify the picker
            // is actually available on the CI emulator.
            device.waitForIdle()

            throw AssertionError(
                "SAF picker opened. Root selection automation comes next."
            )
        } finally {
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }
    }
}

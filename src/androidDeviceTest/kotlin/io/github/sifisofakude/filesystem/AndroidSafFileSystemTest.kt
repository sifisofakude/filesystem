package io.github.sifisofakude.filesystem

import android.content.Intent
import android.net.Uri
import android.content.Context
import android.app.Activity
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.core.app.ApplicationProvider
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

import java.nio.charset.Charsets
import java.io.ByteArrayOutputStream


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
    fun independentSafRoots() {
        val downloadsUri = selectDownloads()

    		fs.changeSelectedDirectory(downloadsUri)
    		
        assertNotNull(fs.getCurrentDirectory())
    
        val root1 = fs.createDirectory("filesystem-test-1")
            ?: error("Failed to create test root 1")
    
        val root2 = fs.createDirectory("filesystem-test-2")
            ?: error("Failed to create test root 2")
    
        val root3 = fs.createDirectory("filesystem-test-3")
            ?: error("Failed to create test root 3")
    
        // Downloads permission is no longer needed.
        context.contentResolver.releasePersistableUriPermission(
            downloadsUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
    
        // Now each directory is selected independently through SAF.
    //     val selectedRoot1 = selectSafDirectory()
    //     assertTrue(
    //         fs.changeSelectedDirectory(selectedRoot1.toString()),
    //         "Failed to select test root 1"
    //     )
    // 
    //     val selectedRoot2 = selectSafDirectory()
    //     assertTrue(
    //         fs.changeSelectedDirectory(selectedRoot2.toString()),
    //         "Failed to select test root 2"
    //     )
    // 
    //     val selectedRoot3 = selectSafDirectory()
    //     assertTrue(
    //         fs.changeSelectedDirectory(selectedRoot3.toString()),
    //         "Failed to select test root 3"
    //     )
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

            return waitForPickerResult()
        } finally {
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }
    }

    private fun selectDownloads(): Uri {
        SafPickerActivity.resultUri = null
        SafPickerActivity.resultCode = Activity.RESULT_CANCELED
    
        val context = ApplicationProvider
            .getApplicationContext<Context>()
    
        val intent = Intent(context, SafPickerActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    
        instrumentation.startActivitySync(intent)
    
        val device = UiDevice.getInstance(instrumentation)
    
    //     check(
    //         device.wait(
    //             Until.hasObject(By.text("Downloads")),
    //             10_000
    //         )
    //     ) {
    //         "Downloads was not visible in SAF picker"
    //     }
    // 
    //     device.findObject(By.text("Downloads")).click()
    // 
    //     check(
    //         device.wait(
    //             Until.hasObject(By.text("Use this folder")),
    //             10_000
    //         )
    //     ) {
    //         "Use this folder was not visible"
    //     }
    // 
    //     device.findObject(By.text("Use this folder")).click()

				val output = ByteArrayOutputStream()
				device.dumpWindowHierarchy(output)
    		println("=========== SAF PICKER UI ======================")
    		println(output.toString(Charsets.UTF_8.name()))
    		println("================================================")

    
        return waitForPickerResult()
    }

    private fun waitForPickerResult(): Uri {
        repeat(50) {
            SafPickerActivity.resultUri?.let { return it }
            Thread.sleep(100)
        }
    
        error("SAF picker did not return a URI")
    }
}

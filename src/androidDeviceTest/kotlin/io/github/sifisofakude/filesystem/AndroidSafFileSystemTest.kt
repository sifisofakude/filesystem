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

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern


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
        assertTrue(adbCreateDirectory("Root1"))
        assertTrue(adbCreateDirectory("Root2"))
        assertTrue(adbCreateDirectory("Root3"))

        Thread.sleep(5000)

        val root1 = constructUri("Root1")
        assertTrue(selectFolder(root1))
        
        val root2 = constructUri("Root2")
        assertTrue(selectFolder(root2))
        
        val root3 = constructUri("Root3")
        assertTrue(selectFolder(root3))
    }

    private fun selectFolder(uri: String): Boolean	{
    	SafPickerActivity.resultUri = null
    	SafPickerActivity.resultCode = Activity.RESULT_CANCELED
    	
    	val intent = Intent(context,SafPickerActivity::class.java).apply	{
    		addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    		putExtra("selectUri",uri)
    	}

    	instrumentation.startActivitySync(intent)

      device.wait(
      	Until.findObject(
      		By.clickable(true).text(Pattern.compile("(?i)(Use this folder|Select)"))
      	),
      	5000
      )?.let	{ selectButton ->
      	selectButton.click()

      	device.wait(
      		Until.findObject(
      			By.clickable(true).text(Pattern.compile("(?i)Allow"))
      		),
      		5000
      	)?.let	{ allowButton ->
      		allowButton.click()
      	}
      }

    	return try	{
    		waitForPickerResult()
    		true
    	}catch(_: IllegalArgumentException)	{
    		true
    	}
    }

    private fun adbCreateDirectory(dir: String): Boolean	{
    	val folder = instrumentation
    		.getUiAutomation()
    		.executeShellCommand("mkdir -p /sdcard/$dir")

    	return if(folder.fileDescriptor.valid())	{
    		folder.close()
    		true
    	}else	{
    		false
    	}
    }

    private fun constructUri(relativePath: String): String	{
    	if(relativePath.isEmpty()) throw IllegalArgumentException("Path can't be an empty string")

    	return "content://com.android.externalstorage.documents/document/primary:$relativePath"
    }

    private fun adbRemoveDirectory(dir: String): Boolean	{
    	val folder = instrumentation
    		.getUiAutomation()
    		.executeShellCommand("rm -r /sdcard/$dir")

    	return if(!folder.fileDescriptor.valid())	{
    		folder.close()
    		true
    	}else	{
    		false
    	}
    }

    private fun waitForPickerResult(): Uri {
        repeat(50) {
            SafPickerActivity.resultUri?.let { return it }
            Thread.sleep(100)
        }
    
        throw IllegalArgumentException("SAF did not return result")
    }
}

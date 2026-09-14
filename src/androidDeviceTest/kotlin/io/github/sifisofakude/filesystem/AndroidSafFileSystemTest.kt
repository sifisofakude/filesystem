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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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
        assertTrue(adbCreateDirectory("Root1/jane/doe"))
        assertTrue(adbCreateDirectory("Root2"))
        assertTrue(adbCreateDirectory("Root3"))

        val root1 = constructUri("Root1")
        assertNotNull(selectFolder(root1))
        
        val root2 = constructUri("Root2")
        assertNotNull(selectFolder(root2))
        
        val root3 = constructUri("Root3")
        assertNotNull(selectFolder(root3))
    }

    @Test
    fun basicOperations() {
        val root = selectFolder(constructUri("Root1/jane/doe"))
            ?: error("Could not select Root1")

        fs.changeSelectedDirectory(root)

        Thread.sleep(1000)

        val stringRoot = root.toString()

        assertNotNull(fs.getCurrentDirectory())
        assertTrue(fs.isSafUri(stringRoot))
        assertTrue(fs.isSafContext("bobby/damn/man.txt"))
        assertTrue(fs.isRelative("bobby/damn/man.sxt"))
        assertTrue(fs.isTreeUri(stringRoot))
    
       	fail("${fs.resolveRelativeUri(root,"got/to/go/za")}")
    }

    @Test
    fun readWriteText() {
        
    }

    @Test
    fun selectedDirectorySupportsRelativePaths() {
      	
    }

    @Test
    fun copyByStream() {
        
    }

    @Test
    fun moveByStream() {
        
    }

    private fun selectUri(uri: String): Uri?	{
    	val rel = fs.relativePathFromUri(uri)
    	val sanitizedUri = if(rel.relativePath.isNotEmpty())	{
    		"${rel.rootUri}%2F${rel.relativePath.replace("/","%2F")}"
    	}else	{
    		rel.rootUri
    	}
    	
    	if(!fs.exists(sanitizedUri)) return null

    	return if(fs.isFile(sanitizedUri))	{
    		val name = fs.getName(sanitizedUri)
    		val root = fs.getParentFile(sanitizedUri) ?: return null

    		// selectFile(root,name)
    		null
    	}else	{
    		selectFolder(sanitizedUri)
    	}
    }

//     private fun selectFile(root: String, name: String): Uri?	{
//     	SafPickerActivity.resultUri = null
//     	SafPickerActivity.resultCode = Activity.RESULT_CANCELED
//     	
//     	val intent = Intent(context,SafPickerActivity::class.java).apply	{
//     		addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
//     		putExtra("selectUri",root)
//     	}
// 
//     	instrumentation.startActivitySync(intent)
// 
//       device.wait(
//       	Until.findObject(
//       		By.clazzName("android.widget.TextView").text(name)
//       	),
//       	5000
//       )?.click()
// 
//     	return try	{
//     		waitForPickerResult()
//     	}catch(_: IllegalArgumentException)	{
//     		null
//     	}
//     }

    private fun selectFolder(uri: String): Uri?	{
    	SafPickerActivity.resultUri = null
    	SafPickerActivity.resultCode = Activity.RESULT_CANCELED

    	val intent = Intent(context,SafPickerActivity::class.java).apply	{
    		addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    		putExtra("selectUri",uri)
    		putExtra("isFolder",true)
    	}

    	instrumentation.startActivitySync(intent)
    	// context.startActivity(intent)

    	device.waitForIdle()

      device.wait(
      	Until.findObject(
      		By.clickable(true).text(Pattern.compile("(?i)(Use this folder|Select)"))
      	),
      	5000
      )?.click()
      
     	device.wait(
     		Until.findObject(
     			By.clickable(true).text(Pattern.compile("(?i)Allow"))
     		),
     		5000
     	)?.click()

    	return try	{
    		waitForPickerResult()
    	}catch(_: IllegalArgumentException)	{
    		null
    	}
    }

    private fun adbCreateDirectory(dir: String): Boolean	{
    	if(dir.isEmpty()) return false
    	
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

    	return "content://com.android.externalstorage.documents/document/primary:${relativePath.replace("/","%2F")}"
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

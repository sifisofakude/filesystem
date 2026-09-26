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
    fun selectRoot() {
        assertTrue(adbCreateDirectory("Root1"))
    
        val root = selectFolder(constructUri("Root1"))
    
        assertNotNull(root)
        assertTrue(fs.isSafUri(root.toString()))
        assertTrue(fs.isTreeUri(root.toString()))
    }

    @Test
    fun selectedDirectoryBecomesCurrentDirectory() {
        assertTrue(adbCreateDirectory("Root1"))
    
        val root = selectFolder(constructUri("Root1"))
            ?: error("Could not select Root1")
    
        fs.changeSelectedDirectory(root)
    
        assertNotNull(fs.getCurrentDirectory())
        assertEquals(root.toString(), fs.getCurrentDirectory().toString())
    }

    @Test
    fun relativePathsAreSafContext() {
        assertTrue(adbCreateDirectory("Root1"))
    
        val root = selectFolder(constructUri("Root1"))
            ?: error("Could not select Root1")
    
        fs.changeSelectedDirectory(root)
    
        assertTrue(fs.isRelative("hello.txt"))
        assertTrue(fs.isRelative("foo/bar.txt"))
    
        assertTrue(fs.isSafContext("hello.txt"))
        assertTrue(fs.isSafContext("foo/bar.txt"))
    }

    @Test
    fun resolveRelativeFileUri() {
        assertTrue(adbCreateDirectory("Root1"))
    
        val root = selectFolder(constructUri("Root1"))
            ?: error("Could not select Root1")
    
        fs.changeSelectedDirectory(root)
    
        val uri = fs.resolveRelativeUri(
            root,
            "hello.txt"
        )

    		uri?.let	{
        	assertTrue(it.contains("hello.txt"))
    		}
    }

    @Test
    fun resolveNestedRelativeUri() {
        assertTrue(adbCreateDirectory("Root1"))
    
        val root = selectFolder(constructUri("Root1"))
            ?: error("Could not select Root1")
    
        fs.changeSelectedDirectory(root)
    
        val uri = fs.resolveRelativeUri(
            root,
            "foo/bar/baz.txt"
        )

    		uri?.let	{
        	assertTrue(it.contains("foo"))
        	assertTrue(it.contains("bar"))
        	assertTrue(it.contains("baz.txt"))
        }
    }

    @Test
    fun relativePathFromRoot() {
        assertTrue(adbCreateDirectory("Root1"))
    
        val root = selectFolder(constructUri("Root1"))
            ?: error("Could not select Root1")
    
        fs.changeSelectedDirectory(root)
    
        val result = fs.relativePathFromUri(
            root.toString()
        )

    		result?.let	{
        	assertEquals("", it.relativePath)
    		}
    }

    @Test
    fun createDirectory() {
        assertTrue(adbCreateDirectory("Root1"))
    
        val root = selectFolder(constructUri("Root1"))
            ?: error("Could not select Root1")
    
        fs.changeSelectedDirectory(root)
    
        val result = fs.createDirectory("foo")

        // assertNotNull(result)
        // assertTrue(fs.exists("foo/"))
        // assertTrue(fs.isDirectory("foo/"))
    }

    @Test
    fun createNestedDirectory() {
        assertTrue(adbCreateDirectory("Root1"))
    
        val root = selectFolder(constructUri("Root1"))
            ?: error("Could not select Root1")
    
        fs.changeSelectedDirectory(root)
    
        // val result = fs.createDirectory(
        //     "foo/bar/baz"
        // )
    
        // assertNotNull(result)
    
    //     assertTrue(fs.exists("foo"))
    //     assertTrue(fs.exists("foo/bar"))
    //     assertTrue(fs.exists("foo/bar/baz"))
    // 
    //     assertTrue(fs.isDirectory("foo"))
    //     assertTrue(fs.isDirectory("foo/bar"))
    //     assertTrue(fs.isDirectory("foo/bar/baz"))
    }

    @Test
    fun createFile() {
        assertTrue(adbCreateDirectory("Root1"))
    
        val root = selectFolder(constructUri("Root1"))
            ?: error("Could not select Root1")
    
        fs.changeSelectedDirectory(root)
    
        // val result = fs.createFile("hello.txt")
    
        // assertNotNull(result)
        // assertTrue(fs.exists("hello.txt"))
        // assertTrue(fs.isFile("hello.txt"))
    }

    @Test
    fun createFileInNestedDirectory() {
        assertTrue(adbCreateDirectory("Root1"))
    
        val root = selectFolder(constructUri("Root1"))
            ?: error("Could not select Root1")
    
        fs.changeSelectedDirectory(root)
    
        // assertNotNull(
            fs.createDirectory("foo/bar")
        // )
    
        val result = fs.createFile(
            "foo/bar/hello.txt"
        )
    
        // assertNotNull(result)
        // assertTrue(fs.exists("foo/bar/hello.txt"))
        // assertTrue(fs.isFile("foo/bar/hello.txt"))
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

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
        assertTrue(adbCreateDirectory("Root1"))
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
        val root = selectFolder(constructUri("Root1"))
            ?: error("Could not select Root1")
    
        fs.changeSelectedDirectory(root)

        fail("Created file: ${fs.createFile("$root/test.txt")}")
    
    //     assertNotNull(fs.createDirectory("docs"))
    //     assertTrue(fs.exists("docs"))
    //     assertTrue(fs.isDirectory("docs"))
    // 
    //     assertNotNull(fs.createFile("docs/test.txt"))
    //     assertTrue(fs.exists("docs/test.txt"))
    //     assertTrue(fs.isFile("docs/test.txt"))
    // 
    //     assertEquals("test.txt", fs.getName("docs/test.txt"))
    //     assertEquals("txt", fs.getExtension("docs/test.txt"))
    //     assertEquals("docs", fs.getName(fs.getParentFile("docs/test.txt")!!))
    }

    @Test
    fun readWriteText() {
        // val root = selectFolder(constructUri("Root1"))
            // ?: error("Could not select Root1")
    
    //     fs.changeSelectedDirectory(root)
    // 
    //     val path = "hello.txt"
    // 
    //     assertNotNull(fs.createFile(path))
    // 
    //     assertTrue(
    //         fs.writeText(path, "Hello SAF")
    //     )
    // 
    //     assertEquals(
    //         "Hello SAF",
    //         fs.readText(path)
    //     )
    // 
    //     assertTrue(
    //         fs.appendText(path, "!")
    //     )
    // 
    //     assertEquals(
    //         "Hello SAF!",
    //         fs.readText(path)
    //     )
    }

    @Test
    fun selectedDirectorySupportsRelativePaths() {
        // val root = selectFolder(constructUri("Root1"))
            // ?: error("Could not select Root1")
    
    //     fs.changeSelectedDirectory(root)
    // 
    //     assertTrue(fs.isRelative("test.txt"))
    // 
    //     assertNotNull(fs.createDirectory("a/b"))
    //     assertNotNull(fs.createFile("a/b/test.txt"))
    // 
    //     assertTrue(fs.exists("a"))
    //     assertTrue(fs.exists("a/b"))
    //     assertTrue(fs.exists("a/b/test.txt"))
    }

    @Test
    fun copyByStream() {
        // val root = selectFolder(constructUri("Root2"))
            // ?: error("Could not select Root2")
    // 
    //     fs.changeSelectedDirectory(root)
    // 
    //     fs.createFile("source/test.txt")
    //     fs.createDirectory("destination")
    // 
    //     assertTrue(
    //         fs.writeText(
    //             "source/test.txt",
    //             "stream copy"
    //         )
    //     )
    // 
    //     val result = fs.copyByStream(
    //         "source/test.txt",
    //         "destination"
    //     )
    // 
    //     assertNotNull(result)
    // 
    //     assertTrue(
    //         fs.exists("destination/test.txt")
    //     )
    // 
    //     assertEquals(
    //         "stream copy",
    //         fs.readText("destination/test.txt")
    //     )
    }

    @Test
    fun moveByStream() {
        // val root = selectFolder(constructUri("Root2"))
            // ?: error("Could not select Root2")
    
    //     fs.changeSelectedDirectory(root)
    // 
    //     fs.createDirectory("source")
    //     fs.createDirectory("destination")
    // 
    //     fs.writeText(
    //         "source/test.txt",
    //         "stream move"
    //     )
    // 
    //     val result = fs.moveByStream(
    //         "source/test.txt",
    //         "destination"
    //     )
    // 
    //     assertNotNull(result)
    // 
    //     assertTrue(
    //         fs.exists("destination/test.txt")
    //     )
    // 
    //     assertTrue(
    //         !fs.exists("source/test.txt")
    //     )
    // 
    //     assertEquals(
    //         "stream move",
    //         fs.readText("destination/test.txt")
    //     )
    }

    private fun selectUri(uri: String): Uri?	{
    	if(!fs.exists(uri)) return null

    	return if(fs.isFile(uri))	{
    		val name = fs.getName(uri)
    		val root = fs.getParentFile(uri) ?: return null

    		// selectFile(root,name)
    		null
    	}else	{
    		selectFolder(uri)
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
    	}

    	instrumentation.startActivitySync(intent)

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

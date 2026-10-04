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

import android.provider.DocumentsContract

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

import java.io.File
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern


@RunWith(AndroidJUnit4::class)
class AndroidSafFileSystemTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private lateinit var device: UiDevice
    private lateinit var fs: AndroidSafFileSystem
		private val context = ApplicationProvider.getApplicationContext<Context>()

		private lateinit var ROOT1: Uri
		private lateinit var ROOT2: Uri

    @Before
    fun setup() {
        device = UiDevice.getInstance(instrumentation)
        fs = AndroidSafFileSystem(context)

        adbCreateDirectory("Root1")
        adbCreateDirectory("Root2")
        
        ROOT1 = selectFolder(constructUri("Root1"))
        	?: error("Could not select Root1")
        	
        ROOT2 = selectFolder(constructUri("Root2"))
        	?: error("Could not select Root2")
        	
    }

    @Test
    fun selectRoot() {
        assertNotNull(ROOT1)
        assertTrue(fs.isSafUri(ROOT1.toString()))
        assertTrue(fs.isTreeUri(ROOT1.toString()))
    
        assertNotNull(ROOT2)
        assertTrue(fs.isSafUri(ROOT2.toString()))
        assertTrue(fs.isTreeUri(ROOT2.toString()))
    }

		@Test
		fun allInOneTest()	{
			// Changing selected root
			fs.changeSelectedDirectory(ROOT1)

			assertNotNull(fs.getCurrentDirectory())
			
			// Create directories
			assertNotNull(fs.createDirectory("test1"))
			assertNotNull(fs.createDirectory("test1/1/2/3/4/5"))
			assertNotNull(fs.createDirectory("$ROOT2||test2/1/2/3/4/5"))

			assertTrue(fs.exists("$ROOT1||test1/1/2/3"))
			assertTrue(fs.isDirectory("test1"))

			// Create files
			assertNotNull(fs.createFile("test1/test-file.txt"))
			assertTrue(fs.isFile("test1/test-file.txt"))
			assertNotNull(fs.createFile("$ROOT2||test2/1/2/test-file.txt"))

			// Write text to file
			assertTrue(fs.writeText("test1/test-file.txt","Testing testing"))
			assertTrue(fs.writeText("test1/1/2/two.txt","another testing file"))

			// Read text from file
			assertEquals(
				"Testing testing",
				fs.readText("test1/test-file.txt")
			)
			
			assertEquals("",fs.readText("$ROOT2||test2/1/2/test-file.txt"))

			// Copy
			assertNotNull(fs.copy("test1/test-file.txt","test1/1"))
			
			assertTrue(fs.exists("test1/1/test-file.txt"))

			val jvmPath = File(context.filesDir,"jvm-testing-dir").apply	{
				deleteRecursively()
				mkdirs()
			}
			
			assertNotNull(fs.copy("test1",jvmPath.absolutePath))
			assertNotNull(fs.copy("test1","$ROOT2||test2/1/2"))

			assertTrue(fs.exists("${jvmPath.absolutePath}/test1"))
			assertTrue(fs.exists("$ROOT2||test2/1/2/test1"))

			// Move
			assertNotNull(fs.move("test1/test-file.txt","test1/1/new-name.txt"))

			assertTrue(!fs.exists("test1/test-file.txt"))
			assertTrue(fs.exists("test1/1/new-name.txt"))

			assertNotNull(fs.move("$ROOT1||test1/1/2","$ROOT2||test2"))

			assertTrue(fs.isDirectory("$ROOT2||test2/2"))

			// Listing files
			val files = fs.listFiles("test1/1")

			assertEquals(2,files.size)
			assertTrue(files.contains("new-name.txt"))

			// Finding files in a directory
			val found = fs.findFiles("test1",emptySet())

			val parentFile = fs.getParentFile("$ROOT1||test1/1")

			assertEquals("$ROOT1||test1",parentFile)

			val resolvedPath = fs.resolvePath("test1/1/2/../new-name.txt")
			assertEquals("test1/1/new-name.txt",resolvedPath)

			val lastModified = fs.lastModified("test1/1")
			assertNotEquals(-1,lastModified)

			jvmPath.deleteRecursively()
			assertTrue(deleteAllFiles("$ROOT1"))
			assertTrue(deleteAllFiles("$ROOT2"))
		}

    private fun deleteAllFiles(uri: String): Boolean	{
    	fs.listFiles(uri).forEach	{ file ->
    		val path = fs.combinePath(uri,file) ?: return false
    		if(!fs.delete(path)) return false
    	}
    	return true
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

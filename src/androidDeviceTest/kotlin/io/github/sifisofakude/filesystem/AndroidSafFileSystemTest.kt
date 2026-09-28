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
		// private lateinit var ROOT3: Uri

    @Before
    fun setup() {
        device = UiDevice.getInstance(instrumentation)
        fs = AndroidSafFileSystem(context)

        adbCreateDirectory("Root1")
        adbCreateDirectory("Root2")
        adbCreateDirectory("Root3")
        
        ROOT1 = selectFolder(constructUri("Root1"))
        	?: error("Could not select Root1")
        	
        ROOT2 = selectFolder(constructUri("Root2"))
        	?: error("Could not select Root2")
        	
        // ROOT3 = selectFolder(constructUri("Root3"))
        	// ?: error("Could not select Root3")
    }

    @Test
    fun selectRoot() {
        assertNotNull(ROOT1)
        assertTrue(fs.isSafUri(ROOT1.toString()))
        assertTrue(fs.isTreeUri(ROOT1.toString()))
    
        assertNotNull(ROOT2)
        assertTrue(fs.isSafUri(ROOT2.toString()))
        assertTrue(fs.isTreeUri(ROOT2.toString()))
    
        // assertNotNull(ROOT3)
        // assertTrue(fs.isSafUri(ROOT3.toString()))
        // assertTrue(fs.isTreeUri(ROOT3.toString()))
    }

//     @Test
//     fun selectedDirectoryBecomesCurrentDirectory() {
//         val root = ROOT1
//     
//         fs.changeSelectedDirectory(root)
//     
//         assertNotNull(fs.getCurrentDirectory())
//         assertEquals(root.toString(), fs.getCurrentDirectory().toString())
//     }
// 
//     @Test
//     fun relativePathsAreSafContext() {
//         val root = ROOT1
//     
//         fs.changeSelectedDirectory(root)
//     
//         assertTrue(fs.isRelative("hello.txt"))
//         assertTrue(fs.isRelative("foo/bar.txt"))
//     
//         assertTrue(fs.isSafContext("hello.txt"))
//         assertTrue(fs.isSafContext("foo/bar.txt"))
//     }
// 
//     @Test
//     fun resolveRelativeFileUri() {
//         val root = ROOT1
//     
//         fs.changeSelectedDirectory(root)
//     
//         val uri = fs.resolveRelativeUri(
//             root,
//             "hello.txt"
//         )
// 
//     		uri?.let	{
//         	assertTrue(it.contains("hello.txt"))
//     		}
//     }
// 
//     @Test
//     fun resolveNestedRelativeUri() {
//         val root = ROOT1
//     
//         fs.changeSelectedDirectory(root)
//     
//         val uri = fs.resolveRelativeUri(
//             root,
//             "foo/bar/baz.txt"
//         )
// 
//     		uri?.let	{
//         	assertTrue(it.contains("foo"))
//         	assertTrue(it.contains("bar"))
//         	assertTrue(it.contains("baz.txt"))
//         }
//     }
// 
//     @Test
//     fun relativePathFromRoot() {
//         val root = ROOT1
//     
//         fs.changeSelectedDirectory(root)
//     
//         val result = fs.relativePathFromUri(
//             root.toString()
//         )
// 
//     		result?.let	{
//         	assertEquals("", it.relativePath)
//     		}
//     }
// 
//     @Test
//     fun createDirectory() {
//         val root = ROOT1
//     
//         fs.changeSelectedDirectory(root)
//     
//         val result = fs.createDirectory("foo")
// 
//         assertNotNull(result)
//         assertTrue(fs.exists("foo"))
//         assertTrue(fs.isDirectory("foo"))
// 
//         deleteAllFiles(root.toString())
//     }
// 
//     @Test
//     fun createNestedDirectory() {
//         val root = ROOT1
//     
//         fs.changeSelectedDirectory(root)
//     
//         val result = fs.createDirectory(
//             "foo/bar/baz"
//         )
//     
//         assertNotNull(result)
//     
//         assertTrue(fs.exists("foo/"))
//         assertTrue(fs.exists("foo/bar"))
//         assertTrue(fs.exists("foo/bar/baz"))
//     
//         assertTrue(fs.isDirectory("foo"))
//         assertTrue(fs.isDirectory("foo/bar"))
//         assertTrue(fs.isDirectory("foo/bar/baz"))
// 
//         deleteAllFiles(root.toString())
//     }
// 
//     @Test
//     fun createFile() {
//         val root = ROOT1
//     
//         fs.changeSelectedDirectory(root)
//     
//         val result = fs.createFile("hello.txt")
//     
//         assertNotNull(result)
//         assertTrue(fs.exists("hello.txt"))
//         assertTrue(fs.isFile("hello.txt"))
// 
//         deleteAllFiles(root.toString())
//     }
// 
//     @Test
//     fun createFileInNestedDirectory() {
//         val root = ROOT1
//     
//         fs.changeSelectedDirectory(root)
//     
//         val result = fs.createFile("foo/bar/hello.txt")
//     
//         assertNotNull(result)
//         assertTrue(fs.exists("foo/bar/hello.txt"))
//         assertTrue(fs.isFile("foo/bar/hello.txt"))
// 
//         deleteAllFiles(root.toString())
//     }
// 
//     @Test
//     fun getDocumentFileForFile() {
//         val root = ROOT1
//     
//         fs.changeSelectedDirectory(root)
//     
//         assertNotNull(
//             fs.createFile("hello.txt")
//         )
//     
//         val document = fs.getDocumentFile(
//             "hello.txt"
//         )
//     
//         assertNotNull(document)
//         assertTrue(document!!.exists())
//         assertTrue(document.isFile)
//         assertEquals("hello.txt", document.name)
// 
//         deleteAllFiles(root.toString())
//     }
// 
//     @Test
//     fun getDocumentFileForNestedFile() {
//         val root = ROOT1
//     
//         fs.changeSelectedDirectory(root)
//     
//         assertNotNull(
//             fs.createDirectory("foo/bar")
//         )
//     
//         assertNotNull(
//             fs.createFile("foo/bar/test.txt")
//         )
//     
//         val document = fs.getDocumentFile(
//             "foo/bar/test.txt"
//         )
//     
//         assertNotNull(document)
//         assertTrue(document!!.exists())
//         assertTrue(document.isFile)
//         assertEquals("test.txt", document.name)
// 
//         deleteAllFiles(root.toString())
//     }
// 
//     @Test
//     fun nonexistentPathDoesNotExist() {
//         val root = ROOT1
//     
//         fs.changeSelectedDirectory(root)
//     
//         assertFalse(
//             fs.exists("this/does/not/exist.txt")
//         )
//     }
// 
//     @Test
//     fun directoryIsNotFile() {
//         val root = ROOT1
//     
//         fs.changeSelectedDirectory(root)
//     
//         assertNotNull(
//             fs.createDirectory("foo")
//         )
//     
//         assertTrue(fs.exists("foo"))
//         assertTrue(fs.isDirectory("foo"))
//         assertFalse(fs.isFile("foo"))
// 
//         deleteAllFiles(root.toString())
//     }
// 
//     @Test
//     fun fileIsNotDirectory() {
//         val root = ROOT1
//     
//         fs.changeSelectedDirectory(root)
//     
//         assertNotNull(
//             fs.createFile("foo.txt")
//         )
//     
//         assertTrue(fs.exists("foo.txt"))
//         assertTrue(fs.isFile("foo.txt"))
//         assertFalse(fs.isDirectory("foo.txt"))
// 
//         deleteAllFiles(root.toString())
//     }
// 
//     @Test
//     fun relativePathFromPlainRelativePath() {
//         val root = ROOT1
//     
//         fs.changeSelectedDirectory(root)
//         
//         val path = "path/to/file.txt"
//     
//         val result = fs.relativePathFromUri(path)
//     
//         assertEquals(path, result.relativePath)
//     }
// 
//     @Test
//     fun relativePathFromUriWithRelativePath() {
//         val root = ROOT1
//     
//         fs.changeSelectedDirectory(root)
//     
//         val path = "path/to/file.txt"
//         val uri = "${root}||$path"
//     
//         val result = fs.relativePathFromUri(uri)
//     
//         assertEquals(path, result.relativePath)
//     }
// 
//     @Test
//     fun relativePathFromAbsoluteSafUri() {
//         val root = ROOT1
//     
//         fs.changeSelectedDirectory(root)
//     
//         assertNotNull(
//             fs.createDirectory("path/to")
//         )
//     
//         assertNotNull(
//             fs.createFile("path/to/file.txt")
//         )
//     
//         val uri = fs.resolveRelativeUri(
//             root,
//             "path/to/file.txt"
//         )
//     
//         val result = fs.relativePathFromUri(uri!!)
//     
//         assertEquals(
//             "",result.relativePath
//         )
// 
//         deleteAllFiles(root.toString())
//     }
// 
//     @Test
//     fun resolveFilesSingleFile() {
//         val root = ROOT1
//     
//         fs.changeSelectedDirectory(root)
//     
//         assertNotNull(
//             fs.createFile("foo.txt")
//         )
//     
//         val result = fs.resolveFiles(
//             listOf("foo.txt"),
//             emptySet()
//         )
//     
//         assertEquals(1, result.size)
//         assertEquals("foo.txt", result[0].relativePath)
//         assertTrue(result[0].absolutePath.startsWith("content://"))
// 
//         deleteAllFiles(root.toString())
//     }
// 
//     @Test
//     fun resolveFilesNestedDirectory() {
//         val root = ROOT1
//     
//         fs.changeSelectedDirectory(root)
//     
//         assertNotNull(
//             fs.createFile("foo/bar/test.txt")
//         )
//     
//         val result = fs.resolveFiles(
//             listOf("foo"),
//             emptySet()
//         )
//     
//         assertEquals(1, result.size)
//         assertEquals(
//             "bar/test.txt",
//             result[0].relativePath
//         )
//     
//         assertTrue(
//             result[0].absolutePath.startsWith("content://")
//         )
// 
//         deleteAllFiles(root.toString())
//     }
// 
//     @Test
//     fun resolveFilesFiltersExtensions() {
//         val root = ROOT1
//     
//         fs.changeSelectedDirectory(root)
//     
//         assertNotNull(fs.createFile("one.txt"))
//         assertNotNull(fs.createFile("two.kt"))
//         assertNotNull(fs.createFile("three.java"))
//     
//         val result = fs.resolveFiles(
//             listOf("."),
//             setOf("txt", "kt")
//         )
//     
//         assertEquals(2, result.size)
//     
//         assertTrue(
//             result.any { it.relativePath == "one.txt" }
//         )
//     
//         assertTrue(
//             result.any { it.relativePath == "two.kt" }
//         )
//     
//         assertFalse(
//             result.any { it.relativePath == "three.java" }
//         )
// 
//         deleteAllFiles(root.toString())
//     }
// 
//     @Test
//     fun findFilesInRoot() {
//         val root = ROOT1
//     
//         fs.changeSelectedDirectory(root)
// 
//         fs.listFiles(root.toString()).forEach	{
//         	fs.delete(it)
//         }
//     
//         assertNotNull(fs.createFile("one.txt"))
//         assertNotNull(fs.createFile("two.txt"))
//     
//         val result = fs.findFiles(
//             "",
//             emptySet()
//         )
//     
//         assertEquals(2, result.size)
//     
//         assertTrue(
//             result.all { it.startsWith("content://") }
//         )
// 
//         deleteAllFiles(root.toString())
//     }
// 
//     @Test
//     fun copySafToNormalFileSystemByStream() {
//         val root = ROOT1
//     
//         fs.changeSelectedDirectory(root)
//     
//         assertTrue(fs.writeText("saf-source.txt", "Hello from SAF"))
//     
//         val normalDir = File(context.filesDir, "saf-copy-test").apply {
//             deleteRecursively()
//             mkdirs()
//         }
//     
//         val destination = File(normalDir, "copied.txt").absolutePath
//     
//         val result = fs.copyByStream(
//             "saf-source.txt",
//             destination
//         )
//     
//         assertNotNull(result)
//     
//         assertTrue(fs.exists("saf-source.txt"))
//         assertTrue(fs.exists(destination))
//     
//         assertEquals(
//             "Hello from SAF",
//             fs.readText(destination)
//         )
//     
//         normalDir.deleteRecursively()
//         deleteAllFiles(root.toString())
//     }
// 
//     @Test
//     fun copyNormalFileSystemToSafByStream() {
//         val root = ROOT1
//     
//         fs.changeSelectedDirectory(root)
//     
//         val source = File(context.filesDir, "normal-source.txt").apply	{
//         	createNewFile()
//         	writeText("Hello from normal filesystem")
//         }
// 
//     
//         assertNotNull(
//             fs.createDirectory("destination")
//         )
//         // fail("Not again: ${fs.exists("destination")}")
//     
//         val result = fs.copyByStream(
//             source.absolutePath,
//             "destination"
//         )
//     
//         assertNotNull(result)
//     
//         assertTrue(source.exists())
//         assertTrue(fs.exists("destination/normal-source.txt"))
//     
//         assertEquals(
//             "Hello from normal filesystem",
//             fs.readText("destination/normal-source.txt")
//         )
//     
//         source.delete()
//         deleteAllFiles(root.toString())
//     }
// 
//     @Test
//     fun moveSafToNormalFileSystemByStream() {
//         val root = ROOT1
//     
//         fs.changeSelectedDirectory(root)
//     
//         assertTrue(fs.writeText("saf-source.txt", "Move from SAF"))
//     
//         val normalDir = File(context.filesDir, "saf-move-test").apply {
//             deleteRecursively()
//             mkdirs()
//         }
//     
//         val destination = File(normalDir, "moved.txt").absolutePath
//     
//         val result = fs.moveByStream(
//             "saf-source.txt",
//             destination
//         )
//     
//         assertNotNull(result)
//     
//         assertFalse(fs.exists("saf-source.txt"))
//         assertTrue(File(destination).exists())
//     
//         assertEquals(
//             "Move from SAF",
//             File(destination).readText()
//         )
//     
//         normalDir.deleteRecursively()
//         deleteAllFiles(root.toString())
//     }
// 
//     @Test
//     fun moveNormalFileSystemToSafByStream() {
//         val root = ROOT1
//     
//         fs.changeSelectedDirectory(root)
//     
//         val source = File(context.filesDir, "normal-source.txt")
//         source.writeText("Move to SAF")
//     
//         assertNotNull(
//             fs.createDirectory("destination")
//         )
//     
//         val result = fs.moveByStream(
//             source.absolutePath,
//             "destination"
//         )
//     
//         assertNotNull(result)
//     
//         assertFalse(source.exists())
//         assertTrue(fs.exists("destination/normal-source.txt"))
//     
//         assertEquals(
//             "Move to SAF",
//             fs.readText("destination/normal-source.txt")
//         )
// 
//         deleteAllFiles(root.toString())
//     }
// 
//     @Test
//     fun copySafDirectoryToNormalFileSystemByStream() {
//         val root = ROOT1
//     
//         fs.changeSelectedDirectory(root)
//     
//         assertTrue(fs.writeText("source/a.txt", "AAA"))
//         assertTrue(fs.writeText("source/nested/b.txt", "BBB"))
//         assertTrue(fs.writeText("source/nested/deep/c.txt", "CCC"))
// 
//         val destination = File(
//             context.filesDir,
//             "saf-directory-copy"
//         ).apply {
//             deleteRecursively()
//             mkdirs()
//         }
//     
//         val result = fs.copyByStream(
//             "source",
//             destination.absolutePath
//         )
//     
//         assertNotNull(result)
//     
//         assertTrue(fs.exists("${destination.absolutePath}/source/a.txt"))
//         assertTrue(fs.exists("${destination.absolutePath}/source/nested/b.txt"))
//         assertTrue(fs.exists("${destination.absolutePath}/source/nested/deep/c.txt"))
//     
//         assertEquals(
//             "AAA",
//             File(destination, "source/a.txt").readText()
//         )
//     
//         assertEquals(
//             "BBB",
//             File(destination, "source/nested/b.txt").readText()
//         )
//     
//         assertEquals(
//             "CCC",
//             File(destination, "source/nested/deep/c.txt").readText()
//         )
//     
//         deleteAllFiles(root.toString())
//         destination.deleteRecursively()
//     }

		// @Test
		// fun copySafFileBetweenDifferentRoots() {
		//     val source = "$ROOT1||source.txt"
		//     val destination = "$ROOT2||destination.txt"
		// 
		//     assertTrue(
		//         fs.createFile(source) != null
		//     )
		// 
		//     assertTrue(
		//         fs.writeText(source, "Hello from Root1")
		//     )
		// 
		//     val result = fs.copyByStream(
		//         source,
		//         destination
		//     )
		// 
		//     assertNotNull(result)
		// 
		//     assertTrue(
		//         fs.exists(destination)
		//     )
		// 
		//     assertEquals(
		//         "Hello from Root1",
		//         fs.readText(destination)
		//     )
		// 
		//     deleteAllFiles(ROOT1.toString())
		//     deleteAllFiles(ROOT2.toString())
		// }

		@Test
		fun copySafDirectoryBetweenDifferentRootsByStream() {
		    val source = "$ROOT1||source"
		    val destination = "$ROOT2||destination"
		
		    assertTrue(
		        fs.writeText(
		            "$ROOT1||source/a.txt",
		            "AAA"
		        )
		    )
		
		    assertTrue(
		        fs.writeText(
		            "$ROOT1||source/nested/b.txt",
		            "BBB"
		        )
		    )
		
		    assertTrue(
		        fs.createDirectory(destination) != null
		    )
		
		    val result = fs.copyByStream(
		        source,
		        destination
		    )
		
		    assertNotNull(result)

		    assertEquals(
		        "AAA",
		        fs.readText("$ROOT2||destination/source/a.txt")
		    )
		
		    assertEquals(
		        "BBB",
		        fs.readText("$ROOT2||destination/source/nested/b.txt")
		    )
		
		    deleteAllFiles(ROOT1.toString())
		    deleteAllFiles(ROOT2.toString())
		}

    private fun deleteAllFiles(uri: String)	{
    	fs.listFiles(uri).forEach	{
    		fs.delete(it)
    	}
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

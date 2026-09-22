package io.github.sifisofakude.filesystem

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileOperationTest {

		val fs = FileSystems.current

    @Test
    fun fileCreationAndExistence() {
        val file = FileOperation("test.txt")

        assertFalse(file.exists())

        val created = file.createNewFile()

        assertNotNull(created)
        assertTrue(file.exists())
        assertTrue(file.isFile())
        assertFalse(file.isDirectory())

        file.delete()

        assertFalse(file.exists())
    }

    @Test
    fun directoryCreation() {
        val directory = FileOperation("parent/child")

        assertFalse(directory.mkdir())

        val created = directory.mkdirs()
        
        assertTrue(created)
        assertTrue(directory.exists())
        assertTrue(directory.isDirectory())

        directory.parent?.let	{
        	it.delete()
        }

        assertFalse(directory.exists())
    }

    @Test
    fun writeAndReadText() {
        val file = FileOperation("hello.txt")

        assertTrue(
            file.writeText("Hello, filesystem!")
        )

        assertTrue(file.exists())
        assertEquals(
            "Hello, filesystem!",
            file.readText()
        )

        file.delete()
    }

    @Test
    fun appendText() {
        val file = FileOperation("append.txt")

        assertTrue(file.writeText("Hello"))
        assertTrue(file.writeText(" World",true))

        assertEquals(
            "Hello World",
            file.readText()
        )

        file.delete()
    }

    @Test
    fun listFiles() {
        val directory = FileOperation("directory")

        directory.mkdir()
        
        fs.createFile("directory/a.txt")
        fs.createFile("directory/b.txt")

        val files = directory.listFiles()

        assertEquals(2, files.size)
        assertTrue(files.any { it.name == "a.txt" })
        assertTrue(files.any { it.name == "b.txt" })

        directory.delete()
    }

    @Test
    fun copyFile() {
        val destination = FileOperation("destination")
        destination.mkdir()
        
        val source = FileOperation("source.txt")
        source.createNewFile()
        source.writeText("Hello")

        val copied = source.copy(
            destination
        )

        assertNotNull(copied)
        assertTrue(copied.exists())
        assertEquals(
            "Hello",
            copied.readText()
        )

        assertTrue(source.exists())

        source.delete()
        destination.delete()
    }

    @Test
    fun moveFile() {
        val destination = FileOperation("destination")
        destination.mkdir()

        val source = FileOperation("source.txt")
        source.createNewFile()
        source.writeText("Hello")

        val moved = source.move(
            destination
        )

        assertNotNull(moved)

        assertFalse(source.exists())
        assertTrue(moved.exists())

        assertEquals(
            "Hello",
            moved.readText()
        )

        destination.delete()
    }

    @Test
    fun copyDirectoryRecursively() {
        fs.createDirectory("source")
        fs.createDirectory("source/sub")

        fs.writeText(
            "source/a.txt",
            "A"
        )

        fs.writeText(
            "source/sub/b.txt",
            "B"
        )

				val source = FileOperation("source")
        val copied = source.copy(
            "destination"
        )

        assertNotNull(copied)

        assertTrue(
            fs.exists("destination/a.txt")
        )

        assertTrue(
            fs.exists("source/sub/b.txt")
        )

        assertEquals(
            "A",
            fs.readText("destination/a.txt")
        )

        assertEquals(
            "B",
            fs.readText("source/sub/b.txt")
        )

        fs.delete("source")
        fs.delete("destination")
    }

    @Test
    fun moveDirectoryRecursively() {
        fs.createDirectory("source")
        fs.createDirectory("source/sub")

        fs.writeText(
            "source/a.txt",
            "A"
        )

        fs.writeText(
            "source/sub/b.txt",
            "B"
        )

        val moved = FileOperation("source").move(
            "destination"
        )

        assertNotNull(moved)

        assertFalse(fs.exists("source"))

        assertTrue(
            fs.exists("destination/a.txt")
        )

        assertTrue(
            fs.exists("destination/sub/b.txt")
        )

        assertEquals(
            "A",
            fs.readText("destination/a.txt")
        )

        assertEquals(
            "B",
            fs.readText("destination/sub/b.txt")
        )

        fs.delete("destination")
    }

    @Test
    fun metadata() {
        val file = FileOperation("metadata.txt")

        file.writeText("12345")

        assertEquals(
            "metadata.txt",
            file.name
        )

        assertEquals(
            "metadata",
            file.name.substringBeforeLast(".")
        )

        assertEquals(
            "txt",
            file.extension
        )

        assertEquals(
            5L,
            file.length
        )

        assertTrue(
            file.lastModified > 0
        )

        file.delete()
    }
    
    @Test
    fun copyFileToExplicitDestination() {
        fs.createDirectory("destination")
        fs.writeText("source.txt", "Hello")
    
        val destination = FileOperation("destination/copied.txt")
        assertFalse(destination.exists())
    
        val copied = FileOperation("source.txt").copy(
            destination
        )
    
        assertTrue(destination.exists())
        assertEquals(
            "Hello",
            destination.readText()
        )
    
        // Source should still exist after copy.
        assertTrue(fs.exists("source.txt"))
    
        fs.delete("source.txt")
        fs.delete("destination")
    }
}

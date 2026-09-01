package io.github.sifisofakude.filesystem

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FileSystemUtilTest {

    private val fs: FileSystemUtil = FileSystems.current

    @Test
    fun fileCreationAndExistence() {
        val file = "test.txt"

        assertFalse(fs.exists(file))

        val created = fs.createFile(file)

        assertNotNull(created)
        assertTrue(fs.exists(file))
        assertTrue(fs.isFile(file))
        assertFalse(fs.isDirectory(file))

        fs.delete(file)

        assertFalse(fs.exists(file))
    }

    @Test
    fun directoryCreation() {
        val directory = "parent/child"

        val created = fs.createDirectory(directory)

        assertNotNull(created)
        assertTrue(fs.exists(directory))
        assertTrue(fs.isDirectory(directory))

        fs.delete("parent")

        assertFalse(fs.exists("parent"))
    }

    @Test
    fun writeAndReadText() {
        val file = "hello.txt"

        assertTrue(
            fs.writeText(file, "Hello, filesystem!")
        )

        assertTrue(fs.exists(file))
        assertEquals(
            "Hello, filesystem!",
            fs.readText(file)
        )

        fs.delete(file)
    }

    @Test
    fun appendText() {
        val file = "append.txt"

        assertTrue(fs.writeText(file, "Hello"))
        assertTrue(fs.appendText(file, " World"))

        assertEquals(
            "Hello World",
            fs.readText(file)
        )

        fs.delete(file)
    }

    @Test
    fun listFiles() {
        fs.createDirectory("directory")
        fs.createFile("directory/a.txt")
        fs.createFile("directory/b.txt")

        val files = fs.listFiles("directory")

        assertEquals(2, files.size)
        assertTrue(files.any { fs.getName(it) == "a.txt" })
        assertTrue(files.any { fs.getName(it) == "b.txt" })

        fs.delete("directory")
    }

    @Test
    fun findFilesRecursively() {
        fs.createDirectory("src")
        fs.createDirectory("src/main")
        fs.createDirectory("src/test")

        fs.createFile("src/main/Main.kt")
        fs.createFile("src/main/Utils.kt")
        fs.createFile("src/test/Test.kt")
        fs.createFile("src/readme.txt")

        val kotlinFiles = fs.findFiles(
            "src",
            setOf("kt")
        )

        assertEquals(3, kotlinFiles.size)

        assertTrue(
            kotlinFiles.all {
                fs.getExtension(it) == "kt"
            }
        )

        fs.delete("src")
    }

    @Test
    fun resolveFilesPreservesRelativePaths() {
        fs.createDirectory("project")
        fs.createDirectory("project/src")
        fs.createDirectory("project/src/main")

        fs.createFile("project/src/main/Main.kt")
        fs.createFile("project/src/main/Utils.kt")

        val files = fs.resolveFiles(
            listOf("project"),
            setOf("kt")
        )

        assertEquals(2, files.size)

        assertTrue(
            files.any {
                it.relativePath == "src/main/Main.kt"
            }
        )

        assertTrue(
            files.any {
                it.relativePath == "src/main/Utils.kt"
            }
        )

        fs.delete("project")
    }

    @Test
    fun copyFile() {
        fs.createDirectory("destination")
        fs.writeText("source.txt", "Hello")

        val copied = fs.copy(
            "source.txt",
            "destination"
        )

        assertNotNull(copied)
        assertTrue(fs.exists(copied))
        assertEquals(
            "Hello",
            fs.readText(copied)
        )

        assertTrue(fs.exists("source.txt"))

        fs.delete("source.txt")
        fs.delete("destination")
    }

    @Test
    fun moveFile() {
        fs.createDirectory("destination")
        fs.writeText("source.txt", "Hello")

        val moved = fs.move(
            "source.txt",
            "destination"
        )

        assertNotNull(moved)

        assertFalse(fs.exists("source.txt"))
        assertTrue(fs.exists(moved))

        assertEquals(
            "Hello",
            fs.readText(moved)
        )

        fs.delete("destination")
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

        val copied = fs.copy(
            "source",
            "destination"
        )

        assertNotNull(copied)

        assertTrue(
            fs.exists("destination/source/a.txt")
        )

        assertTrue(
            fs.exists("destination/source/sub/b.txt")
        )

        assertEquals(
            "A",
            fs.readText("destination/source/a.txt")
        )

        assertEquals(
            "B",
            fs.readText("destination/source/sub/b.txt")
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

        val moved = fs.move(
            "source",
            "destination"
        )

        assertNotNull(moved)

        assertFalse(fs.exists("source"))

        assertTrue(
            fs.exists("destination/source/a.txt")
        )

        assertTrue(
            fs.exists("destination/source/sub/b.txt")
        )

        assertEquals(
            "A",
            fs.readText("destination/source/a.txt")
        )

        assertEquals(
            "B",
            fs.readText("destination/source/sub/b.txt")
        )

        fs.delete("destination")
    }

    @Test
    fun metadata() {
        val file = "metadata.txt"

        fs.writeText(file, "12345")

        assertEquals(
            "metadata.txt",
            fs.getName(file)
        )

        assertEquals(
            "metadata",
            fs.getName(file).substringBeforeLast(".")
        )

        assertEquals(
            "txt",
            fs.getExtension(file)
        )

        assertEquals(
            5L,
            fs.size(file)
        )

        assertTrue(
            fs.lastModified(file) > 0
        )

        fs.delete(file)
    }

    @Test
    fun combinePath() {
        assertEquals(
            "parent/child",
            fs.combinePath("parent", "child")
        )

        assertEquals(
            "parent/child",
            fs.combinePath("parent/", "child")
        )

        assertEquals(
            "parent/child",
            fs.combinePath("parent", "/child")
        )
    }

    @Test
    fun copyFileToExplicitDestination() {
        fs.createDirectory("destination")
        fs.writeText("source.txt", "Hello")
    
        val destination = "destination/copied.txt"
    
        val copied = fs.copy(
            "source.txt",
            destination
        )
    
        assertEquals(destination, copied)
        assertTrue(fs.exists(destination))
        assertEquals(
            "Hello",
            fs.readText(destination)
        )
    
        // Source should still exist after copy.
        assertTrue(fs.exists("source.txt"))
    
        fs.delete("source.txt")
        fs.delete("destination")
    }
}

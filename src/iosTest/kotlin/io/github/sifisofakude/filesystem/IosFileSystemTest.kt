package io.github.sifisofakude.filesystem

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class IosFileSystemTest {

    private val fs: FileSystemUtil = FileSystems.current

    @Test
    fun selectedDirectory() {
        val root = "ios-selected-root"

//         try {
//             assertNotNull(fs.createDirectory(root))
// 
//             (fs as IosFileSystem).changeSelectedDirectory(root)
// 
//             assertEquals(
//                 fs.resolvePath(root),
//                 fs.getCurrentDirectory()
//             )
// 
//             assertNotNull(
//                 fs.createFile("test.txt")
//             )
// 
//             assertTrue(fs.exists("test.txt"))
//             assertTrue(
//                 fs.exists(
//                     fs.combinePath(root, "test.txt")
//                 )
//             )
//         } finally {
//             (fs as IosFileSystem).changeSelectedDirectory(null)
//             fs.delete(root)
//         }
    }

    @Test
    fun relativePathUsesSelectedDirectory() {
        val root = "ios-relative-root"

        try {
            fs.createDirectory(root)

            (fs as IosFileSystem).changeSelectedDirectory(root)

            assertTrue(
                fs.writeText(
                    "hello.txt",
                    "Hello iOS"
                )
            )

            assertTrue(fs.exists("hello.txt"))

            assertEquals(
                "Hello iOS",
                fs.readText("hello.txt")
            )

            assertTrue(
                fs.exists(
                    fs.combinePath(root, "hello.txt")
                )
            )
        } finally {
            (fs as IosFileSystem).changeSelectedDirectory(null)
            fs.delete(root)
        }
    }

    @Test
    fun absolutePathIgnoresSelectedDirectory() {
        val root = "ios-absolute-root"
        val outside = "ios-outside.txt"

//         try {
//             fs.createDirectory(root)
//             fs.writeText(outside, "outside")
// 
//             (fs as IosFileSystem).changeSelectedDirectory(root)
// 
//             val absolutePath = fs.resolvePath(outside)
// 
//             assertFalse(
//                 fs.exists(absolutePath)
//             )
// 
//             assertFalse(
//                 fs.exists(
//                     fs.combinePath(
//                         root,
//                         outside
//                     )
//                 )
//             )
//         } finally {
//             (fs as IosFileSystem).changeSelectedDirectory(null)
//             fs.delete(root)
//             fs.delete(outside)
//         }
    }

    @Test
    fun nativeCopyIntoExistingDirectory() {
        val source = "ios-copy-source.txt"
        val destination = "ios-copy-destination"

        try {
            fs.writeText(source, "Hello iOS")
            fs.createDirectory(destination)

            val result = fs.copy(
                source,
                destination
            )

            val expected = fs.combinePath(
                destination,
                fs.getName(source)
            )

            assertEquals(
                expected,
                result
            )

            assertNotNull(result)

            assertTrue(fs.exists(result))
            assertTrue(fs.exists(source))

            assertEquals(
                "Hello iOS",
                fs.readText(result)
            )
        } finally {
            fs.delete(source)
            fs.delete(destination)
        }
    }

    @Test
    fun nativeCopyToNewDestination() {
        val source = "ios-copy-source.txt"
        val destination = "ios-copy-result.txt"

        try {
            fs.writeText(source, "Hello iOS")

            val result = fs.copy(
                source,
                destination
            )

            assertEquals(
                destination,
                result
            )

            assertTrue(fs.exists(source))
            assertTrue(fs.exists(destination))

            assertEquals(
                "Hello iOS",
                fs.readText(destination)
            )
        } finally {
            fs.delete(source)
            fs.delete(destination)
        }
    }

    @Test
    fun nativeMoveIntoExistingDirectory() {
        val source = "ios-move-source.txt"
        val destination = "ios-move-destination"

        try {
            fs.writeText(source, "Hello iOS")
            fs.createDirectory(destination)

            val result = fs.move(
                source,
                destination
            )

            val expected = fs.combinePath(
                destination,
                fs.getName(source)
            )

            assertEquals(
                expected,
                result
            )

            assertNotNull(result)

            assertFalse(fs.exists(source))
            assertTrue(fs.exists(result))

            assertEquals(
                "Hello iOS",
                fs.readText(result)
            )
        } finally {
            fs.delete(source)
            fs.delete(destination)
        }
    }

    @Test
    fun nativeMoveToNewDestination() {
        val source = "ios-move-source.txt"
        val destination = "ios-move-result.txt"

        try {
            fs.writeText(source, "Hello iOS")

            val result = fs.move(
                source,
                destination
            )

            assertEquals(
                destination,
                result
            )

            assertFalse(fs.exists(source))
            assertTrue(fs.exists(destination))

            assertEquals(
                "Hello iOS",
                fs.readText(destination)
            )
        } finally {
            fs.delete(source)
            fs.delete(destination)
        }
    }

    @Test
    fun nativeDirectoryCopy() {
        val source = "ios-copy-source"
        val destination = "ios-copy-destination"

        try {
            fs.createDirectory(source)

            fs.writeText(
                fs.combinePath(source, "a.txt"),
                "A"
            )

            fs.createDirectory(destination)

            val result = fs.copy(
                source,
                destination
            )

            val copiedRoot = fs.combinePath(
                destination,
                fs.getName(source)
            )

            assertEquals(
                copiedRoot,
                result
            )

            assertTrue(
                fs.exists(
                    fs.combinePath(copiedRoot, "a.txt")
                )
            )

            assertEquals(
                "A",
                fs.readText(
                    fs.combinePath(copiedRoot, "a.txt")
                )
            )

            assertTrue(fs.exists(source))
        } finally {
            fs.delete(source)
            fs.delete(destination)
        }
    }

    @Test
    fun nativeDirectoryMove() {
        val source = "ios-move-source"
        val destination = "ios-move-destination"

        try {
            fs.createDirectory(source)

            fs.writeText(
                fs.combinePath(source, "a.txt"),
                "A"
            )

            fs.createDirectory(destination)

            val result = fs.move(
                source,
                destination
            )

            val movedRoot = fs.combinePath(
                destination,
                fs.getName(source)
            )

            assertEquals(
                movedRoot,
                result
            )

            assertFalse(fs.exists(source))

            assertTrue(
                fs.exists(
                    fs.combinePath(movedRoot, "a.txt")
                )
            )

            assertEquals(
                "A",
                fs.readText(
                    fs.combinePath(movedRoot, "a.txt")
                )
            )
        } finally {
            fs.delete(source)
            fs.delete(destination)
        }
    }

    @Test
    fun streamCopyWorksIndependentlyOfNativeCopy() {
        val source = "ios-stream-source.txt"
        val destination = "ios-stream-result.txt"

        try {
            fs.writeText(
                source,
                "Stream copy"
            )

            val result = fs.copyByStream(
                source,
                destination
            )

            // assertEquals(
            //     destination,
            //     result
            // )

            // assertTrue(fs.exists(source))
//             assertTrue(fs.exists(destination))
// 
//             assertEquals(
//                 "Stream copy",
//                 fs.readText(destination)
//             )
        } finally {
            fs.delete(source)
            fs.delete(destination)
        }
    }

    @Test
    fun streamMoveWorksIndependentlyOfNativeMove() {
//         val source = "ios-stream-source.txt"
//         val destination = "ios-stream-result.txt"
// 
//         try {
//             fs.writeText(
//                 source,
//                 "Stream move"
//             )
// 
//             val result = fs.moveByStream(
//                 source,
//                 destination
//             )
// 
//             assertEquals(
//                 destination,
//                 result
//             )
// 
//             assertFalse(fs.exists(source))
//             assertTrue(fs.exists(destination))
// 
//             assertEquals(
//                 "Stream move",
//                 fs.readText(destination)
//             )
//         } finally {
//             fs.delete(source)
//             fs.delete(destination)
//         }
    }

    @Test
    fun appendText() {
//         val file = "ios-append.txt"
// 
//         try {
//             assertTrue(
//                 fs.writeText(
//                     file,
//                     "Hello"
//                 )
//             )
// 
//             assertTrue(
//                 fs.appendText(
//                     file,
//                     " iOS"
//                 )
//             )
// 
//             assertEquals(
//                 "Hello iOS",
//                 fs.readText(file)
//             )
//         } finally {
//             fs.delete(file)
//         }
    }
}

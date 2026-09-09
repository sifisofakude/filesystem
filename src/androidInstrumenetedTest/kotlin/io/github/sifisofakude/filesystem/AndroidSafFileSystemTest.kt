package io.github.sifisofakude.filesystem

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AndroidSafFileSystemTest {

    private val context =
        ApplicationProvider.getApplicationContext<Context>()

    private val fs = AndroidSafFileSystem(context)

    @Test
    fun detectsSafUri() {
        assertTrue(
            fs.isSafUri(
                "content://com.android.externalstorage.documents/tree/primary%3ADocuments"
            )
        )

        assertFalse(
            fs.isSafUri(
                "/storage/emulated/0/Documents"
            )
        )

        assertFalse(
            fs.isSafUri(
                "relative/path"
            )
        )
    }

    @Test
    fun conventionalPathsStillWork() {
        val root =
            context.filesDir
                .resolve("filesystem-test")
                .absolutePath

        val file =
            fs.combinePath(
                root,
                "test.txt"
            )

        try {
            assertNotNull(
                fs.createDirectory(root)
            )

            assertTrue(
                fs.writeText(
                    file,
                    "Hello Android"
                )
            )

            assertTrue(
                fs.exists(file)
            )

            assertTrue(
                fs.isFile(file)
            )

            assertTrue(
                fs.readText(file) ==
                    "Hello Android"
            )
        } finally {
            fs.delete(root)
        }
    }

    @Test
    fun conventionalCopyWorks() {
        val root =
            context.filesDir
                .resolve("filesystem-copy-test")
                .absolutePath

        val source =
            fs.combinePath(
                root,
                "source.txt"
            )

        val destination =
            fs.combinePath(
                root,
                "destination.txt"
            )

        try {
            fs.createDirectory(root)

            assertTrue(
                fs.writeText(
                    source,
                    "Hello Android"
                )
            )

            val copied =
                fs.copy(
                    source,
                    destination
                )

            assertNotNull(copied)

            assertTrue(
                fs.exists(destination)
            )

            assertTrue(
                fs.exists(source)
            )

            assertTrue(
                fs.readText(destination) ==
                    "Hello Android"
            )
        } finally {
            fs.delete(root)
        }
    }

    @Test
    fun conventionalMoveWorks() {
        val root =
            context.filesDir
                .resolve("filesystem-move-test")
                .absolutePath

        val source =
            fs.combinePath(
                root,
                "source.txt"
            )

        val destination =
            fs.combinePath(
                root,
                "destination.txt"
            )

        try {
            fs.createDirectory(root)

            assertTrue(
                fs.writeText(
                    source,
                    "Hello Android"
                )
            )

            val moved =
                fs.move(
                    source,
                    destination
                )

            assertNotNull(moved)

            assertFalse(
                fs.exists(source)
            )

            assertTrue(
                fs.exists(destination)
            )

            assertTrue(
                fs.readText(destination) ==
                    "Hello Android"
            )
        } finally {
            fs.delete(root)
        }
    }

    @Test
    fun safUriIsNotRelative() {
        val uri =
            "content://com.android.externalstorage.documents/tree/primary%3ADocuments"

        assertFalse(
            fs.isRelative(uri)
        )
    }

    @Test
    fun conventionalRelativePathIsRelative() {
        assertTrue(
            fs.isRelative(
                "documents/test.txt"
            )
        )
    }

    @Test
    fun absolutePathIsNotRelative() {
        assertFalse(
            fs.isRelative(
                "/storage/emulated/0/Documents/test.txt"
            )
        )
    }
}

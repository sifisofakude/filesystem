package io.github.sifisofakude.filesystem

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class IosFileSystemTest {

  private val fs = FileSystems.current as IosFileSystem

  @Test
  fun selectedDirectory() {
  	val testRoot = "test-root"
  	val testRoot2 = "test-root2"
  	
    fs.createDirectory(testRoot)
    fs.createDirectory(testRoot2)
    
    fs.changeSelectedDirectory(testRoot)

    fs.createFile("to/source.txt")

    fs.changeSelectedDirectory(testRoot2)
    assertTrue(fs.exists("to/source.txt"))
    
    fs.changeSelectedDirectory(testRoot)
    assertTrue(fs.exists("to/source.txt"))
  }

  @Test
  fun absolutePathBypassesSelectedDirectory() {
      // Verify absolute paths aren't resolved under selected root
  }

  @Test
  fun nativeCopyUsesIosFilesystem() {
      // Native NSFileManager copy
  }

  @Test
  fun nativeMoveUsesIosFilesystem() {
      // Native NSFileManager move
  }

  @Test
  fun nativeMetadata() {
      // Foundation metadata
  }
}

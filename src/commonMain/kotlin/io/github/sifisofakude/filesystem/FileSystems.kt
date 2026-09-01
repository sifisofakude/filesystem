package io.github.sifisofakud.filesystem

 /**
  * Provides access to the filesystem implementation for the current platform.
  *
  * The platform-specific implementation determines which [FileSystemUtil]
  * is exposed through [current].
  *
  * This allows common code to perform filesystem operations without directly
  * depending on a platform-specific implementation.
  *
  * The actual filesystem implementation is supplied by each supported
  * platform.
  *
  * Example:
  *
  * ```kotlin
  * val file = FileOperation("example.txt")
  * file.writeText("Hello, world!")
  * ```
  *
  * @see FileSystemUtil
  * @see FileOperation
  */
 expect object FileSystems {
     /**
      * Returns the filesystem implementation provided by the current platform.
      *
      * The returned implementation may represent a conventional filesystem,
      * an Android Storage Access Framework filesystem, or another
      * platform-specific storage backend.
      */
     val current: FileSystemUtil
 }

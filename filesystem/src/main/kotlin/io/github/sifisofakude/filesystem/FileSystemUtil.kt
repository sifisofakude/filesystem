package io.github.sifisofakude.filesystem

import java.io.InputStream
import java.io.OutputStream

/**
 * Represents a file together with its relative path and input stream.
 *
 * This class is typically produced by [FileSystemUtil.resolveFiles] when
 * resolving files from paths or directories.
 *
 * @property relativePath the path of the file relative to the source directory
 * @property stream the input stream used to read the file contents
 *
 * @since 0.1.0
 */
data class FileSource(
	val relativePath: String,
	val stream: InputStream
)


/**
 * Provides an abstraction over filesystem operations.
 *
 * Implementations may target different environments such as:
 *
 * - JVM desktop environments
 * - Android storage systems
 * - Virtual or in-memory filesystems
 *
 * This interface allows tools and libraries to interact with files without
 * depending on a specific platform implementation.
 *
 * For example:
 *
 * ```kotlin
 * val fs: FileSystemUtil = JvmFileSystem()
 * val files = fs.resolveFiles(listOf("src"), setOf("java", "kt"))
 * ```
 *
 * @since 0.1.0
 */
interface FileSystemUtil	{
	/**
   * Returns the current working directory.
   *
   * @return the current directory path or null if unavailable
   */
	fun getCurrentDirectory(): String?

	/**
   * Creates a directory if it does not exist.
   *
   * If the path represents a file, its parent directory may be created.
   *
   * @param path the directory path
   * @return the resulting directory path or null if creation failed
   */
	fun createDirectory(path: String): String?

	/**
   * Opens an output stream for writing to a file.
   *
   * Implementations may automatically create missing parent directories.
   *
   * @param path the file path
   * @return an output stream for writing, or null if the stream could not be opened
   */
	fun openOutputStream(path: String): OutputStream?

	/**
   * Recursively finds files inside a directory.
   *
   * If extensions are provided, only files matching those extensions
   * will be returned.
   *
   * @param directory the directory to search
   * @param extensions allowed file extensions (empty set means all files)
   * @return a list of absolute file paths
   */
	fun findFiles(directory: String,extensions: Set<String>): List<String>

	/**
   * Resolves a collection of file inputs into readable [FileSource] objects.
   *
   * Supported input types include:
   *
   * - String paths
   * - File objects (implementation dependent)
   *
   * If a directory is provided, implementations should recursively
   * discover files matching the given extensions.
   *
   * @param inputFiles files or paths to resolve
   * @param extensions allowed file extensions (empty set means all files)
   * @return a list of resolved [FileSource] objects
   */
	fun resolveFiles(inputFiles: List<Any>,extensions: Set<String>): List<FileSource>
}

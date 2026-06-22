package io.github.sifisofakude.filesystem

import java.io.InputStream
import java.io.OutputStream

/**
 * Represents a resolved file together with its logical and physical paths.
 *
 * This class is typically produced by [FileSystemUtil.resolveFiles] when
 * resolving files from paths or directories.
 *
 * `relativePath` preserves the file's location relative to its source
 * directory and is useful when generating archives or compiling sources
 * where directory structure must be retained.
 *
 * `absolutePath` identifies the file's resolved location and can be used
 * with [FileSystemUtil.openInputStream] or other filesystem operations to
 * access its contents.
 *
 * @property relativePath the path relative to the source directory
 * @property absolutePath the resolved filesystem path
 *
 * @since 0.2.0
 */
data class FileSource(
	val relativePath: String,
	val absolutePath: String,
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
	 * Creates a file if it does not already exist.
	 *
	 * Implementations may automatically create missing parent directories.
	 *
	 * @param path the file path
	 * @return the resulting file path, or null if creation failed
	 */
	fun createFile(path: String): String?

	/**
	 * Copies one or more files or directories to the specified destination.
	 *
	 * If a source is a directory, its contents should be copied recursively.
	 * Existing files may be replaced depending on the value of [overwrite].
	 *
	 * @param src the source files or directories to copy
	 * @param dst the destination path
	 * @param overwrite whether existing files should be overwritten
	 */
	fun copy(
		src: List<String>,
		dst: String,overwrite: Boolean = true
	)

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
	 * Opens an input stream for reading from a file.
	 *
	 * @param path the file path
	 * @return an input stream for reading, or null if the stream could not be opened
	 */
	fun openInputStream(path: String): InputStream?

	/**
	 * Reads the contents of a file as text.
	 *
	 * @param path the file path
	 * @return the file contents, or null if the file could not be read
	 */
	fun readText(path: String): String?

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
	fun findFiles(
		directory: String,
		extensions: Set<String> = emptySet()
	): List<String>

	/**
	 * Lists the immediate children of a directory.
	 *
	 * Unlike [findFiles], this method does not recursively traverse
	 * subdirectories.
	 *
	 * @param path the directory path
	 * @return a list of child paths, or an empty list if the directory is empty
	 *         or cannot be read
	 */
	fun listFiles(path: String): List<String>

	/**
	 * Returns whether a file or directory exists.
	 *
	 * @param path the path to test
	 * @return `true` if the path exists, otherwise `false`
	 */
	fun exists(path: String): Boolean

	/**
	 * Deletes a file or directory.
	 *
	 * If the path refers to a directory, implementations may recursively
	 * delete its contents.
	 *
	 * @param path the path to delete
	 * @return `true` if the file or directory was successfully deleted,
	 *         otherwise `false`
	 */
	fun delete(path: String): Boolean

	/**
	 * Renames a file or directory.
	 *
	 * @param src the existing path
	 * @param target the new name or target path
	 * @return the resulting path, or null if the operation failed
	 */
	fun rename(src: String, target: String): String?

	/**
	 * Moves a file or directory to a new location.
	 *
	 * @param src the source path
	 * @param dst the destination path
	 * @return the resulting path, or null if the operation failed
	 */
	fun move(src: String, dst: String): String?
	
	/**
	 * Resolves a collection of file inputs into [FileSource] objects.
	 *
	 * If a directory is provided, implementations recursively discover files
	 * matching the given extensions.
	 *
	 * The returned [FileSource] instances contain metadata only. File contents
	 * can be accessed using [openInputStream] with the returned
	 * [FileSource.absolutePath].
	 *
	 * @param inputFiles files or paths to resolve
	 * @param extensions allowed file extensions (empty set means all files)
	 * @return a list of resolved [FileSource] objects
	 */
	fun resolveFiles(
		inputFiles: List<Any>,
		extensions: Set<String> = emptySet()
	): List<FileSource>

	/**
	 * Resolves a path relative to the current working directory.
	 *
	 * If the supplied path is already absolute, it should typically be
	 * returned unchanged.
	 *
	 * @param path the path to resolve
	 * @return the resolved path
	 */
	fun resolvePath(path: String): String

	/**
	 * Returns whether the specified path refers to a regular file.
	 *
	 * @param path the path to test
	 * @return `true` if the path refers to a file, otherwise `false`
	 */
	fun isFile(path: String): Boolean

	/**
	 * Returns whether the specified path refers to a directory.
	 *
	 * @param path the path to test
	 * @return `true` if the path refers to a directory, otherwise `false`
	 */
	fun isDirectory(path: String): Boolean

	/**
	 * Returns the last modification time of a file or directory.
	 *
	 * The returned value is expressed as the number of milliseconds since
	 * the Unix epoch (00:00:00 UTC on 1 January 1970).
	 *
	 * @param path the path to query
	 * @return the last modification time in milliseconds
	 */
	fun lastModified(path: String): Long

	/**
	 * Returns the size of a file in bytes.
	 *
	 * If the specified path refers to a directory, the result is
	 * implementation-defined.
	 *
	 * @param path the path to query
	 * @return the size of the file in bytes
	 */
	fun size(path: String): Long
}

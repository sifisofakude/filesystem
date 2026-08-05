package io.github.sifisofakude.filesystem

import java.io.InputStream
import java.io.OutputStream


/**
 * Provides access to the active filesystem implementation.
 *
 * Applications must assign a [FileSystemUtil] implementation before using
 * [FileOperation].
 *
 * Example:
 *
 * ```kotlin
 * FileSystems.current = JvmFileSystem()
 * ```
 */
object FileSystems	{
	lateinit var current: FileSystemUtil
}

/**
 * Represents a file or directory in the current filesystem.
 *
 * `FileOperation` provides an object-oriented API for interacting with files
 * and directories without exposing platform-specific implementations.
 *
 * The underlying filesystem is provided by [FileSystems.current], allowing the
 * same API to operate on:
 *
 * - JVM files (`java.io.File`)
 * - Android Storage Access Framework (SAF) resources
 *
 * Before creating a [FileOperation], a filesystem implementation must be
 * assigned to [FileSystems.current].
 *
 * Example:
 *
 * ```kotlin
 * FileSystems.current = JvmFileSystem()
 *
 * val file = FileOperation("notes.txt")
 *
 * if (!file.exists()) {
 *     file.createNewFile()
 * }
 *
 * file.writeText("Hello, World!")
 *
 * println(file.readText())
 * ```
 *
 * @property path the filesystem path or URI represented by this object
 *
 * @since 0.4.0
 */
class FileOperation(
	val path: String
)	{
	private val fs
			get() = FileSystems.current

	/**
	 * Returns the resource name.
	 *
	 * For example:
	 *
	 * - `/tmp/file.txt` → `file.txt`
	 * - `content://...` → provider-defined display name
	 */
	val name 
			get() = fs.getName(path)

	/**
	 * Returns the file extension.
	 *
	 * The extension is the substring following the final `.` in the resource
	 * name. If the resource has no extension, an empty string is returned.
	 *
	 * Examples:
	 *
	 * - `Main.kt` → `kt`
	 * - `archive.tar.gz` → `gz`
	 * - `README` → ``
	 * - `.gitignore` → ``
	 */
	val extension
	    get() = name.substringAfterLast('.', "")

	/**
	 * Returns the resource name without its file extension.
	 *
	 * If the resource has no extension, the complete resource name is returned.
	 *
	 * Examples:
	 *
	 * - `Main.kt` → `Main`
	 * - `archive.tar.gz` → `archive.tar`
	 * - `README` → `README`
	 * - `.gitignore` → `.gitignore`
	 */
	val nameWithoutExtension
	    get() = name.substringBeforeLast('.', name)

	/**
	 * Returns the parent directory, or `null` if this resource has no parent.
	 */
	val parent
			get() = fs.getParentFile(path)?.let(::FileOperation)

	/**
	 * Returns the size of the file in bytes.
	 *
	 * Directory size is implementation-defined.
	 */
	val length
			get() = fs.size(path)

	/**
	 * Returns the last modification timestamp expressed as milliseconds since
	 * the Unix epoch.
	 */
	val lastModified
			get() = fs.lastModified(path)

	/**
	 * Returns the canonical or normalized path represented by this object.
	 *
	 * The returned value may differ from [path] if the underlying filesystem
	 * performs path normalization.
	 */
	val absolutePath
			get() = fs.resolvePath(path)

	/**
	 * Creates this directory.
	 *
	 * Unlike [mkdirs], this method succeeds only if the parent directory already
	 * exists.
	 *
	 * @return `true` if the directory was created successfully
	 */
	fun mkdir(): Boolean	{
		val parent = fs.getParentFile(path) ?: return false

		if(!fs.exists(parent))	{
			return false
		}
		return fs.createDirectory(path) != null
	}

	/**
	 * Creates this directory together with any missing parent directories.
	 *
	 * @return `true` if the directory was created successfully
	 */
	fun mkdirs(): Boolean	{
		if(!fs.exists(path))	{
			return fs.createDirectory(path) != null
		}
		return false
	}

	
	fun isFile(): Boolean = fs.isFile(path)
	
	fun isDirectory(): Boolean = fs.isDirectory(path)

	/**
	 * Copies this file or directory.
	 *
	 * If [dst] is an existing directory, this resource is copied into it.
	 *
	 * Otherwise, [dst] is treated as the destination file or directory path.
	 *
	 * @param dst destination path
	 * @return the copied resource, or `null` if the operation failed
	 */
	fun copy(dst: String): FileOperation? = fs.copy(path,dst)?.let(::FileOperation)

	/**
	 * Moves this file or directory.
	 *
	 * @param dst destination path
	 * @return the moved resource, or `null` if the operation failed
	 */
	fun move(dst: String): FileOperation? = fs.move(path,dst)?.let(::FileOperation)

	/**
	 * Reads this file as UTF-8 text.
	 *
	 * @return the file contents, or `null` if the file could not be read
	 */
	fun readText(): String? = fs.readText(path)

	/**
	 * Writes text to this file.
	 *
	 * The file is created automatically if it does not already exist.
	 *
	 * @return `true` if the write completed successfully
	 */
	fun writeText(text: String): Boolean = fs.writeText(path,text)

	/**
	 * Creates a new empty file.
	 *
	 * @return `true` if the file was created
	 */
	fun createNewFile(): Boolean = fs.createFile(path) != null

	/**
	 * Deletes this file or directory.
	 *
	 * @return `true` if deletion succeeded
	 */
	fun delete(): Boolean = fs.delete(path)

	/**
	 * Opens this file for reading.
	 *
	 * The caller is responsible for closing the returned stream.
	 */
	fun inputStream(): InputStream? = fs.openInputStream(path)

	/**
	 * Opens this file for writing.
	 *
	 * The caller is responsible for closing the returned stream.
	 */
	fun outputStream(): OutputStream? = fs.openOutputStream(path)

	/**
	 * Lists the immediate children of this directory.
	 *
	 * This method does not traverse subdirectories.
	 *
	 * @return child files and directories
	 */
	fun listFiles(): List<FileOperation> = fs.listFiles(path).map(::FileOperation)

	fun exists(): Boolean = fs.exists(path)

	override fun toString(): String = path
}

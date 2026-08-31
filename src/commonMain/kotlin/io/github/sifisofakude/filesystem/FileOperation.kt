package io.github.sifisofakude.filesystem

import kotlinx.io.Sink
import kotlinx.io.Source

/**
 * Represents a file or directory in the active filesystem.
 *
 * `FileOperation` provides the primary object-oriented API exposed to
 * applications. It delegates filesystem operations to the implementation
 * currently assigned to [FileSystems.current].
 *
 * This allows application code to work with filesystem resources without
 * depending directly on the underlying storage implementation.
 *
 * Depending on the active filesystem, [path] may represent:
 *
 * - a conventional JVM filesystem path,
 * - an Android Storage Access Framework URI,
 * - a relative path resolved against a selected Android SAF directory,
 * - an iOS filesystem path, or
 * - a relative path resolved against a selected iOS directory.
 *
 * @property path path or URI represented by this operation
 * @see FileSystems
 * @see FileSystemUtil
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
	 * Returns the resolved path or URI represented by this resource.
	 *
	 * The result is determined by the active filesystem implementation and may
	 * differ from [path]. For conventional filesystems this may be an absolute
	 * normalized path, while other implementations may return a
	 * filesystem-specific representation.
	 */
	val absolutePath
			get() = fs.resolvePath(path)

	/**
	 * Creates this directory when its parent already exists.
	 *
	 * Unlike [mkdirs], this method does not create missing parent directories.
	 * The operation is delegated to the active filesystem implementation.
	 *
	 * @return `true` if the directory was created successfully, otherwise `false`
	 */
	fun mkdir(): Boolean	{
		val parent = fs.getParentFile(path) ?: return false

		if(!fs.exists(parent))	{
			return false
		}
		return fs.createDirectory(path) != null
	}

	/**
	 * Creates this directory and any missing parent directories.
	 *
	 * The operation is delegated to the active filesystem implementation.
	 *
	 * @return `true` if the directory was created successfully, otherwise `false`
	 */
	fun mkdirs(): Boolean	{
		if(!fs.exists(path))	{
			return fs.createDirectory(path) != null
		}
		return false
	}

	/**
	 * Determines whether this resource is a regular file.
	 *
	 * @return `true` if this resource exists and is a file, otherwise `false`
	 */
	fun isFile(): Boolean = fs.isFile(path)

	/**
	 * Determines whether this resource is a directory.
	 *
	 * @return `true` if this resource exists and is a directory, otherwise `false`
	 */
	fun isDirectory(): Boolean = fs.isDirectory(path)

	/**
	 * Copies this resource to [dst].
	 *
	 * Directories are copied recursively. If [dst] refers to an existing
	 * directory, this resource is copied into that directory.
	 *
	 * The operation is delegated to the active [FileSystemUtil] implementation,
	 * allowing paths and filesystem-specific URIs to be used as destinations.
	 *
	 * @param dst destination path or URI
	 * @param overwrite whether an existing destination may be replaced
	 * @return the resulting resource, or `null` if copying failed
	 */
	fun copy(dst: String, overwrite: Boolean = false): FileOperation? = 
		fs.copy(path,dst,overwrite)?.let(::FileOperation)

	/**
	 * Copies this resource to the destination represented by [dst].
	 *
	 * Directories are copied recursively. If the destination represents an
	 * existing directory, this resource is copied into that directory.
	 *
	 * The operation is delegated to the active [FileSystemUtil] implementation,
	 * allowing the source and destination to use different filesystem
	 * representations when supported.
	 *
	 * @param dst destination resource
	 * @param overwrite whether an existing destination may be replaced
	 * @return the resulting resource, or `null` if copying failed
	 */
	fun copy(dst: FileOperation,overwrite: Boolean = false): FileOperation? = 
		copy(dst.path,overwrite)

	/**
	 * Moves this resource to [dst].
	 *
	 * Files are transferred to the destination and the original file is deleted
	 * after the transfer succeeds. Directories are moved recursively, with each
	 * child resource transferred and removed before the source directory is
	 * deleted.
	 *
	 * If [dst] refers to an existing directory, this resource is moved inside it.
	 *
	 * @param dst destination path or URI
	 * @return the moved resource, or `null` if the operation failed
	 */
	fun move(dst: String): FileOperation? = fs.move(path,dst)?.let(::FileOperation)

	/**
	 * Moves this resource to the destination represented by [dst].
	 *
	 * Files are transferred to the destination and the original file is deleted
	 * after the transfer succeeds. Directories are moved recursively, with each
	 * child resource transferred and removed before the source directory is
	 * deleted.
	 *
	 * If [dst] represents an existing directory, this resource is moved inside it.
	 *
	 * @param dst destination resource
	 * @return the moved resource, or `null` if the operation failed
	 */
	fun move(dst: FileOperation): FileOperation? = move(dst.path)

	/**
	 * Reads the complete contents of this resource as text.
	 *
	 * The text is decoded according to the behavior of the active filesystem
	 * implementation.
	 *
	 * @return the resource contents, or `null` if the resource could not be read
	 */
	fun readText(): String? = fs.readText(path)

	/**
	 * Writes text to this resource.
	 *
	 * When [append] is `true`, the text is appended to the existing contents.
	 * Otherwise, existing contents are replaced.
	 *
	 * The resource is created automatically when necessary.
	 *
	 * @param text text to write
	 * @param append whether to append instead of replacing existing contents
	 * @return `true` if the operation succeeds
	 */
	fun writeText(text: String,append: Boolean = false): Boolean {
		return if(append)	{
			fs.appendText(path,text)
		}else	{
			fs.writeText(path,text)
		}
	}

	/**
	 * Creates a new empty file represented by this resource.
	 *
	 * Parent directories may be created by the active filesystem implementation.
	 *
	 * @return `true` if the file was created successfully, otherwise `false`
	 */
	fun createNewFile(): Boolean = fs.createFile(path) != null

	/**
	 * Deletes this resource.
	 *
	 * When this resource is a directory, the deletion behavior is determined by
	 * the active filesystem implementation and may include recursively deleting
	 * its contents.
	 *
	 * @return `true` if the resource was deleted successfully, otherwise `false`
	 */
	fun delete(): Boolean = fs.delete(path)

	/**
	 * Opens this resource for writing.
	 *
	 * When [append] is `true`, new data is written after the existing contents.
	 * Otherwise, writing starts from the beginning of the resource.
	 *
	 * The returned [Sink] is provided by the active filesystem implementation.
	 * The caller is responsible for closing the returned sink.
	 *
	 * @param append whether to append to existing contents
	 * @return a writable sink, or `null` if the resource could not be opened
	 */
	fun openSink(append: Boolean = false): Sink? = fs.openSink(path,append)

	/**
	 * Opens this resource for reading.
	 *
	 * The returned [Source] provides access to the resource contents through the
	 * active filesystem implementation.
	 *
	 * The caller is responsible for closing the returned source.
	 *
	 * @return a readable source, or `null` if the resource could not be opened
	 */
	fun openSource(): Source? = fs.openSource(path)

	/**
	 * Lists the immediate children of this directory.
	 *
	 * This method does not recursively traverse subdirectories.
	 *
	 * Each returned [FileOperation] uses the same active filesystem
	 * implementation as this resource.
	 *
	 * @return the immediate child resources, or an empty list if the directory
	 *         cannot be listed or contains no children
	 */
	fun listFiles(): List<FileOperation> = fs.listFiles(path).map(::FileOperation)

	/**
	 * Determines whether this resource exists.
	 *
	 * @return `true` if the resource exists, otherwise `false`
	 */
	fun exists(): Boolean = fs.exists(path)

	/**
	 * Returns the path or URI represented by this resource.
	 *
	 * @return the original path or URI supplied to this [FileOperation]
	 */
	override fun toString(): String = path
}

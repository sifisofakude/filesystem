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
class FileOperation constructor(
	val path: String
) {
	private var parentInstance: FileOperation? = null

	constructor(parent: FileOperation, path: String) : this(FileSystems.current.combinePath(parent.path,path))	{
		parentInstance = parent
	}

	private val fs
	    get() = FileSystems.current

	val name
	    get() = fs.getName(path)

	val extension
	    get() = fs.getExtension(name)

	val nameWithoutExtension
	    get() = if (extension.isEmpty()) {
	    	name
	    } else {
	    	name.substringBeforeLast(".")
	    }

	val parent: FileOperation?
	    get() = fs.getParentFile(path)?.let(::FileOperation) ?: parentInstance

	val length
	    get() = fs.size(path)

	val lastModified
	    get() = fs.lastModified(path)

	val absolutePath
	    get() = fs.resolvePath(path)

	val canRead
	    get() = fs.canRead(path)

	val canWrite
	    get() = fs.canWrite(path)

	fun mkdir(): Boolean {
	    val parent = parent ?: return false
	    if (!parent.exists()) return false
	    return fs.createDirectory(path) != null
	}

	fun mkdirs(): Boolean = fs.createDirectory(path) != null

	fun isFile(): Boolean = fs.isFile(path)

	fun isDirectory(): Boolean = fs.isDirectory(path)

	fun copy(dst: String, overwrite: Boolean = false): FileOperation? =
	    fs.copy(path, dst, overwrite)?.let(::FileOperation)

	fun copy(dst: FileOperation, overwrite: Boolean = false): FileOperation? =
	    fs.copy(path, dst.path, overwrite)?.let {
	        FileOperation(dst.parent ?: return@let FileOperation(it), dst.path)
	    }

	fun move(dst: String): FileOperation? = fs.move(path, dst)?.let(::FileOperation)

	fun move(dst: FileOperation): FileOperation? =
	    fs.move(path, dst.path)?.let {
	        FileOperation(dst.parent ?: return@let FileOperation(it), dst.path)
	    }

	fun readText(): String? = fs.readText(path)

	fun writeText(text: String, append: Boolean = false): Boolean =
	    if (append) {
	        fs.appendText(path, text)
	    } else {
	        fs.writeText(path, text)
	    }

	fun createNewFile(): Boolean = fs.createFile(path) != null

	fun delete(): Boolean = fs.delete(path)

	fun openSink(append: Boolean = false): Sink? = fs.openSink(path, append)

	fun openSource(): Source? = fs.openSource(path)

	fun listFiles(): List<FileOperation> =
	    fs.listFiles(path).map {
	        FileOperation(this, it)
	    }

	fun exists(): Boolean = fs.exists(path)

	override fun toString(): String = path
}

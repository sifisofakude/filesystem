package io.github.sifisofakude.filesystem

import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path

import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSDate
import platform.Foundation.NSNumber
import platform.Foundation.NSString
import platform.Foundation.NSFileSize
import platform.Foundation.NSFileModificationDate
import platform.Foundation.NSFileType
import platform.Foundation.NSFileTypeDirectory
import platform.Foundation.NSFileTypeRegular

/**
 * iOS filesystem implementation of [FileSystemUtil].
 *
 * This implementation uses Apple's Foundation filesystem APIs to provide
 * access to files and directories stored on the iOS filesystem.
 *
 * Relative paths may be resolved against a selected directory using
 * [changeSelectedDirectory].
 *
 * Absolute paths are always treated as direct filesystem paths and are
 * therefore independent of the selected directory.
 *
 * The implementation supports:
 *
 * - File and directory creation
 * - Recursive file discovery
 * - File copying and moving
 * - File deletion
 * - File metadata inspection
 * - Stream-based file access through [kotlinx.io]
 * - Relative paths inside a selected directory
 *
 * ## Example
 *
 * ```kotlin
 * val fs = IosFileSystem()
 *
 * fs.changeSelectedDirectory("/var/mobile/...")
 *
 * val file = FileOperation("documents/test.txt")
 *
 * file.writeText("Hello iOS")
 * ```
 *
 * @see FileSystemUtil
 */
class IosFileSystem : FileSystemUtil {

	private val fileManager = NSFileManager.defaultManager

	/**
	 * Directory used as the root for relative paths.
	 */
	private var selectedParentDirectory: String? = null

	/**
	 * Sets the directory used as the root for relative paths.
	 *
	 * The supplied path is resolved using the current filesystem context and is
	 * only selected when it identifies an existing directory.
	 *
	 * Absolute paths are unaffected by the selected directory.
	 *
	 * Passing `null` clears the currently selected directory.
	 *
	 * @param path directory to use as the active filesystem root, or `null` to
	 * clear the current root.
	 */
	fun changeSelectedDirectory(path: String?) {
		selectedParentDirectory = path?.let {
			val resolved = resolvePath(it)

			if (isDirectory(resolved)) {
				resolved
			} else {
				null
			}
		}
	}

	/**
	 * Returns the currently selected filesystem root.
	 *
	 * @return selected directory path, or null if no directory is selected
	 */
	override fun getCurrentDirectory(): String? {
		return selectedParentDirectory
	}

	/**
	 * Determines whether [path] is an absolute filesystem path.
	 *
	 * @param path path to inspect
	 * @return true when the path is absolute
	 */
	fun isAbsolute(path: String): Boolean {
		return path.startsWith("/")
	}

	/**
	 * Determines whether [path] is relative.
	 *
	 * @param path path to inspect
	 * @return true when the path is relative
	 */
	fun isRelative(path: String): Boolean {
		return !isAbsolute(path)
	}

	/**
	 * Resolves a path against the currently selected filesystem root.
	 *
	 * Absolute paths are returned unchanged. Relative paths are combined with
	 * [selectedParentDirectory] when one has been selected; otherwise the
	 * relative path is returned unchanged.
	 *
	 * @param path relative or absolute path.
	 * @return path resolved against the current filesystem root when applicable.
	 */
	private fun resolveSelectedPath(path: String): String {
		if (isAbsolute(path)) {
			return path
		}

		val root = selectedParentDirectory
			?: return path

		return combinePath(root, path)
	}

	/**
	 * Creates a new empty file.
	 *
	 * Missing parent directories are created automatically.
	 *
	 * If a resource already exists at [path], no file is created and `null`
	 * is returned.
	 *
	 * @param path file path.
	 * @return resolved file path when creation succeeds, or `null` if creation
	 * fails or the path already exists.
	 */
	override fun createDirectory(path: String): String? {
		val resolved = resolveSelectedPath(path)

		return try {
			val success = fileManager.createDirectoryAtPath(
				path = resolved,
				withIntermediateDirectories = true,
				attributes = null
			)

			if (success) {
				resolved
			} else if (isDirectory(resolved)) {
				resolved
			} else {
				null
			}
		} catch (e: Exception) {
			null
		}
	}

	/**
	 * Creates a new empty file.
	 *
	 * Missing parent directories are created automatically.
	 *
	 * If a resource already exists at [path], no file is created and `null`
	 * is returned.
	 *
	 * @param path file path.
	 * @return resolved file path when creation succeeds, or `null` if creation
	 * fails or the path already exists.
	 */
	override fun createFile(path: String): String? {
		val resolved = resolveSelectedPath(path)

		if (exists(resolved)) {
			return null
		}

		return try {
			getParentFile(resolved)?.let {
				if (!exists(it)) {
					createDirectory(it) ?: return null
				}
			}

			val success = fileManager.createFileAtPath(
				path = resolved,
				contents = null,
				attributes = null
			)

			if (success) {
				resolved
			} else {
				null
			}
		} catch (e: Exception) {
			null
		}
	}

	/**
	 * Combines two filesystem paths.
	 *
	 * @param parent parent directory
	 * @param child child path
	 * @return combined path
	 */
	override fun combinePath(
		parent: String,
		child: String
	): String {
		if (child.isEmpty()) {
			return parent
		}

		if (parent.isEmpty()) {
			return child
		}

		return if (parent.endsWith("/")) {
			"$parent${child.trimStart('/')}"
		} else {
			"$parent/${child.trimStart('/')}"
		}
	}

	/**
	 * Opens a file for reading.
	 *
	 * @param path file path
	 * @return buffered source, or null if the file cannot be opened
	 */
	override fun openSource(path: String): Source? {
		val resolved = resolveSelectedPath(path)

		if (!isFile(resolved)) {
			return null
		}

		return try {
			FileSystem.SYSTEM
				.source(Path(resolved))
				.buffered()
		} catch (e: Exception) {
			null
		}
	}

	/**
	 * Opens a buffered sink for writing to a file.
	 *
	 * If the file does not exist, it is created along with any missing parent
	 * directories.
	 *
	 * @param path file path.
	 * @param append whether existing contents should be preserved.
	 * @return buffered sink, or `null` if the file cannot be opened.
	 */
	override fun openSink(
		path: String,
		append: Boolean
	): Sink? {
		val resolved = resolveSelectedPath(path)

		if (!exists(resolved)) {
			createFile(resolved) ?: return null
		}

		if (!isFile(resolved)) {
			return null
		}

		return try {
			FileSystem.SYSTEM
				.sink(Path(resolved), append = append)
				.buffered()
		} catch (e: Exception) {
			null
		}
	}

	/**
	 * Recursively finds files inside a directory.
	 *
	 * Relative directories are resolved against the selected filesystem root.
	 * Returned paths are the resolved filesystem paths.
	 *
	 * @param directory directory to search.
	 * @param extensions allowed file extensions; an empty set includes all files.
	 * @return list of matching resolved file paths.
	 */
	override fun findFiles(
		directory: String,
		extensions: Set<String>
	): List<String> {
		val resolved = resolveSelectedPath(directory)
		val results = mutableListOf<String>()

		if (!isDirectory(resolved)) {
			return results
		}

		listFiles(resolved).forEach { file ->
			if (isDirectory(file)) {
				results.addAll(findFiles(file, extensions))
			} else if (
				extensions.isEmpty() ||
				getExtension(file) in extensions
			) {
				results.add(file)
			}
		}

		return results
	}

	/**
	 * Lists the immediate children of a directory.
	 *
	 * @param path directory path
	 * @return list of child paths
	 */
	override fun listFiles(path: String): List<String> {
		val resolved = resolveSelectedPath(path)

		return try {
			fileManager
				.contentsOfDirectoryAtPath(resolved, error = null)
				?.map {
					combinePath(resolved, it as String)
				}
				?: emptyList()
		} catch (e: Exception) {
			emptyList()
		}
	}

	/**
	 * Checks whether a file or directory exists.
	 *
	 * @param path path to inspect
	 * @return true when the resource exists
	 */
	override fun exists(path: String): Boolean {
		val resolved = resolveSelectedPath(path)

		return fileManager.fileExistsAtPath(resolved)
	}

	/**
	 * Deletes a file or directory recursively.
	 *
	 * @param path resource path
	 * @return true when deletion succeeds
	 */
	override fun delete(path: String): Boolean {
		val resolved = resolveSelectedPath(path)

		if (!exists(resolved)) {
			return false
		}

		return try {
			fileManager.removeItemAtPath(
				resolved,
				error = null
			)
		} catch (e: Exception) {
			false
		}
	}

	/**
	 * Moves a file or directory to a new name or location.
	 *
	 * If [target] is relative, it is resolved against the source's parent
	 * directory. If [target] is absolute, it is used directly as the
	 * destination.
	 *
	 * @param src source path.
	 * @param target new name or destination path.
	 * @return destination path when successful, or `null` if the operation fails.
	 */
	override fun rename(
		src: String,
		target: String
	): String? {
		val source = resolveSelectedPath(src)

		if (!exists(source)) {
			return null
		}

		val parent = getParentFile(source)
			?: return null

		val destination = if (isAbsolute(target)) {
			target
		} else {
			combinePath(parent, target)
		}

		return try {
			if (
				fileManager.moveItemAtPath(
					source,
					toPath = destination,
					error = null
				)
			) {
				destination
			} else {
				null
			}
		} catch (e: Exception) {
			null
		}
	}

	/**
	 * Resolves input files into [FileSource] objects.
	 *
	 * Directory inputs are recursively traversed while preserving
	 * their relative structure.
	 *
	 * @param inputFiles files or directories
	 * @param extensions allowed extensions
	 * @return resolved file sources
	 */
	override fun resolveFiles(
		inputFiles: List<Any>,
		extensions: Set<String>
	): List<FileSource> {
		val results = mutableListOf<FileSource>()

		inputFiles.forEach { input ->
			val path = when (input) {
				is String -> input
				is NSURL -> input.path
				else -> null
			} ?: return@forEach

			val resolved = resolveSelectedPath(path)

			if (isFile(resolved)) {
				if (
					extensions.isEmpty() ||
					getExtension(resolved) in extensions
				) {
					results.add(
						FileSource(
							relativePath = getName(resolved),
							absolutePath = resolved
						)
					)
				}
			} else if (isDirectory(resolved)) {
				resolveDirectory(
					resolved,
					"",
					extensions,
					results
				)
			}
		}

		return results
	}

	/**
	 * Recursively resolves files inside a directory.
	 */
	private fun resolveDirectory(
		directory: String,
		basePath: String,
		extensions: Set<String>,
		results: MutableList<FileSource>
	) {
		listFiles(directory).forEach { file ->
			val name = getName(file)

			val relativePath =
				if (basePath.isEmpty()) {
					name
				} else {
					"$basePath/$name"
				}

			if (isDirectory(file)) {
				resolveDirectory(
					file,
					relativePath,
					extensions,
					results
				)
			} else if (
				extensions.isEmpty() ||
				getExtension(file) in extensions
			) {
				results.add(
					FileSource(
						relativePath = relativePath,
						absolutePath = file
					)
				)
			}
		}
	}

	/**
	 * Resolves and normalizes a filesystem path.
	 *
	 * @param path path to resolve
	 * @return normalized path
	 */
	override fun resolvePath(path: String): String {
		val resolved = resolveSelectedPath(path)

		return normalizePath(resolved)
	}

	/**
	 * Normalizes path separators and resolves `.` and `..` segments.
	 */
	private fun normalizePath(path: String): String {
		val parts = path
			.split("/")
			.filter { it.isNotEmpty() && it != "." }

		val normalized = mutableListOf<String>()

		parts.forEach { part ->
			if (part == "..") {
				if (normalized.isNotEmpty()) {
					normalized.removeAt(normalized.lastIndex)
				}
			} else {
				normalized.add(part)
			}
		}

		return if (path.startsWith("/")) {
			"/" + normalized.joinToString("/")
		} else {
			normalized.joinToString("/")
		}
	}

	/**
	 * Copies a file or directory into a destination directory.
	 *
	 * The source may be a relative or absolute path. Relative paths are resolved
	 * against the currently selected directory.
	 *
	 * The destination must be an existing directory. The copied resource keeps
	 * the same name as the source.
	 *
	 * When [overwrite] is `false`, the operation fails if a resource with the
	 * same name already exists in the destination.
	 *
	 * @param src source file or directory path
	 * @param dst destination directory path
	 * @param overwrite whether an existing resource with the same name may be
	 * replaced
	 * @return path of the copied resource, or `null` if the operation fails
	 */
	override fun copy(
    src: String,
    dst: String,
    overwrite: Boolean
	): String? {
    val source = resolveSelectedPath(src)
    val destination = resolveSelectedPath(dst)

    if (!exists(source) || !isDirectory(destination)) {
      return null
    }

    val target = combinePath(
      destination,
      getName(source)
    )

    if (exists(target)) {
      if (!overwrite) {
        return null
      }

      try {
        fileManager.removeItemAtPath(
          target,
          error = null
        )
      } catch (e: Exception) {
        return null
      }
    }

    return try {
      if (
          fileManager.copyItemAtPath(
              source,
              toPath = target,
              error = null
          )
      ) {
          target
      } else {
          null
      }
    } catch (e: Exception) {
      null
    }
	}

	/**
	 * Moves a file or directory into a destination directory.
	 *
	 * The source may be a relative or absolute path. Relative paths are resolved
	 * against the currently selected directory.
	 *
	 * The destination must be an existing directory. The moved resource keeps
	 * the same name as the source.
	 *
	 * @param src source file or directory path
	 * @param dst destination directory path
	 * @return path of the moved resource, or `null` if the operation fails
	 */
	override fun move(
    src: String,
    dst: String
	): String? {
    val source = resolveSelectedPath(src)
    val destination = resolveSelectedPath(dst)

    if (!exists(source) || !isDirectory(destination)) {
      return null
    }

    val target = combinePath(
      destination,
      getName(source)
    )

    if (exists(target)) {
      return null
    }

    return try {
      if (
        fileManager.moveItemAtPath(
          source,
          toPath = target,
          error = null
        )
      ) {
        target
      } else {
        null
      }
    } catch (e: Exception) {
      null
    }
	}

	/**
	 * Returns whether a resource is a regular file.
	 *
	 * @param path path to inspect
	 * @return true when the resource is a file
	 */
	override fun isFile(path: String): Boolean {
		val resolved = resolveSelectedPath(path)

		return try {
			val attributes =
				fileManager.attributesOfItemAtPath(
					resolved,
					error = null
				)

			attributes?.get(NSFileType) == NSFileTypeRegular
		} catch (e: Exception) {
			false
		}
	}

	/**
	 * Returns whether a resource is a directory.
	 *
	 * @param path path to inspect
	 * @return true when the resource is a directory
	 */
	override fun isDirectory(path: String): Boolean {
		val resolved = resolveSelectedPath(path)

		return try {
			val attributes =
				fileManager.attributesOfItemAtPath(
					resolved,
					error = null
				)

			attributes?.get(NSFileType) == NSFileTypeDirectory
		} catch (e: Exception) {
			false
		}
	}

	/**
	 * Returns the last modification timestamp.
	 *
	 * The returned value is expressed as milliseconds since the Unix epoch.
	 *
	 * @param path path to inspect
	 * @return modification timestamp, or -1 if unavailable
	 */
	override fun lastModified(path: String): Long {
		val resolved = resolveSelectedPath(path)

		return try {
			val attributes =
				fileManager.attributesOfItemAtPath(
					resolved,
					error = null
				)

			val date = attributes?.get(NSFileModificationDate)
				as? NSDate
				?: return -1L

			(date.timeIntervalSince1970 * 1000.0).toLong()
		} catch (e: Exception) {
			-1L
		}
	}

	/**
	 * Returns the parent directory of a resource.
	 *
	 * @param path resource path
	 * @return parent path, or null if unavailable
	 */
	override fun getParentFile(path: String): String? {
		val resolved = resolveSelectedPath(path)

		val index = resolved.lastIndexOf('/')

		if (index <= 0) {
			return if (resolved.startsWith("/")) {
				"/"
			} else {
				null
			}
		}

		return resolved.substring(0, index)
	}

	/**
	 * Returns the size of a file in bytes.
	 *
	 * @param path file path
	 * @return file size, or 0 if unavailable
	 */
	override fun size(path: String): Long {
		val resolved = resolveSelectedPath(path)

		return try {
			val attributes =
				fileManager.attributesOfItemAtPath(
					resolved,
					error = null
				)

			val value = attributes?.get(NSFileSize)

			when (value) {
				is NSNumber -> value.longLongValue
				else -> 0L
			}
		} catch (e: Exception) {
			0L
		}
	}

	/**
	 * Returns the final component of a path.
	 *
	 * @param path file or directory path
	 * @return resource name
	 */
	override fun getName(path: String): String {
		val cleanPath = path.trimEnd('/')

		return cleanPath.substringAfterLast('/')
	}
}

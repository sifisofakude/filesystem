package io.github.sifisofakude.filesystem

import kotlinx.io.Source
import kotlinx.io.Sink
import kotlinx.io.Buffer
import kotlinx.io.buffered
import kotlinx.io.readString
import kotlinx.io.writeString

/**
 * Represents a resolved file together with its logical and resolved paths.
 *
 * A [FileSource] is produced by [FileSystemUtil.resolveFiles] when resolving
 * files from individual paths, directories, or filesystem-specific resources.
 *
 * [relativePath] represents the file's location relative to the root from
 * which it was resolved. This is useful when preserving directory structure
 * during operations such as archive creation or source processing.
 *
 * [absolutePath] identifies the resolved resource. Depending on the active
 * filesystem implementation, this may be a conventional filesystem path,
 * or another platform-specific resource identifier.
 *
 * @property relativePath path relative to the resolution root
 * @property absolutePath resolved path or filesystem-specific resource identifier
 *
 * @since 0.2.0
 */
data class FileSource(
	val relativePath: String,
	val absolutePath: String,
)


/**
 * Provides a platform-independent abstraction over filesystem operations.
 *
 * [FileSystemUtil] defines the common filesystem operations used by
 * [FileOperation] and provides default implementations for higher-level
 * operations such as copying, moving, reading, and writing.
 *
 * Implementations may use fundamentally different storage models. For example:
 *
 * - [JvmFileSystem] uses conventional filesystem paths.
 * - [AndroidSafFileSystem] supports Android Storage Access Framework resources
 *   identified by document URIs as well as paths relative to a selected root.
 * - An iOS implementation may use Apple's Foundation filesystem APIs.
 *
 * Paths are represented as [String] values so that the same API can operate
 * across filesystem implementations. The interpretation of each path is
 * determined by the active implementation.
 *
 * The default implementations of [copy], [move], [readText], [writeText],
 * and related operations use the primitive operations defined by this
 * interface. This allows implementations to inherit common behavior while
 * overriding only operations that require platform-specific handling.
 *
 * @see FileOperation
 * @see FileSystems
 */
interface FileSystemUtil	{

	/**
	 * Returns the currently selected directory or filesystem root.
	 *
	 * The returned value is implementation-specific. It may be a conventional
	 * filesystem path, a SAF URI, or another platform-specific resource
	 * identifier.
	 *
	 * Implementations that do not maintain a selected root may return `null`.
	 *
	 * @return current directory or filesystem root, or `null` if none is selected
	 */
	fun getCurrentDirectory(): String?

	/**
	 * Creates a directory at [path].
	 *
	 * Implementations may create missing parent directories when supported by
	 * the underlying filesystem.
	 *
	 * The returned value identifies the resulting directory and may differ from
	 * [path]. For example, an implementation may return an absolute or normalized
	 * path even when the supplied path was relative.
	 *
	 * @param path directory path or filesystem-specific resource identifier
	 * @return resulting directory path or URI, or `null` if creation failed
	 */
	fun createDirectory(path: String): String?

	/**
	 * Creates a file at [path].
	 *
	 * Implementations may create missing parent directories when supported by
	 * the underlying filesystem. If a file already exists, the implementation
	 * may return the existing resource or `null`.
	 *
	 * The returned value identifies the resulting file and may differ from
	 * [path] when the underlying implementation resolves or normalizes it.
	 *
	 * @param path file path or filesystem-specific resource identifier
	 * @return resulting file path or URI, or `null` if creation failed
	 */
	fun createFile(path: String): String?

	/**
	 * Combines [parent] and [child] into a path understood by the filesystem
	 * implementation.
	 *
	 * Implementations are responsible for applying the appropriate separator
	 * or resource-specific path joining rules.
	 *
	 * @param parent parent directory or resource path
	 * @param child child name or relative path
	 * @return combined path
	 */
	fun combinePath(parent: String, child: String): String

	/**
	 * Copies a file or directory from [src] to [dst].
	 *
	 * Files are copied using [openSource] and [openSink]. Directories are copied
	 * recursively using [listFiles], [createDirectory], and [copy].
	 *
	 * If [dst] refers to an existing directory, the source is copied into that
	 * directory using the source resource's name.
	 *
	 * If [dst] does not exist, its parent directory must already exist. The
	 * destination resource is then created before the contents are copied.
	 *
	 * The operation is implemented entirely through [FileSystemUtil] operations,
	 * allowing an implementation to support transfers between different
	 * filesystem roots or storage representations.
	 *
	 * For example, an implementation may support copying between a conventional
	 * filesystem path and a provider-backed resource such as an Android SAF URI.
	 *
	 * The returned value identifies the actual destination resource. When copying
	 * into an existing directory, this may differ from [dst].
	 *
	 * @param src source file or directory
	 * @param dst destination file or directory
	 * @param overwrite whether an existing destination file may be overwritten
	 * @return resulting destination path or URI, or `null` if copying failed
	 */
	fun copy(src: String, dst: String,overwrite: Boolean = true): String?
	
	fun streamCopy(
    src: String,
    dst: String,
    overwrite: Boolean = true
	): String? {
		var tmpSource = src
		val sourceParent = getParentFile(src)
		if(sourceParent == null)	{
			if(isRelative(src))	{
				getCurrentDirectory()?.let	{
					tmpSource = combinePath(it,src)
				} ?: return null
			}else	{
				return null
			}
		}

		var tmpDestination = dst
		val destinationParent = getParentFile(dst)
		if(destinationParent == null)	{
			if(isRelative(dst))	{
				getCurrentDirectory()?.let	{
					tmpDestination = combinePath(it,dst)
				} ?: return null
			}else	{
				return null
			}
		}

		var returnDst = dst

	
    if (!exists(tmpSource)) {
    	return null
    }

    if (isDirectory(tmpSource)) {
   		var finalDst: String? = null
   		
    	if(isFile(tmpDestination))	{
    		return null
    	}

    	if(isDirectory(tmpDestination))	{
	   		val srcName = getName(tmpSource)
	   		val dstName = getName(tmpDestination)
	   		
	   		finalDst = if(srcName != dstName)	{
	   			val tmpPath = combinePath(tmpDestination,srcName)

	   			returnDst = combinePath(returnDst,srcName)
	   			
	   			createDirectory(tmpPath)?.let	{
	   				tmpPath
	   			} ?: return null
	   		}else	{
	   			tmpDestination
	   		}
    	}

    	if(!exists(tmpDestination))	{
    		createDirectory(tmpDestination) ?: return null

    		finalDst = tmpDestination
    	}


   		if(finalDst == null) return null
   		
   		for(file in listFiles(tmpSource))	{
 				val name = getName(file)

   			if(isDirectory(file))	{
   				createDirectory(combinePath(finalDst,name))?.let	{
   					copy(file,it,overwrite)
   				}
   			}else	{
   				createFile(combinePath(finalDst,name))?.let	{
   					copy(file,it,true)
   				}
   			}
   		}
   		return returnDst
    }

		var finalDst = tmpDestination
		var finalOverwrite = overwrite
		
    if(isDirectory(tmpDestination))	{
    	val name = getName(tmpSource)
    	finalDst = combinePath(tmpDestination,name)
    	returnDst = combinePath(returnDst,name)
    }else if(!exists(tmpDestination))	{

   		createFile(tmpDestination) ?: return null
   		finalOverwrite = true
    }
    
 		if(finalOverwrite)	{
 			openSource(tmpSource)?.use { source ->
 				openSink(finalDst)?.use { sink ->
 					source.transferTo(sink)
 				}
 			}
 			return returnDst
 		}
    return null
	}

	/**
	 * Opens [path] for reading.
	 *
	 * The returned [Source] provides access to the contents of the resource.
	 * The caller is responsible for closing the returned source.
	 *
	 * @param path file path or filesystem-specific resource identifier
	 * @return readable source, or `null` if the resource could not be opened
	 */
	fun openSource(path: String): Source?

	/**
	 * Opens [path] for writing.
	 *
	 * When [append] is `false`, writing starts at the beginning of the resource
	 * according to the behavior of the underlying implementation. When [append]
	 * is `true`, data is appended to the existing contents.
	 *
	 * The caller is responsible for closing the returned sink.
	 *
	 * @param path destination file path or filesystem-specific resource identifier
	 * @param append whether data should be appended to existing contents
	 * @return writable sink, or `null` if the resource could not be opened
	 */
	fun openSink(path: String, append: Boolean = false): Sink?

	/**
	 * Reads the complete contents of [path] as text.
	 *
	 * The resource is opened using [openSource] and closed automatically after
	 * reading.
	 *
	 * @param path file path or filesystem-specific resource identifier
	 * @return contents of the resource, or `null` if it could not be opened
	 */
	fun readText(path: String): String?	{
		val source = openSource(path) ?: return null

		return source.use 	{ bufferedSource ->
			bufferedSource.readString()
		}
	}

	/**
	 * Recursively searches [directory] for files.
	 *
	 * When [extensions] is not empty, only files whose extensions are contained
	 * in the set are returned. An empty set includes files with any extension.
	 *
	 * Returned paths are represented according to the active filesystem
	 * implementation.
	 *
	 * @param directory directory from which the search begins
	 * @param extensions allowed file extensions; an empty set includes all files
	 * @return matching file paths or filesystem-specific resource identifiers
	 */
	fun findFiles(
		directory: String, 
		extensions: Set<String>
	): List<String>

	/**
	 * Returns the immediate children of [path].
	 *
	 * Unlike [findFiles], this method does not recursively traverse
	 * subdirectories.
	 *
	 * @param path directory path or filesystem-specific resource identifier
	 * @return immediate child paths or resource identifiers
	 */
	fun listFiles(path: String): List<String>

	/**
	 * Determines whether a filesystem resource exists at [path].
	 *
	 * @param path file, directory, or filesystem-specific resource identifier
	 * @return `true` if the resource exists, otherwise `false`
	 */
	fun exists(path: String): Boolean

	/**
	 * Deletes the resource at [path].
	 *
	 * If [path] refers to a directory, the implementation may recursively delete
	 * its contents.
	 *
	 * @param path file or directory path
	 * @return `true` if the resource was successfully deleted, otherwise `false`
	 */
	fun delete(path: String): Boolean

	/**
	 * Renames a file or directory.
	 *
	 * The interpretation of [target] is implementation-specific. It may
	 * represent a new resource name or a destination path.
	 *
	 * Implementations may impose restrictions on rename operations, such as
	 * requiring the source and target to share the same parent.
	 *
	 * @param src source file or directory
	 * @param target new name or destination
	 * @return resulting path or URI, or `null` if the operation failed
	 */
	fun rename(src: String, target: String): String?

	/**
	 * Moves a file or directory from [src] to [dst].
	 *
	 * Files are moved by copying their contents to the destination and deleting
	 * the source only after the copy succeeds.
	 *
	 * Directories are moved recursively. Each child resource is moved
	 * individually, and the source child is deleted only after its transfer
	 * succeeds. Once all children have been moved successfully, the original
	 * directory is deleted.
	 *
	 * This implementation uses the filesystem abstraction rather than a
	 * platform-specific move or rename operation. This allows resources to be
	 * moved between different filesystem roots or storage representations when
	 * the implementation supports them.
	 *
	 * If [dst] is an existing directory, the source resource is moved into it
	 * using the source resource's name.
	 *
	 * If a child move fails, the operation stops and the source directory is not
	 * deleted. Resources that were successfully moved before the failure remain
	 * moved.
	 *
	 * @param src source file or directory
	 * @param dst destination file or directory
	 * @return resulting destination path or URI, or `null` if the move failed
	 */
	fun move(src: String, dst: String): String?
	
	fun streamMove(src: String, dst: String): String? {
		var tmpSource = src
		val sourceParent = getParentFile(src)
      	println(tmpSource)
		if(sourceParent == null)	{
			if(isRelative(src))	{
				getCurrentDirectory()?.let	{
					tmpSource = combinePath(it,src)
				} ?: return null
			}else	{
				return null
			}
		}

		var tmpDestination = dst
		val destinationParent = getParentFile(dst)
		if(destinationParent == null)	{
			if(isRelative(dst))	{
				getCurrentDirectory()?.let	{
					tmpDestination = combinePath(it,dst)
				} ?: return null
			}
		}

		var returnDst = dst
		
    if (!exists(tmpSource)) return null
    
    if (isDirectory(tmpSource)) {
      if (isFile(tmpDestination)) return null

      val finalDst = if (isDirectory(tmpDestination)) {
				returnDst = combinePath(returnDst,getName(tmpSource))
        val target = combinePath(tmpDestination, getName(tmpSource))
        if (!exists(target)) {
            createDirectory(target) ?: return null
        }
         target
      } else {
        createDirectory(tmpDestination) ?: return null
        tmpDestination
      }

      for (child in listFiles(tmpSource)) {
        val childDst = combinePath(finalDst, getName(child))

        if (move(child, childDst) == null) {
            return null
        }
      }

      return if(delete(tmpSource))	{
      	returnDst
      }else	{
      	null
      }
    }

    val finalDst = if (isDirectory(tmpDestination)) {
    	returnDst = combinePath(returnDst,getName(tmpSource))
      combinePath(tmpDestination, getName(tmpSource))
    } else {
        tmpDestination
    }

    if (copy(tmpSource, finalDst,true) == null) {
        return null
    }

    return if (delete(tmpSource)) returnDst else null
	}

	/**
	 * Resolves input resources into a flat collection of [FileSource] entries.
	 *
	 * Input values may represent files, directories, paths, URIs, or other
	 * filesystem-specific resource types supported by the implementation.
	 *
	 * Directory inputs are traversed recursively.
	 *
	 * Each returned [FileSource] contains a logical relative path together with
	 * the resolved path or resource identifier used to access the file.
	 *
	 * When [extensions] is not empty, only matching files are returned.
	 *
	 * @param inputFiles files, directories, paths, URIs, or filesystem-specific objects
	 * @param extensions allowed file extensions; an empty set includes all files
	 * @return resolved file sources
	 */
	fun resolveFiles(
		inputFiles: List<Any>,
		extensions: Set<String>
	): List<FileSource>

	/**
	 * Resolves and normalizes [path] according to the filesystem implementation.
	 *
	 * Conventional filesystem implementations may return an absolute,
	 * normalized path, while virtual or provider-backed implementations may
	 * return a filesystem-specific resource identifier.
	 *
	 * @param path path or filesystem-specific resource identifier
	 * @return resolved or normalized path
	 */
	fun resolvePath(path: String): String

	/**
	 * Determines whether [path] represents a regular file.
	 *
	 * @param path file or filesystem-specific resource identifier
	 * @return `true` if the resource is a file, otherwise `false`
	 */
	fun isFile(path: String): Boolean

	/**
	 * Determines whether [path] represents a directory.
	 *
	 * @param path directory or filesystem-specific resource identifier
	 * @return `true` if the resource is a directory, otherwise `false`
	 */
	fun isDirectory(path: String): Boolean

	fun isRelative(path: String): Boolean

	/**
	 * Returns the last modification timestamp of [path].
	 *
	 * The timestamp is expressed as milliseconds since the Unix epoch.
	 *
	 * @param path file or directory path
	 * @return last modification timestamp in milliseconds
	 */
	fun lastModified(path: String): Long

	/**
	 * Returns the parent directory of [path].
	 *
	 * The returned value is represented according to the active filesystem
	 * implementation.
	 *
	 * @param path file or directory path
	 * @return parent directory path or URI, or `null` if no parent exists
	 */
	fun getParentFile(path: String): String?

	/**
	 * Returns the size of [path] in bytes.
	 *
	 * The result for directories is implementation-specific.
	 *
	 * @param path file or filesystem-specific resource identifier
	 * @return resource size in bytes
	 */
	fun size(path: String): Long

	/**
	 * Returns the display name of [path].
	 *
	 * For conventional filesystems, this is normally the final component of
	 * the path. Provider-backed implementations may obtain the name from the
	 * underlying provider.
	 *
	 * @param path file, directory, or filesystem-specific resource identifier
	 * @return display name of the resource
	 */
	fun getName(path: String): String

	/**
	 * Returns the file extension of [path].
	 *
	 * The extension is the portion of the path following the final `.`.
	 * The leading `.` is not included.
	 *
	 * If the path does not contain an extension, an empty string is returned.
	 *
	 * @param path file or resource path
	 * @return file extension without the leading `.`
	 */
	fun getExtension(path: String): String	{
		val name = getName(path)
		val dotIndex = name.lastIndexOf('.')
		return if(dotIndex > 0 && dotIndex < name.length)	{
			name.substring(dotIndex+1)
		}else	{
			""
		}
	}

	/**
	 * Materializes a filesystem-specific or virtual resource into a physical
	 * filesystem hierarchy.
	 *
	 * This is primarily intended for resources that cannot be consumed directly
	 * by APIs requiring conventional filesystem paths, such as provider-backed
	 * or URI-based resources.
	 *
	 * Implementations that already operate on physical filesystem paths may
	 * simply return [path].
	 *
	 * @param path source path or filesystem-specific resource identifier
	 * @param outDir destination directory for materialized data
	 * @return physical path to the materialized resource
	 */
	fun materialize(path: String, outDir: String): String = path

	/**
	 * Removes resources previously created by [materialize].
	 *
	 * Implementations that do not require materialization may leave this method
	 * empty.
	 *
	 * @param path materialized resource or materialization identifier
	 */
	fun clearMaterialized(path: String) {}

	/**
	 * Transfers all bytes from [input] to [output].
	 *
	 * Neither [input] nor [output] is closed by this method.
	 *
	 * The output is flushed after the transfer completes.
	 *
	 * @param input source stream
	 * @param output destination stream
	 * @return `true` if the transfer succeeds, otherwise `false`
	 */
	fun write(input: Source, output: Sink): Boolean	{
		try	{
			input.transferTo(output)
			output.flush()
			return true
		}catch(e: Exception) {}
		return false
	}

	/**
	 * Writes [text] to [outputFile].
	 *
	 * If the destination does not exist, an attempt is made to create it using
	 * [createFile].
	 *
	 * Existing contents are replaced according to the behavior of [openSink]
	 * when append mode is disabled.
	 *
	 * @param outputFile destination file path or filesystem-specific resource
	 * @param text text to write
	 * @return `true` if the text was written successfully, otherwise `false`
	 */
	fun writeText(outputFile: String, text: String): Boolean	{
		if(!exists(outputFile)) createFile(outputFile)
		
		openSink(outputFile)?.use 	{ output ->
			output.writeString(text)
			output.flush()

			return true
		}
		return false
	}

	/**
	 * Appends [text] to [outputFile].
	 *
	 * The existing contents of the destination are preserved. If the destination
	 * does not exist, creation behavior is determined by the underlying
	 * [openSink] implementation.
	 *
	 * @param outputFile destination file path or filesystem-specific resource
	 * @param text text to append
	 * @return `true` if the text was appended successfully, otherwise `false`
	 */
	fun appendText(outputFile: String, text: String): Boolean	{
		return openSink(outputFile,true)?.use { bufferedSink ->
			bufferedSink.writeString(text)
			bufferedSink.flush()

			true
		} 
		?: false
	}
}


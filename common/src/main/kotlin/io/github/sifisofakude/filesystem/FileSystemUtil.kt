package io.github.sifisofakude.filesystem

import java.io.InputStream
import java.io.OutputStream

import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

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
 * Core abstraction for filesystem operations across JVM and Android.
 *
 * This interface defines a unified API over:
 * - JVM file system (`java.io.File`)
 * - Android SAF (`content://` URIs)
 *
 * Implementations may override platform-specific behavior.
 *
 * Some methods include default JVM-based implementations.
 */
interface FileSystemUtil	{

	/**
	 * Returns the current working directory or active root directory.
	 *
	 * Implementations may interpret this differently depending on the
	 * underlying filesystem:
	 *
	 * - JVM implementations typically return the process working directory
	 * - SAF implementations may return the currently selected tree URI
	 *
	 * @return current directory path or URI, or null if unavailable
	 */
	fun getCurrentDirectory(): String?

	/**
	 * Creates a directory.
	 *
	 * Implementations may create missing parent directories automatically.
	 *
	 * @param path directory path or URI
	 * @return resulting directory path or URI, or null if creation failed
	 */
	fun createDirectory(path: String): String?

	/**
	 * Creates a file.
	 *
	 * Implementations may create missing parent directories automatically.
	 *
	 * If the file already exists, behavior is implementation-defined.
	 *
	 * @param path file path or URI
	 * @return resulting file path or URI, or null if creation failed
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
    src: String,
    dst: String,
    overwrite: Boolean = true
	) {
    if(isDirectory(src))	{
    	for(file in listFiles(src))	{
    		if(isDirectory(file))	{
    			val dir = "$dst${File.separator}${getName(file)}"
    			
    			createDirectory(dir)
    			copy(file,dir)
    		}else	{
    			var parentFile = getParentFile(file) ?: ""
    			if(parentFile.isNotEmpty()) parentFile = "$parentFile${File.separator}"
    			
    			val destinationFile = "$dst${File.separator}${getName(file)}"

    			if(exists(destinationFile) && !overwrite)	{
    				continue
    			}

					createFile(destinationFile) ?: continue
					
    			openInputStream(file)?.use { input ->
    				openOutputStream(destinationFile)?.use { output ->
    					input.copyTo(output)
    				}
    			}
    		}
    	}
    }else	{
    	createDirectory(dst)
    	
    	val destinationFile = "$dst${File.separator}${getName(src)}"


 			if(exists(destinationFile) && !overwrite)	{
 				return
 			}

 			createFile(destinationFile) ?: return

 			openInputStream(src)?.use { input ->
 				openOutputStream(destinationFile)?.use { output ->
 					input.copyTo(output)
 				}
 			}
    }
	}

	/**
	 * Opens an output stream for writing.
	 *
	 * Implementations may support:
	 *
	 * - JVM filesystem paths
	 * - Android SAF URIs
	 *
	 * The caller is responsible for closing the stream.
	 *
	 * @param path file path or URI
	 * @return writable output stream, or null if unavailable
	 */
	fun openOutputStream(path: String): OutputStream?

	/**
	 * Opens an input stream for reading.
	 *
	 * Implementations may support:
	 *
	 * - JVM filesystem paths
	 * - Android SAF URIs
	 *
	 * The caller is responsible for closing the stream.
	 *
	 * @param path file path or URI
	 * @return readable input stream, or null if unavailable
	 */
	fun openInputStream(path: String): InputStream?

	/**
	 * Reads the contents of a file as text.
	 *
	 * @param path the file path
	 * @return the file contents, or null if the file could not be read
	 */
	fun readText(path: String): String?	{
		val stream = openInputStream(path) ?: return null

		return stream.bufferedReader().use { it.readText() }
	}

	/**
	 * Recursively searches a directory for files.
	 *
	 * When [extensions] is not empty, only files whose extension matches
	 * one of the supplied values are returned.
	 *
	 * @param directory root directory path or URI
	 * @param extensions allowed file extensions
	 * @return matching file paths or URIs
	 */
	fun findFiles(
		directory: String, 
		extensions: Set<String>
	): List<String>

	/**
	 * Lists the immediate children of a directory.
	 *
	 * Unlike [findFiles], this method does not recursively traverse
	 * subdirectories.
	 *
	 * @param path directory path or URI
	 * @return child file and directory paths or URIs
	 */
	fun listFiles(path: String): List<String>

	/**
	 * Returns whether a file or directory exists.
	 *
	 * @param path file path or URI
	 * @return true if the resource exists
	 */
	fun exists(path: String): Boolean

	/**
	 * Deletes a file or directory.
	 *
	 * Directory deletion behavior is implementation-defined.
	 *
	 * @param path file path or URI
	 * @return true if deletion succeeded
	 */
	fun delete(path: String): Boolean

	/**
	 * Renames a file or directory.
	 *
	 * Implementations may impose restrictions on rename operations,
	 * such as requiring source and destination to share the same parent.
	 *
	 * @param src source path or URI
	 * @param target target name or destination
	 * @return resulting path or URI, or null if the operation failed
	 */
	fun rename(src: String, target: String): String?

	/**
	 * Moves a file or directory to a new location.
	 *
	 * @param src the source path
	 * @param dst the destination path
	 * @return the resulting path, or null if the operation failed
	 */
	fun move(src: String, dst: String): String?	{
		var destination = "$dst${File.separator}${File(src).name}"

		if(exists(src))	{
			copy(src,destination)
			if(exists(destination)) delete(src)
			return destination
		}
		return null
	}

	/**
	 * Resolves input files, directories, or filesystem-specific resources
	 * into a flat collection of [FileSource] entries.
	 *
	 * Directory inputs are traversed recursively.
	 *
	 * The resulting [FileSource.relativePath] preserves directory structure
	 * relative to the supplied root whenever possible.
	 *
	 * @param inputFiles files, directories, paths, or filesystem-specific objects
	 * @param extensions allowed file extensions
	 * @return resolved file sources
	 */
	fun resolveFiles(
		inputFiles: List<Any>,
		extensions: Set<String>
	): List<FileSource>

	/**
	 * Resolves a path into its canonical form.
	 *
	 * Implementations may normalize path separators, remove redundant
	 * segments such as "." and "..", or normalize filesystem-specific URIs.
	 *
	 * @param path path or URI to resolve
	 * @return normalized path or URI
	 */
	fun resolvePath(path: String): String

	/**
	 * Returns whether the supplied path or URI represents a regular file.
	 *
	 * @param path file path or URI
	 * @return true if the resource is a file
	 */
	fun isFile(path: String): Boolean

	/**
	 * Returns whether the supplied path or URI represents a directory.
	 *
	 * @param path directory path or URI
	 * @return true if the resource is a directory
	 */
	fun isDirectory(path: String): Boolean

	/**
	 * Returns the last modification timestamp.
	 *
	 * The returned value is expressed as milliseconds since the Unix epoch.
	 *
	 * @param path file path or URI
	 * @return last modification timestamp
	 */
	fun lastModified(path: String): Long

	/**
	 * Returns the parent directory of a file or directory.
	 *
	 * @param path file path or URI
	 * @return parent path or URI, or null if unavailable
	 */
	fun getParentFile(path: String): String?

	/**
	 * Returns the size of a file in bytes.
	 *
	 * Directory size behavior is implementation-defined.
	 *
	 * @param path file path or URI
	 * @return file size in bytes
	 */
	fun size(path: String): Long

	/**
	 * Returns the display name or file name of a resource.
	 *
	 * @param path file path or URI
	 * @return resource name
	 */
	fun getName(path: String): String

	/**
	 * Returns the application's internal files directory when available.
	 *
	 * This method primarily exists for Android-based implementations.
	 *
	 * JVM implementations may return null.
	 *
	 * @return internal files directory or null
	 */
	fun getAndroidFilesDir(): File? = null

	/**
	 * Materializes a virtual or URI-based resource into a physical file
	 * hierarchy that can be accessed using standard filesystem APIs.
	 *
	 * This is primarily intended for SAF resources that need to be exposed
	 * as real files.
	 *
	 * JVM implementations may simply return the original path.
	 *
	 * @param path source path or URI
	 * @param outDir output directory
	 * @return materialized filesystem path
	 */
	fun materialize(path: String, outDir: String): String = path

	/**
	 * Removes files previously created by [materialize].
	 *
	 * Implementations may ignore this operation when materialization
	 * is not required.
	 *
	 * @param path materialized target identifier
	 */
	fun clearMaterialized(path: String) {}
}

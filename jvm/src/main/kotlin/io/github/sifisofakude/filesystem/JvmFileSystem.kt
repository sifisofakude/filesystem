package io.github.sifisofakude.filesystem


import java.io.InputStream
import java.io.OutputStream

import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

/**
 * JVM implementation of [FileSystemUtil].
 *
 * This implementation uses the standard Java file APIs
 * ([java.io.File], [java.nio.file.Files], and [java.nio.file.Paths])
 * to provide filesystem operations on local storage.
 *
 * Features include:
 *
 * - File and directory creation
 * - Recursive file discovery
 * - File copying and moving
 * - Stream-based file access
 * - Path normalization
 * - Metadata inspection
 *
 * ## Cross-platform usage
 *
 * The default implementations provided by [FileSystemUtil.copy],
 * [FileSystemUtil.move], and [FileSystemUtil.readText] operate entirely
 * through the [FileSystemUtil] abstraction.
 *
 * As a result, subclasses can override only the filesystem-specific
 * operations such as [openInputStream], [openOutputStream],
 * [createFile], and [createDirectory] while inheriting higher-level
 * functionality.
 *
 * This allows operations such as:
 *
 * - JVM → JVM copying
 * - SAF → SAF copying
 * - JVM → SAF copying
 * - SAF → JVM copying
 *
 * without changing the calling code.
 *
 * This implementation serves as the default filesystem backend and may
 * be extended by platform-specific implementations such as
 * [AndroidSafFileSystem].
 *
 * All paths are treated as local filesystem paths.
 */
open class JvmFileSystem : FileSystemUtil	{
	/**
	 * Returns the current working directory.
	 *
	 * @return current directory path or null if unavailable
	 */
	override open fun getCurrentDirectory(): String?	{
		return System.getProperty("user.dir")
	}

	/**
	 * Creates a directory and any missing parent directories.
	 *
	 * If the directory already exists, its absolute path is returned.
	 *
	 * @param path directory path
	 * @return resulting directory path, or null if creation failed
	 */
	override open fun createDirectory(path: String): String?	{
		val dir = File(path).apply { mkdirs() }
		if(dir.exists())	{
			return dir.absolutePath
		}
		return null
	}

	/**
	 * Creates a new file.
	 *
	 * Missing parent directories are created automatically.
	 *
	 * If the file already exists, no file is created and null is returned.
	 *
	 * @param path file path
	 * @return absolute path of the created file, or null if creation failed
	 *         or the file already exists
	 */
	override open fun createFile(path: String): String?	{
		val file = File(path)
		file.parentFile?.let	{
			it.mkdirs()
		}

		if(file.createNewFile())	{
			return file.absolutePath
		}
		return null
	}

	/**
	 * Opens an output stream for an existing file.
	 *
	 * The target file must already exist.
	 *
	 * @param path file path
	 * @return writable output stream, or null if the file does not exist
	 *         or is not a regular file
	 */
	override open fun openOutputStream(path: String): OutputStream?	{
		return if(exists(path) && isFile(path))	{
			File(path).outputStream()
		}else	{
			null
		}
	}
	

	/**
	 * Opens an input stream for reading from a file.
	 *
	 * @param path the file path
	 * @return an input stream for reading, or null if the stream could not be opened
	 */
	override open fun openInputStream(path: String): InputStream?	{
		var stream: InputStream? = null

		val file = File(path)
		if(file.exists() && file.isFile)	{
			stream = file.inputStream()
		}
		return stream
	}

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
	override open fun findFiles(directory: String, extensions: Set<String>): List<String>	{
		val results = mutableListOf<String>()
		
		val dir = File(directory)
		if(dir.isDirectory)	{
			val contents = dir.listFiles()
			contents?.forEach	{ file ->
				if(file.isFile && (extensions.isEmpty() || extensions.contains(file.extension)))	{
					results.add(file.absolutePath)
				}else if(file.isDirectory)	{
					results.addAll(findFiles(file.absolutePath,extensions))
				}
			}
		}
		return results
	}

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
	override open fun listFiles(path: String): List<String>	{
		return File(path).listFiles()
			?.map { it.absolutePath }?.toList() 
			?: emptyList()
	}

	/**
	 * Returns whether a file or directory exists.
	 *
	 * @param path the path to test
	 * @return `true` if the path exists, otherwise `false`
	 */
	override open fun exists(path: String): Boolean	{
		return File(path).exists()
	}

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
	override open fun delete(path: String): Boolean	{
		if(isDirectory(path))	{
			return File(path).deleteRecursively()
		}else	{
			return File(path).delete()
		}
	}

	/**
	 * Renames a file or directory.
	 *
	 * @param src the existing path
	 * @param target the new name or target path
	 * @return the resulting path, or null if the operation failed
	 */
	override open fun rename(src: String, target: String): String?	{
		val source = Paths.get(src)

		return try	{
			Files.move(source,source.resolveSibling(target))
				.toString()
		}catch(e: Exception)	{
			null
		}
	}
	
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
	override open fun resolveFiles(inputFiles: List<Any>,extensions: Set<String>): List<FileSource>	{
		val results = mutableListOf<FileSource>()

		inputFiles.forEach	{ file ->
			var doc: File? = null
			
			when(file)	{
				is File -> doc = file
				is String -> doc = File(file)
			}

			if(doc != null)	{
				if(
					doc.exists() && doc.isFile && 
					(extensions.isEmpty() || extensions.contains(doc.extension))
				)	{
					try	{
						results.add(
							FileSource(
								relativePath = doc.path,
								absolutePath = doc.absolutePath,
							)
						)
					}catch(e: Exception)	{}
				}else if(doc.exists() && doc.isDirectory)	{
					findFiles(doc.absolutePath,extensions).forEach	{
						val relativePath = it
							.replace("${doc.absolutePath}${File.separator}","")
							
						try	{
							results.add(
								FileSource(
									relativePath = relativePath,
									absolutePath = "${doc.absolutePath}${File.separator}$relativePath"
								)
							)
						}catch(e: Exception) {}
					}
				}
			}
		}
		return results
	}

	/**
	 * Converts a path to an absolute, normalized path.
	 *
	 * Relative segments such as "." and ".." are resolved using
	 * the platform path normalization rules.
	 *
	 * @param path file or directory path
	 * @return normalized absolute path
	 */
	override open fun resolvePath(path: String): String	{
		return Paths.get(File(path).absolutePath).normalize().toString()
	}

	/**
	 * Returns whether the specified path refers to a regular file.
	 *
	 * @param path the path to test
	 * @return `true` if the path refers to a file, otherwise `false`
	 */
	override open fun isFile(path: String): Boolean	{
		return File(path).isFile
	}

	/**
	 * Returns whether the specified path refers to a directory.
	 *
	 * @param path the path to test
	 * @return `true` if the path refers to a directory, otherwise `false`
	 */
	override open fun isDirectory(path: String): Boolean	{
		return File(path).isDirectory
	}

	/**
	 * Returns the last modification time of a file or directory.
	 *
	 * The returned value is expressed as the number of milliseconds since
	 * the Unix epoch (00:00:00 UTC on 1 January 1970).
	 *
	 * @param path the path to query
	 * @return the last modification time in milliseconds
	 */
	override open fun lastModified(path: String): Long	{
		return File(path).lastModified()
	}

	/**
	 * Returns the parent directory of the supplied path.
	 *
	 * @param path file or directory path
	 * @return absolute path of the parent directory, or null if the path
	 *         has no parent
	 */
	override open fun getParentFile(path: String): String?	{
		return File(path).parentFile?.absolutePath
	}

	/**
	 * Returns the size of a file in bytes.
	 *
	 * If the specified path refers to a directory, the result is
	 * implementation-defined.
	 *
	 * @param path the path to query
	 * @return the size of the file in bytes
	 */
	override open fun size(path: String): Long	{
		return File(path).length()
	}

	/**
	 * Returns the file or directory name portion of a path.
	 *
	 * Examples:
	 *
	 * ```kotlin
	 * getName("/home/user/file.txt") // file.txt
	 * getName("/tmp/build")          // build
	 * ```
	 *
	 * @param path file or directory path
	 * @return file or directory name
	 */
	override open fun getName(path: String): String	{
		return File(path).name
	}
}


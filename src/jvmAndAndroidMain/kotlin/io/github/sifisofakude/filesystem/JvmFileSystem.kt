package io.github.sifisofakude.filesystem


import java.io.InputStream
import java.io.OutputStream
import java.io.FileOutputStream

import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.io.Sink
import kotlinx.io.Source

/**
 * JVM implementation of [FileSystemUtil].
 *
 * This implementation provides filesystem operations for conventional
 * local filesystem paths using the Java standard library.
 *
 * It uses [java.io.File], [java.nio.file.Files], and [java.nio.file.Paths]
 * for filesystem access and provides support for:
 *
 * - File and directory creation
 * - Recursive file discovery
 * - File and directory copying
 * - File and directory moving
 * - File and directory deletion
 * - File and directory renaming
 * - Stream-based file access
 * - Path normalization
 * - File metadata inspection
 *
 * Higher-level operations inherited from [FileSystemUtil] operate through
 * the filesystem abstraction, allowing this implementation to be extended
 * by platform-specific filesystem implementations.
 *
 * All paths handled directly by this implementation are conventional
 * JVM filesystem paths.
 *
 * @see FileSystemUtil
 */
open class JvmFileSystem : FileSystemUtil	{
	/**
	 * Returns the current working directory of the JVM process.
	 *
	 * @return the current working directory, or `null` if it cannot be determined
	 */
	override open fun getCurrentDirectory(): String?	{
		return System.getProperty("user.dir")
	}

	/**
	 * Creates a directory and any missing parent directories.
	 *
	 * If the directory already exists, its normalized absolute path is returned.
	 *
	 * @param path path of the directory to create
	 * @return the absolute path of the directory, or `null` if creation failed
	 */
	override open fun createDirectory(path: String): String?	{
		val dir = File(path).apply { mkdirs() }
		if(dir.exists())	{
			return dir.absolutePath
		}
		return null
	}

	/**
	 * Creates a new empty file.
	 *
	 * Missing parent directories are created automatically.
	 *
	 * If a file already exists at [path], no new file is created and `null`
	 * is returned.
	 *
	 * @param path path of the file to create
	 * @return the absolute path of the newly created file, or `null` if the
	 *         file could not be created or already exists
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
	 * Combines a parent path and child name using the platform-specific
	 * filesystem separator.
	 *
	 * This method does not create or access the resulting filesystem resource.
	 *
	 * @param parent parent directory path
	 * @param child child file or directory name
	 * @return the combined filesystem path
	 */
	override fun combinePath(parent: String, child: String): String	{
		return "$parent${File.separator}$child"
	}

	/**
	 * Opens a writable stream for a file.
	 *
	 * When [append] is `false`, writing begins at the start of the file and
	 * existing contents may be replaced. When [append] is `true`, new data is
	 * written after the existing contents.
	 *
	 * The returned sink is buffered.
	 *
	 * @param path path of the file to open
	 * @param append whether data should be appended to the existing contents
	 * @return a writable [Sink], or `null` if the file could not be opened
	 */
	override open fun openSink(
		path: String, 
		append: Boolean
	): Sink?	{
		return  try	{
			FileOutputStream(path,append).asSink().buffered()
		}catch(_: Exception)	{
			null
		}
	}
	

	/**
	 * Opens a readable stream for a file.
	 *
	 * The returned source is buffered and must be closed by the caller.
	 *
	 * @param path path of the file to open
	 * @return a readable [Source], or `null` if the file could not be opened
	 */
	override open fun openSource(path: String): Source?	{
		val file = File(path)
		
		return try	{
			file.inputStream().asSource().buffered()
		}catch(_: Exception)	{
			null
		}
	}

	/**
	 * Recursively searches a directory for files.
	 *
	 * Subdirectories are traversed recursively. When [extensions] is non-empty,
	 * only files whose extensions are contained in the set are returned.
	 *
	 * Returned paths are absolute filesystem paths.
	 *
	 * @param directory directory from which the search begins
	 * @param extensions allowed file extensions; an empty set includes all files
	 * @return a list of matching absolute file paths
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
	 * Returns the immediate children of a directory.
	 *
	 * Unlike [findFiles], this method does not recursively traverse
	 * subdirectories.
	 *
	 * Returned paths are absolute filesystem paths.
	 *
	 * @param path path of the directory to list
	 * @return a list of child paths, or an empty list if the directory cannot
	 *         be read or contains no children
	 */
	override open fun listFiles(path: String): List<String>	{
		return File(path).listFiles()
			?.map { it.absolutePath }?.toList() 
			?: emptyList()
	}

	/**
	 * Determines whether a filesystem resource exists.
	 *
	 * Both files and directories are considered existing resources.
	 *
	 * @param path path of the resource to test
	 * @return `true` if the resource exists, otherwise `false`
	 */
	override open fun exists(path: String): Boolean	{
		return File(path).exists()
	}

	/**
	 * Moves a file or directory to the specified destination.
	 *
	 * The move is performed using the JVM filesystem's native move operation
	 * provided by [Files.move].
	 *
	 * If [dst] refers to an existing directory, the source resource is moved
	 * into that directory using the source resource's name.
	 *
	 * Directory moves are handled by the underlying filesystem and may move
	 * the directory and its contents as a single filesystem operation when
	 * supported by the underlying storage.
	 *
	 * @param src source file or directory path
	 * @param dst destination file or directory path
	 * @return the resulting destination path, or `null` if the operation failed
	 */
	override fun move(src: String, dst: String): String?	{
		val sourcePath = Paths.get(src)
		val destinationPath = Paths.get(dst)

		return try	{
			Files.move(sourcePath,destinationPath).toString()
		}catch(_: Exception)	{
			null
		}
	}

	/**
	 * Deletes a filesystem resource.
	 *
	 * Files are deleted directly. Directories are deleted recursively together
	 * with their contents.
	 *
	 * @param path path of the file or directory to delete
	 * @return `true` if the resource was successfully deleted, otherwise `false`
	 */
	override open fun delete(path: String): Boolean	{
		if(isDirectory(path))	{
			return File(path).deleteRecursively()
		}else	{
			return File(path).delete()
		}
	}

	/**
	 * Renames a file or directory within its current parent directory.
	 *
	 * The [target] parameter represents the new name of the resource rather
	 * than a complete destination path.
	 *
	 * @param src path of the file or directory to rename
	 * @param target new name of the resource
	 * @return the resulting path, or `null` if the rename operation failed
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
	 * Resolves a collection of filesystem inputs into [FileSource] objects.
	 *
	 * Each input may be a [File] or a filesystem path represented by a
	 * [String]. Files are included directly when they match the requested
	 * extension filter. Directory inputs are traversed recursively.
	 *
	 * For directory inputs, [FileSource.relativePath] preserves the file's
	 * location relative to the supplied directory.
	 *
	 * @param inputFiles files or filesystem paths to resolve
	 * @param extensions allowed file extensions; an empty set includes all files
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
	 * Resolves a filesystem path to an absolute, normalized path.
	 *
	 * Relative paths are resolved against the JVM process's current working
	 * directory. Relative path segments such as `.` and `..` are normalized
	 * according to the platform filesystem rules.
	 *
	 * @param path filesystem path to resolve
	 * @return the absolute, normalized filesystem path
	 */
	override open fun resolvePath(path: String): String	{
		return Paths.get(File(path).absolutePath).normalize().toString()
	}

	/**
	 * Determines whether the specified path refers to a regular file.
	 *
	 * @param path path to test
	 * @return `true` if the path exists and refers to a regular file,
	 *         otherwise `false`
	 */
	override open fun isFile(path: String): Boolean	{
		return File(path).isFile
	}

	/**
	 * Determines whether the specified path refers to a directory.
	 *
	 * @param path path to test
	 * @return `true` if the path exists and refers to a directory,
	 *         otherwise `false`
	 */
	override open fun isDirectory(path: String): Boolean	{
		return File(path).isDirectory
	}

	/**
	 * Returns the last modification time of a filesystem resource.
	 *
	 * The returned value is expressed as milliseconds since the Unix epoch
	 * (1970-01-01T00:00:00Z).
	 *
	 * @param path path of the resource
	 * @return last modification time in milliseconds
	 */
	override open fun lastModified(path: String): Long	{
		return File(path).lastModified()
	}

	/**
	 * Returns the parent directory of a filesystem resource.
	 *
	 * The returned path is absolute.
	 *
	 * @param path filesystem path
	 * @return absolute parent directory path, or `null` if the resource has
	 *         no parent
	 */
	override open fun getParentFile(path: String): String?	{
		return File(path).parentFile?.absolutePath
	}

	/**
	 * Returns the size of a filesystem resource in bytes.
	 *
	 * For directories, the value is the directory size reported by the
	 * underlying filesystem and does not represent the recursive size of
	 * its contents.
	 *
	 * @param path path of the resource
	 * @return resource size in bytes
	 */
	override open fun size(path: String): Long	{
		return File(path).length()
	}

	/**
	 * Returns the final name component of a filesystem path.
	 *
	 * For a file, this is the file name. For a directory, this is the
	 * directory name.
	 *
	 * @param path filesystem path
	 * @return the file or directory name
	 */
	override open fun getName(path: String): String	{
		return File(path).name
	}
}


package io.github.sifisofakude.filesystem

import java.io.File
import java.io.InputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.charset.StandardCharsets

import kotlin.io.normalize

/**
 * JVM implementation of [FileSystemUtil] backed by the standard Java
 * `java.io.File` and `java.nio.file` APIs.
 *
 * This implementation provides concrete filesystem access for JVM-based
 * environments such as desktop applications, CLI tools, build systems,
 * and server-side utilities.
 *
 * It acts as the reference implementation of the [FileSystemUtil] contract
 * and defines the expected behavior for other platform implementations
 * (e.g. Android SAF or in-memory virtual filesystems).
 *
 * ## Path Resolution
 *
 * Relative paths are resolved against the JVM process working directory
 * (`user.dir`). All resolved paths are normalized using `java.nio.file.Path`
 * to ensure consistent behavior across platforms and OS differences.
 *
 * ## FileSource Semantics
 *
 * This implementation produces [FileSource] objects via [resolveFiles].
 * Each `FileSource` contains:
 * - a stable `relativePath` used for packaging, compilation, or archiving
 * - an `absolutePath` that can be used for direct filesystem access
 *
 * File contents are NOT stored in memory; they must be accessed using
 * [openInputStream] or [openOutputStream].
 *
 * ## Directory Traversal
 *
 * Recursive file discovery is performed using [findFiles], which walks
 * the filesystem tree depth-first starting from a given directory.
 *
 * ## Error Handling
 *
 * This implementation prefers safe failure:
 * - Non-existent paths return empty results or `null`
 * - Unsupported operations do not throw unless explicitly required
 * - Some internal file resolution errors may be skipped during traversal
 *
 * ## Thread Safety
 *
 * This class is not explicitly synchronized. Concurrent usage is safe
 * only if the underlying filesystem is not concurrently modified.
 *
 * ## Intended Use Cases
 *
 * - Kotlin/Java build tools
 * - Dependency resolvers (e.g. cmdapk)
 * - Archive generation (JAR/ZIP/AAR)
 * - Code analysis and compilation pipelines
 *
 * @since 0.1.0
 */
class JvmFileSystem : FileSystemUtil	{
	/**
   * The current working directory of the running JVM process.
   */
	private val currentDir = System.getProperty("user.dir")
	
	/**
	 * Resolves a path into an absolute, normalized filesystem path.
	 *
	 * If the input path is relative, it is resolved against the JVM working
	 * directory (`user.dir`). Absolute paths are returned unchanged except for
	 * normalization.
	 *
	 * Normalization is performed using `java.nio.file.Paths` to remove redundant
	 * path segments such as `.` and `..`.
	 *
	 * @param path the input path, either relative or absolute
	 * @return a normalized absolute path string
	 */
	override fun resolvePath(path: String): String	{
		var resolvedPath = if(!File(path).isAbsolute)	{
			File(currentDir,path).path
		}else	{
			path
		}
		return Paths.get(resolvedPath).normalize().toString()
	}

	/**
	 * Returns the JVM process working directory.
	 *
	 * This is the base directory used when resolving relative paths.
	 *
	 * @return the current working directory path
	 */
	override fun getCurrentDirectory(): String?	{
		return currentDir
	}
	
	/**
   * Resolves a list of file inputs into readable [FileSource] objects.
   *
   * Inputs may be:
   * <ul>
   *     <li>{@link java.io.File} objects</li>
   *     <li>String file paths</li>
   * </ul>
   *
   *
	 * If a file cannot be opened due to permissions or I/O errors,
	 * it will be skipped and will not appear in the result list.
	 *
   * If a directory is provided, all files matching the provided extensions
   * will be recursively discovered and returned.
   *
   * @param inputFiles files or file paths to resolve
   * @param extensions allowed file extensions (empty set allows all files)
   * @return list of resolved [FileSource] objects
   */
	override fun resolveFiles(
	  inputFiles: List<Any>,
	  extensions: Set<String>
	): List<FileSource> {
	
    val results = mutableListOf<FileSource>()

    for (input in inputFiles) {
      val file = when (input) {
        is File -> input
        is String -> File(resolvePath(input))
        else -> continue
      }

      if (!file.exists()) continue

      if (file.isFile) {
        if (extensions.isEmpty() || file.extension in extensions) {
          results.add(
            FileSource(
              relativePath = file.name,
              absolutePath = file.absolutePath
            )
          )
        }
        continue
      }

      if (file.isDirectory) {
        val root = file.absolutePath

        results += findFiles(root, extensions).map { absolute ->
          val relative = absolute.removePrefix(root + File.separator)

          FileSource(
            relativePath = relative,
            absolutePath = absolute
          )
        }
      }
    }

    return results
	}

	/**
	 * Copies one or more files or directories to a destination directory.
	 *
	 * If a source is a directory, its contents are copied recursively while
	 * preserving directory structure.
	 *
	 * Existing files are replaced depending on the value of [overwrite].
	 *
	 * This operation uses Java NIO (`Files.copy`, `Files.walk`) internally.
	 *
	 * @param src list of source file or directory paths
	 * @param dst destination directory path
	 * @param overwrite whether existing files should be replaced
	 */
	override fun copy(
	    src: List<String>,
	    dst: String,
	    overwrite: Boolean
	) {
    val destinationRoot = Path.of(dst)

    Files.createDirectories(destinationRoot)

    src.forEach { sourcePathString ->
      val source = Path.of(sourcePathString)

      require(Files.exists(source)) {
          "Source does not exist: $source"
      }

      val target = destinationRoot.resolve(source.fileName)

      if (Files.isDirectory(source)) {

          Files.walk(source).forEach { current ->
              val relative = source.relativize(current)
              val destination = target.resolve(relative)

              if (Files.isDirectory(current)) {
                  Files.createDirectories(destination)
              } else {
                  destination.parent?.let(Files::createDirectories)

                  if (overwrite) {
                      Files.copy(
                          current,
                          destination,
                          StandardCopyOption.REPLACE_EXISTING
                      )
                  } else {
                      Files.copy(current, destination)
                  }
              }
          }

      } else {
          if (overwrite) {
              Files.copy(
                  source,
                  target,
                  StandardCopyOption.REPLACE_EXISTING
              )
          } else {
              Files.copy(source, target)
          }
      }
    }
	}

	/**
	 * Recursively scans a directory and returns all matching files.
	 *
	 * Traversal is depth-first starting from the given directory.
	 *
	 * If [extensions] is not empty, only files with matching extensions are
	 * included in the result.
	 *
	 * Returned paths are absolute filesystem paths.
	 *
	 * This method does not return directories.
	 *
	 * @param directory root directory to scan
	 * @param extensions allowed file extensions; empty set allows all files
	 * @return list of absolute file paths
	 */
	override fun findFiles(
	    directory: String,
	    extensions: Set<String>
	): List<String> {
	
	   val root = File(directory)

	   if (!root.isDirectory) return emptyList()

	   val rootPath = root.absolutePath

	   val results = mutableListOf<String>()

	   fun walk(current: File) {
       val children = current.listFiles() ?: return

       for (file in children) {

         if (file.isDirectory) {
           walk(file)
           continue
         }

         if (file.isFile) {
           if (extensions.isEmpty() || file.extension in extensions) {
             results.add(file.absolutePath)
           }
         }
       }
	   }

	   walk(root)

	   return results
	}


	/**
	 * Checks whether a file or directory exists at the given path.
	 *
	 * @param path filesystem path
	 * @return true if the path exists, otherwise false
	 */
	override fun exists(path: String): Boolean	{
		return File(path).exists()
	}
	
	/**
	 * Creates a directory at the specified path if it does not already exist.
	 *
	 * If the directory already exists, the existing path is returned.
	 *
	 * This method does not strictly distinguish between file and directory
	 * semantics; callers are responsible for providing appropriate paths.
	 *
	 * @param path directory path to create
	 * @return absolute path of the created or existing directory, or null if failed
	 */
	override fun createDirectory(path: String): String? {
    val target = File(path)

    val dir = if (target.isFile || target.extension.isNotEmpty()) {
        target.parentFile ?: target
    } else {
        target
    }

    return if (dir.mkdirs() || dir.exists()) {
        dir.absolutePath
    } else null
	}

	/**
	 * Creates a new file at the specified path.
	 *
	 * If parent directories do not exist, file creation may fail depending on
	 * filesystem state.
	 *
	 * If the file already exists, this method returns null.
	 *
	 * @param path file path
	 * @return the created file path, or null if creation failed
	 */
	override fun createFile(path: String): String?	{
		var createdFile: String? = null

		File(path).apply	{
			if(createNewFile()) createdFile = path
		}
		return createdFile
	}


	/**
	 * Lists the immediate children of a directory.
	 *
	 * This method does not traverse subdirectories recursively.
	 *
	 * Only direct children of the given directory are returned.
	 *
	 * @param path directory path
	 * @return list of absolute paths of child files and directories
	 */
	override fun listFiles(path: String): List<String>	{
		val result = mutableListOf<String>()
		
		File(path).apply	{
			if(isDirectory)	{
				listFiles().forEach	{ result.add(it.path) }
			}
		}
		return result
	}
	
	/**
   * Opens an [OutputStream] for writing to a file.
   *
   * Parent directories will be automatically created if they do not exist.
   *
   * @param path the file path
   * @return an output stream for the file
   */
	override fun openOutputStream(path: String): OutputStream? {
    val file = File(path)

    file.parentFile?.let {
        createDirectory(it.path)
    }

    if (!file.exists()) {
        file.createNewFile()
    }

    return file.outputStream()
	}

	/**
	 * Opens an input stream for reading a file.
	 *
	 * The file must exist and must be a regular file.
	 *
	 * @param path file path
	 * @return input stream for reading, or null if file does not exist
	 */
	override fun openInputStream(path: String): InputStream? {
	    val file = File(path)
	    return if (file.exists() && file.isFile) file.inputStream() else null
	}

	/**
	 * Reads the entire contents of a file as a UTF-8 string.
	 *
	 * Internally uses [openInputStream] and reads the stream fully.
	 *
	 * @param path file path
	 * @return file contents as a string, or null if reading fails
	 */
	override fun readText(path: String): String?	{
		var text: String? = null

		openInputStream(path)?.use 	{ stream ->
			val br = BufferedReader(
				InputStreamReader(stream, StandardCharsets.UTF_8)
			)

			text = br.readText()
		}
		return text
	}

	/**
	 * Deletes a file or directory.
	 *
	 * If the target is a directory, deletion is performed recursively.
	 *
	 * @param path file or directory path
	 * @return true if deletion was successful, otherwise false
	 */
	override fun delete(path: String): Boolean {
    val file = File(path)

    if (!file.exists()) {
        return false
    }

    return if (file.isDirectory) {
        file.deleteRecursively()
    } else {
        file.delete()
    }
	}

	/**
	 * Renames a file or moves it within the same directory.
	 *
	 * The target name is treated as a sibling within the same parent directory.
	 *
	 * @param src existing file path
	 * @param target new file name (not full path)
	 * @return new path if successful, otherwise null
	 */
	override fun rename(src: String, target: String): String? {
    return try {
        val source = Paths.get(src)
        Files.move(source, source.resolveSibling(target)).toString()
    } catch (e: Exception) {
        null
    }
	}

	/**
	 * Moves a file or directory to a new location.
	 *
	 * If the destination already exists, it may be replaced depending on
	 * filesystem behavior and implementation flags.
	 *
	 * @param src source path
	 * @param dst destination path
	 * @return new path if successful, otherwise null
	 */
	override fun move(src: String, dst: String): String?	{
		val source = Paths.get(src)
		val destination = Paths.get(dst)
		
		return try	{
			Files.move(source,destination,StandardCopyOption.REPLACE_EXISTING)
				.toString()
		}catch(e: Exception)	{
			null
		}
	}

	/**
	 * Checks whether the given path refers to a regular file.
	 *
	 * @param path filesystem path
	 * @return true if path is a file, otherwise false
	 */
	override fun isFile(path: String): Boolean	{
		return File(path).isFile
	}

	/**
	 * Checks whether the given path refers to a directory.
	 *
	 * @param path filesystem path
	 * @return true if path is a directory, otherwise false
	 */
	override fun isDirectory(path: String): Boolean	{
		return File(path).isDirectory
	}

	/**
	 * Returns the last modification timestamp of a file or directory.
	 *
	 * The value is expressed as milliseconds since the Unix epoch.
	 *
	 * If the file does not exist, -1 is returned.
	 *
	 * @param path filesystem path
	 * @return last modified time in milliseconds, or -1 if not found
	 */
	override fun lastModified(path: String): Long	{
		val file = File(path)
		return if(file.exists())	{
			file.lastModified()
		}else	{
			-1
		}
	}

	/**
	 * Returns the size of a file in bytes.
	 *
	 * For directories, the result is implementation-dependent.
	 *
	 * @param path filesystem path
	 * @return size in bytes
	 */
	override fun size(path: String): Long	{
		return File(path).length()
	}

}


fun main()	{
	val jvm = JvmFileSystem()
	val tmpOut = jvm.listFiles("tmpOut")
	// jvm.copyFiles(tmpOut,"test/")
	// jvm.remove("test/io")
}

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
 * JVM implementation of [FileSystemUtil].
 *
 * <p>
 * This implementation uses the standard Java {@link java.io.File} API to provide
 * filesystem access on desktop JVM environments.
 * </p>
 *
 * <p>
 * It supports:
 * <ul>
 *     <li>Resolving relative paths</li>
 *     <li>Finding files recursively</li>
 *     <li>Opening input and output streams</li>
 *     <li>Creating directories</li>
 * </ul>
 * </p>
 *
 * This class is intended for JVM platforms such as:
 * <ul>
 *     <li>Desktop applications</li>
 *     <li>CLI tools</li>
 *     <li>Server applications</li>
 * </ul>
 *
 * @since 0.1.0
 */
class JvmFileSystem : FileSystemUtil	{
	/**
   * The current working directory of the running JVM process.
   */
	private val currentDir = System.getProperty("user.dir")
	
	/**
   * Resolves a path into an absolute path.
   *
   * If the provided path is relative, it will be resolved against the
   * current working directory of the JVM.
   *
   * @param path the input path (relative or absolute)
   * @return the resolved absolute path
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
   * Returns the current working directory.
   *
   * @return the current directory path
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
	override fun resolveFiles(inputFiles: List<Any>,extensions: Set<String>): List<FileSource>	{
		val results = mutableListOf<FileSource>()
		val currentDir = System.getProperty("user.dir")

		inputFiles.forEach	{ file ->
			var doc: File? = null
			var relativePath = ""
			var absolutePath = ""
			
			if(file is File)	{
				doc = file
				absolutePath = file.absolutePath
			}
			
			if(file is String)	{
				absolutePath = resolvePath(file)
				
				doc = File(absolutePath)
			}

			if(doc != null)	{
				if(
					doc.exists() && doc.isFile && 
					(extensions.isEmpty() || extensions.contains(doc.extension))
				)	{
					try	{
						results.add(
							FileSource(
								relativePath = doc.name,
								absolutePath = absolutePath,
								stream = doc.inputStream()
							)
						)
					}catch(e: Exception)	{}
				}else if(doc.exists() && doc.isDirectory)	{
					findFiles(doc.absolutePath,extensions).forEach	{
						val relativePath = it.replace("${doc.absolutePath}${File.separator}","")
						try	{
							results.add(
								FileSource(
									stream = File(it).inputStream(),
									relativePath = relativePath,
									absolutePath = doc.absolutePath
								)
							)
						}catch(e: Exception) {}
					}
				}
			}
		}
		return results
	}

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
   * Recursively finds files in a directory.
   *
   * If extensions are provided, only files matching those extensions
   * will be included.
   *
   * @param directory the directory to search
   * @param extensions allowed file extensions
   * @return list of absolute file paths
   */
	override fun findFiles(directory: String, extensions: Set<String>): List<String>	{
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

	override fun exists(path: String): Boolean	{
		return File(path).exists()
	}
	
	/**
   * Creates a directory if it does not exist.
   *
   * If the provided path appears to reference a file, the parent
   * directory will be created instead.
   *
   * @param path the directory or file path
   * @return the created directory path, or null if creation failed
   */
	override fun createDirectory(path: String): String?	{
		var dir = File(path)
		var file: String? = null
		if(!dir.extension.isEmpty() && dir.parent != null)	{
			file = dir.name
			dir = File(dir.parent)
		}
		
		if((!dir.exists() && dir.mkdirs()) || dir.exists())	{
			if(file != null)	{
				dir = File(dir,file)
			}
			return dir.absolutePath
		}
		return null
	}

	override fun createFile(path: String): String?	{
		var createdFile: String? = null

		File(path).apply	{
			if(createNewFile()) createdFile = path
		}
		return createdFile
	}

	override fun listFiles(path: String): List<String>	{
		val result = mutableListOf<String>()
		
		File(path).apply	{
			if(isDirectory)	{
				listFiles().forEach	{ result.add(it.path) }
			}
		}
		return result
	}

	override fun remove(path: String): Boolean {
    return try {
	    val target = Path.of(path)

	    if (!Files.exists(target)) {
        return false
	    }

	    if (Files.isDirectory(target)) {
        Files.walk(target)
        	.sorted(Comparator.reverseOrder())
          .forEach(Files::delete)
	    } else {
        Files.delete(target)
	    }

	    true
    } catch (_: Exception) {
       false
    }
	}
	
	/**
   * Opens an [OutputStream] for writing to a file.
   *
   * Parent directories will be automatically created if they do not exist.
   *
   * @param path the file path
   * @return an output stream for the file
   */
	override fun openOutputStream(path: String): OutputStream?	{
		createDirectory(path)
		
		val file = File(path).apply	{
			if(!exists()) createNewFile()
		}
		
		return file.outputStream()
	}

	override fun openInputStream(path: String): InputStream?	{
		var stream: InputStream? = null

		val file = File(path)
		if(file.exists() && file.isFile)	{
			stream = file.inputStream()
		}
		return stream
	}

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

	override fun delete(path: String): Boolean	{
		return File(path).delete()
	}

	override fun rename(src: String, target: String): String?	{
		val source = Paths.get(src)

		return try	{
			Files.move(source,source.resolveSibling(target))
				.toString()
		}catch(e: Exception)	{
			null
		}
	}

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

	override fun isFile(path: String): Boolean	{
		return File(path).isFile
	}

	override fun isDirectory(path: String): Boolean	{
		return File(path).isDirectory
	}

	override fun lastModified(path: String): Long	{
		val file = File(path)
		return if(file.exists())	{
			file.lastModified()
		}else	{
			-1
		}
	}

}


fun main()	{
	val jvm = JvmFileSystem()
	val tmpOut = jvm.listFiles("tmpOut")
	// jvm.copyFiles(tmpOut,"test/")
	jvm.remove("test/io")
}

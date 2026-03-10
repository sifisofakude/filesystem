package io.github.sifisofakude.filesystem

import java.io.File
import java.io.InputStream
import java.io.OutputStream

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
	fun resolvePath(path: String): String	{
		var resolvedPath = if(!File(path).isAbsolute)	{
			File(currentDir,path).path
		}else	{
			path
		}
		return resolvedPath
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
			
			if(file is File)	{
				doc = file
			}
			
			if(file is String)	{
				val absolutePath = resolvePath(file)
				
				doc = File(absolutePath)
			}

			if(doc != null)	{
				if(doc.exists() && doc.isFile)	{
					try	{
						results.add(
							FileSource(
								relativePath = doc.name,
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
									relativePath = relativePath
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
			dir = File(dir.parent!!)
		}
		
		if((!dir.exists() && dir.mkdirs()) || dir.exists())	{
			if(file != null)	{
				dir = File(dir,file)
			}
			return dir.absolutePath
		}
		return null
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
		val file = File(path)
		val parent = file.getParent()
		if(parent != null)	{
			createDirectory(parent)
		}
		return file.outputStream()
	}
}

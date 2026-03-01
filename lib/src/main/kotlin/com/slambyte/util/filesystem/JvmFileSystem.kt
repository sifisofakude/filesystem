package com.slambyte.util.filesystem

import java.io.File
import java.io.InputStream
import java.io.OutputStream

class JvmFileSystem : FileSystemUtil	{
	private val currentDir = System.getProperty("user.dir")
	
	fun resolvePath(path: String): String	{
		var resolvedPath = if(!File(path).isAbsolute)	{
			File(currentDir,path).path
		}else	{
			path
		}
		return resolvedPath
	}

	override fun getCurrentDirectory(): String?	{
		return currentDir
	}
	
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
					results.add(
						FileSource(
							relativePath = doc.name,
							stream = doc.inputStream()
						)
					)
				}else if(doc.exists() && doc.isDirectory)	{
					findFiles(doc.absolutePath,extensions).forEach	{
						val relativePath = it.replace("${doc.absolutePath}${File.separator}","")

						results.add(
							FileSource(
								stream = File(it).inputStream(),
								relativePath = relativePath
							)
						)
					}
				}
			}
		}
		return results
	}

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
	
	override fun openOutputStream(path: String): OutputStream?	{
		val file = File(path)
		val parent = file.getParent()
		if(parent != null)	{
			createDirectory(parent)
		}
		return file.outputStream()
	}
}

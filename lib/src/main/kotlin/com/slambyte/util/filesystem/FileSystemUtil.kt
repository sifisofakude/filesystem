package com.slambyte.util.filesystem

import java.io.InputStream
import java.io.OutputStream

data class FileSource(
	val relativePath: String?,
	val stream: InputStream?
)

interface FileSystemUtil	{
	fun getCurrentDirectory(): String?
	fun createDirectory(path: String): String?
	fun openOutputStream(path: String): OutputStream?
	fun findFiles(directory: String,extensions: Set<String>): List<String>
	fun resolveFiles(inputFiles: List<Any>,extensions: Set<String>): List<FileSource>
}

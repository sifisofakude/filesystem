package io.github.sifisofakude.filesystem

import java.io.InputStream
import java.io.OutputStream

fun FileOperation.openInputStream(): InputStream?	{
	val fs = FileSystems.current
	if(fs !is JvmFileSystem) return null
	
	return fs.openInputStream(path)
}

@JvmOverloads
fun FileOperation.openOutputStream(append: Boolean = false): OutputStream?	{
	val fs = FileSystems.current
	if(fs !is JvmFileSystem) return null
	
	return fs.openOutputStream(path,append)
}

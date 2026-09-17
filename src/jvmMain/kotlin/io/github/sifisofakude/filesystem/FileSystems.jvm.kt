package io.github.sifisofakude.filesystem

object FileSystems	{
	val current: FileSystemUtil by lazy	{
	    JvmFileSystem()
	}
}


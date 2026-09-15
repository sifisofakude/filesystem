package io.github.sifisofakude.filesystem

actual object FileSystems	{
	actual val current: FileSystemUtil by lazy	{
	    JvmFileSystem()
	}
}


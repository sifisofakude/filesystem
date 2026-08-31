package io.github.sifisofakude.filesystem

import android.content.Context
import androidx.startup.Initializer

actual object FileSystems	{
	actual val current: FileSystemUtil by lazy	{
		AndroidSafFileSystem(ContextProvider.appContext)
	}
}

class ContextProvider : Initializer<Unit>	{
	companion object	{
		lateinit val appContext: Context
			private set
	}

	fun create(context: Context)	{
		appContext = context.applicationContext
	}

	fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}

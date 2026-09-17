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
		lateinit var appContext: Context
			private set
	}

	override fun create(context: Context)	{
		appContext = context.applicationContext
	}

	override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}

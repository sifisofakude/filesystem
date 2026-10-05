package io.github.sifisofakude.filesystem

import android.content.Context
import androidx.startup.Initializer

actual object FileSystems	{
	actual val current: FileSystemUtil by lazy	{
		AndroidSafFileSystem(ContextProvider.appContext)
	}
}

/**
 * Provides the application context used to initialize the Android filesystem.
 *
 * AndroidX Startup initializes this provider before [FileSystems.current]
 * is accessed, allowing the filesystem implementation to use an application
 * scoped [Context] without requiring application code to perform manual
 * initialization.
 */
class ContextProvider : Initializer<Unit>	{
	companion object	{
		/**
		 * Application-scoped context used by the Android filesystem implementation.
		 */
		lateinit var appContext: Context
			private set
	}

	override fun create(context: Context)	{
		appContext = context.applicationContext
	}

	override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}

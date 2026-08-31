/**
 * JVM implementation of [FileSystems].
 *
 * Provides the default filesystem implementation for JVM platforms through
 * [JvmFileSystem].
 *
 * The filesystem instance is initialized lazily, so [JvmFileSystem] is not
 * created until [current] is first accessed.
 *
 * @see FileSystems
 * @see JvmFileSystem
 */
actual object FileSystems {

	/**
	 * The filesystem implementation used by the current JVM platform.
	 *
	 * This value is initialized lazily and provides a [JvmFileSystem] instance.
	 */
	actual val current: FileSystemUtil by lazy {
		JvmFileSystem()
	}
}

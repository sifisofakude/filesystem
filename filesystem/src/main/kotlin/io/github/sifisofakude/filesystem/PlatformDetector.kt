package io.github.sifisofakude.filesystem

/**
 * Detects the underlying platform (operating system and runtime environment).
 *
 * <p>
 * Useful for cross-platform libraries to determine whether the code is running on:
 * <ul>
 *     <li>Windows, macOS, Linux</li>
 *     <li>Android</li>
 *     <li>Termux (Linux on Android)</li>
 * </ul>
 * </p>
 *
 * Example usage:
 * ```kotlin
 * val detector = PlatformDetector()
 * if(detector.isAndroid()) { ... }
 * if(detector.isDesktop(detector.detectPlatform()!!)) { ... }
 * ```
 *
 * @since 0.1.0
 */
class PlatformDetector	{
	/**
   * Lazily detected platform string.
   */
	private val platform by lazy	{ detectPlatform() }
	
	/**
   * Detects the current platform.
   *
   * @return one of "Windows", "Mac", "Linux", "Android", "Termux", or null if unknown
   */
	fun detectPlatform(): String?	{
		val os = System.getProperty("os.name")?.lowercase()
		val vm = System.getProperty("java.vm.name")?.lowercase()
	
		var platform: String? = null

		if(os == null) return null
		
		when	{
			os.contains("win") -> platform = "Windows"
			os.contains("mac") -> platform = "Mac"
			os.contains("linux") ->	{
				platform = "Linux"
				val vendor = System.getProperty("java.vendor")
				if(vendor == "Termux") platform = "Termux"
				if(vm != null && vm.contains("dalvik")) platform = "Android"
			}
		}
		return platform
	}
	
	/**
   * Checks if the platform is a desktop OS.
   *
   * Desktop OSes include Windows, Mac, and Linux (excluding Termux/Android).
   *
   * @param platform platform string to check
   * @return true if the platform is desktop, false otherwise
   */
	fun isDesktop(platform: String): Boolean	{
		return when(platform.lowercase())	{
			"windows","mac","linux" -> true
			else -> false
		}
	}

	/**
   * Checks if the platform is Android.
   *
   * @return true if Android, false otherwise
   */
	fun isAndroid(): Boolean = platform == "Android"

	/**
   * Checks if the platform is Windows.
   *
   * @return true if Windows, false otherwise
   */
	fun isWindows(): Boolean = platform == "Windows"

	/**
   * Checks if the platform is macOS.
   *
   * @return true if Mac, false otherwise
   */
	fun isMac(): Boolean = platform == "Mac"

	/**
   * Checks if the platform is Linux (excluding Android/Termux).
   *
   * @return true if Linux, false otherwise
   */
	fun isLinux(): Boolean = platform == "Linux"

	/**
   * Checks if the platform is Termux (Linux on Android).
   *
   * @return true if Termux, false otherwise
   */
	fun isTermux(): Boolean = platform == "Termux"
}


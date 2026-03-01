package com.slambyte.util.filesystem

class PlatformDetector	{
	private val platform by lazy	{ detectPlatform() }
	
	fun detectPlatform(): String?	{
		val os = System.getProperty("os.name")?.lowercase()
		val vm = System.getProperty("java.vm.name")?.lowercase()
	
		var platform: String? = null

		if(os == null) return null
		
		if(os.contains("win"))	{
			platform = "Windows"
		}else if(os.contains("mac"))	{
			platform = "Mac"
		}else if(os.contains("linux"))	{
			platform = "Linux"

			val vendor = System.getProperty("java.vendor")
			if(vendor == "Termux") platform = "Termux"
			
			if(vm != null && vm.contains("dalvik"))	{
				platform = "Android"
			}
		}
		return platform
	}
	
	fun isDesktop(platform: String): Boolean	{
		var desktop = false
		when(platform.lowercase())	{
			"windows","mac","linux" -> desktop = true
			else -> desktop = false
		}
		return desktop
	}

	fun isAndroid(): Boolean = platform == "Android"

	fun isWindows(): Boolean = platform == "Windows"

	fun isMac(): Boolean = platform == "Mac"

	fun isLinux(): Boolean = platform == "Linux"

	fun isTermux(): Boolean = platform == "Termux"
}


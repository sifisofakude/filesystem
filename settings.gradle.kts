pluginManagement	{
	plugins	{
		id("org.jetbrains.kotlin.jvm") version "2.3.10" apply false
		id("org.jetbrains.kotlin.android") version "2.3.10" apply false
		id("com.android.library") version "8.13.2" apply false
	}

	repositories	{
		google()
		gradlePluginPortal()
		mavenCentral()
	}
}

dependencyResolutionManagement	{
	repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
	repositories  {
		google()
		mavenCentral()
	}
}

rootProject.name = "filesystem"

include(":android")
include(":common")
include(":jvm")

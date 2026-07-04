plugins	{
	id("org.jetbrains.kotlin.android")
	id("com.android.library")
}

android	{
  namespace = "com.example.filesystem.android"
	compileSdk = 36
	
	defaultConfig {
	  minSdk = 23
	}
}

dependencies  {
  implementation(project(":common"))
  implementation("androidx.documentfile:documentfile:1.1.0")
}


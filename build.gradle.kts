plugins {
  id("com.vanniktech.maven.publish") apply false
	id("org.jetbrains.kotlin.jvm") apply false
	id("org.jetbrains.kotlin.android") apply false
	id("org.jetbrains.kotlin.multiplatform") apply false
	id("com.android.library") apply false
}

allprojects {
  group = "io.github.sifisofakude"
  version = "0.1.1"
}

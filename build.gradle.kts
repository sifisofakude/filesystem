plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp)
    alias(libs.plugins.maven.publish)
}

kotlin {
    jvm()

    val iosArm64Target = iosArm64()
    val iosSimulatorArm64Target = iosSimulatorArm64()
    val iosX64Target = iosX64()

    jvmToolchain(21)

    android {
        namespace = "io.github.sifisofakude.filesystem"
        compileSdk = 36
        minSdk = 24
    }

    sourceSets {
        val commonMainSourceSet = named("commonMain").get()

        commonMain {
            dependencies {
                implementation(libs.kotlinx.io.core)
            }
        }

        val jvmAndAndroidMain = create("jvmAndAndroidMain") {
            dependsOn(commonMainSourceSet)
        }

        named("jvmMain") {
            dependsOn(jvmAndAndroidMain)
        }

        named("androidMain") {
            dependsOn(jvmAndAndroidMain)

            dependencies {
                implementation(libs.androidx.documentfile)
                implementation(libs.androidx.startup)
            }
        }
    }
}

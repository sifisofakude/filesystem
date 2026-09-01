plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp)
    alias(libs.plugins.maven.publish)
}

kotlin {
    jvm()

    iosArm64()
    iosSimulatorArm64()
    iosX64()

    jvmToolchain(21)

    androidLibrary {
        namespace = "io.github.sifisofakude.filesystem"
        compileSdk = 36
        minSdk = 24
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlinx.io.core)
            }
        }

        val commonMainSourceSet = named("commonMain").get()

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

        named("iosMain") {
            dependsOn(commonMainSourceSet)
        }
    }
}

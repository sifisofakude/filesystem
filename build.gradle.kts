plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp)
    alias(libs.plugins.maven.publish)
    signing
}

version = "1.0.0-beta"
group = "io.github.sifisofakude.filesystem"

kotlin {
    jvm()

    iosArm64()
    iosSimulatorArm64()
    iosX64()

    applyDefaultHierarchyTemplate()

    jvmToolchain(21)

    android {
        namespace = "io.github.sifisofakude.filesystem"
        compileSdk = 36
        minSdk = 24

        withDeviceTest {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.io.core)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        val jvmAndAndroidMain = create("jvmAndAndroidMain") {
            dependsOn(commonMain.get())
        }

        jvmMain {
            dependsOn(jvmAndAndroidMain)
        }

        androidMain {
            dependsOn(jvmAndAndroidMain)

            dependencies {
                implementation(libs.androidx.startup)
            }
        }

        val androidDeviceTest by getting {
            dependencies {
                implementation("androidx.test.ext:junit:1.2.1")
                implementation("androidx.test:core:1.6.1")
                implementation("androidx.test:runner:1.6.1")
                implementation("androidx.test.uiautomator:uiautomator:2.3.0")
            }
        }
    }
}

publishing {
    repositories {
        maven {
            name = "CentralStaging"
            url = uri(layout.buildDirectory.dir("central-staging"))
        }
    }
}

signing {
		useInMemoryPgpKeys(
        providers.environmentVariable("GPG_SIGNING_KEY").orNull,
        providers.environmentVariable("GPG_PASSPHRASE").orNull
    )
    
    sign(publishing.publications)
}

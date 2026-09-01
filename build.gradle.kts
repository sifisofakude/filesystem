kotlin {
    jvm()

    iosArm64()
    iosSimulatorArm64()
    iosX64()

    applyDefaultHierarchyTemplate()

    jvmToolchain(21)

    androidLibrary {
        namespace = "io.github.sifisofakude.filesystem"
        compileSdk = 36
        minSdk = 24
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.io.core)
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
                implementation(libs.androidx.documentfile)
                implementation(libs.androidx.startup)
            }
        }
    }
}

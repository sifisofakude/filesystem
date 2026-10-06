import org.gradle.jvm.tasks.Jar
import org.gradle.api.publish.maven.MavenPublication

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.dokka)
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


val javadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")

    val readme = layout.buildDirectory.file(
        "javadoc-placeholder/README.md"
    )

    doFirst {
        val file = readme.get().asFile

        file.parentFile.mkdirs()

        file.writeText(
            """
            # Javadoc

            This artifact is a placeholder Javadoc JAR for the
            `io.github.sifisofakude.filesystem` Kotlin Multiplatform library.

            The library provides Kotlin Multiplatform documentation through
            the project's documentation:

            https://github.com/sifisofakude/filesystem

            Kotlin Multiplatform publications cannot currently be generated
            using Dokka's Javadoc output format.

            """.trimIndent()
        )
    }

    from(readme.map { it.asFile.parentFile })
}

publishing {
    publications.withType<MavenPublication>().configureEach {
    		if (name != "kotlinMultiplatform") {
            artifact(javadocJar)
        }
        
        pom {
            name.set("filesystem")
            description.set(
                "Android-first cross-platform file system abstraction library with Android Storage Access Framework support."
            )
            url.set("https://github.com/sifisofakude/filesystem")

            licenses {
                license {
                    name.set("MIT License")
                    url.set("https://opensource.org/licenses/MIT")
                    distribution.set("repo")
                }
            }

            developers {
                developer {
                    id.set("sifisofakude")
                    name.set("Sifiso Fakude")
                }
            }

            scm {
                connection.set(
                    "scm:git:git://github.com/sifisofakude/filesystem.git"
                )
                developerConnection.set(
                    "scm:git:ssh://github.com/sifisofakude/filesystem.git"
                )
                url.set("https://github.com/sifisofakude/filesystem")
            }
        }
    }

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

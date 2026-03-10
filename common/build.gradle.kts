import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.SourcesJar

plugins	{
	id("org.jetbrains.kotlin.multiplatform")
  id("com.vanniktech.maven.publish")
	id("signing")
}

kotlin	{
//	androidTarget()
	jvm()

	sourceSets	{
		val commonMain by getting	{
			dependencies	{
			}
		}
	}
}

mavenPublishing {
//    configureBasedOnAppliedPlugins(
//        javadocJar = JavadocJar.Javadoc(),
//        sourcesJar = SourcesJar.Sources()
//    )
    
//    publishToMavenCentral()

//    signAllPublications()

    coordinates(group.toString(),"filesystem-common",version.toString())

    pom    {
      name.set("FileSystem Common")
      description.set("Interface for jvm/android filesystem")
      url.set("https://github.com/sifisofakude/filesystem")
      licenses  {
        license {
          name.set("MIT License")
          url.set("https://opensource.org/licenses/MIT")
        }
      }
      developers  {
        developer {
          id.set("sifisofakude")
          name.set("Sifiso Fakue")
          email.set("sifisofakude404@gmail.com")
        }
      }
      
      scm {
        url.set("https://github.com/sifisofakude/filesystem/")
        connection.set("scm:git:git://github.com/sifisofakude/filesystem.git")
        developerConnection.set("scm:git:git://github.com/sifisofakude/filesystem.git")
      }
    }
}

//repositories  {
//  mavenCentral()
//}

//publishing  {
//  publications.withType<MavenPublication>  {
//    artifactId = "filesystem-common"
//    groupId = project.group.toString()
//    version = project.version.toString()
//    
//    pom {
//      name.set("FileSystem Common")
//      description.set("Common module for FileSystem library")
//      url.set("https://github.com")
//      
//      licenses  {
//        license {
//          name.set("The MIT License")
//          url.set("https://opensource.org")
//        }
//      }
//      
//      
//      developers  {
//          developer   {
//              id = "sifisofakude"
//              name = "Sifiso Fakude"
//              email = "sifisofakude404@gmail.com"
//          }
//      }
//
//      scm {
//          connection.set("scm:git:git://://github.com")
//          developerConnection.set("scm:git:ssh://github.com:sifisofakude/filesystem.git")
//          url.set("https://github.com")
//      }
//    }
//  }
//  
//  repositories  {
//    maven {
//      name = "OSSRH"
//      // Use staging URL for releases and snapshot URL for development
//      val releasesRepoUrl = "https://s01.oss.sonatype.org/service/staging/deploy/maven2/"
//      val snapshotsRepoUrl = "https://s01.oss.sonatype.org/content/repositories/snapshots/"
//      url = uri(releasesRepoUrl)
//      
//      credentials {
//        username = findProperty("ossrhUsername")?.toString()
//        password = findProperty("ossrhPassword")?.toString()
//      }
//    }
//  }
//}
//publishing  {
//  }
//  
//  publications.withType<MavenPublication> {
//    }
//  }
//}
//
//signing {
//  val signingKey = providers.gradleProperties("signingKey").orNull
//  val signingPassword = providers.gradleProperties("signingPassword").orNull
//  if(signingKey != null && signingPassword != null) {
//    
//  }
//}

//signing {
//  sign(publishing.publications)
//}

// Apply a specific Java toolchain to ease working on different environments.
//java {
//    toolchain {
//        languageVersion = JavaLanguageVersion.of(21)
//    }
//}

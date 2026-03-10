import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.SourcesJar

plugins	{
	id("org.jetbrains.kotlin.android")
	id("com.android.library")
  id("com.vanniktech.maven.publish")
	id("signing")
}

android	{
  namespace = "com.example.filesystem.android"
	compileSdk = 36
	
	defaultConfig {
	  minSdk = 23
	}
	
//	publishing  {
//	  singleVariant("release")
//	}
}

dependencies  {
  implementation(project(":common"))
  implementation("androidx.documentfile:documentfile:1.1.0")
}

mavenPublishing {
    
    configure(com.vanniktech.maven.publish.AndroidSingleVariantLibrary())
    
    configureBasedOnAppliedPlugins(
        javadocJar = JavadocJar.Javadoc(),
        sourcesJar = SourcesJar.Sources()
    )
//    publishToMavenCentral()

//    signAllPublications()
    
    coordinates(group.toString(),"filesystem-android",version.toString())
    
    pom    {
      name.set("Android SAF FileSystem")
      description.set("Allows easy reading/writing to file/directories")
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
//publishing  {
//  publications  {
//    create<MavenPublication>("release")  {
//      afterEvaluate {
//        from(components["release"])
//      }
//      
//      artifactId = "filesystem-android"
//      groupId = project.group.toString()
//      version = project.version.toString()
//    }
//  }
//}

//signing {
//  sign(publishing.publications)
//}


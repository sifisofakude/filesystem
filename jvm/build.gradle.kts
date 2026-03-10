import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.SourcesJar

plugins	{
	kotlin("jvm")
	id("com.vanniktech.maven.publish")
	`signing`
}

kotlin  {
  sourceSets.main {
    kotlin.setSrcDirs(listOf("src/jvmMain/kotlin"))
  }
}

dependencies  {
  implementation(project(":common"))
  implementation(kotlin("stdlib"))
}

mavenPublishing {
    configureBasedOnAppliedPlugins(
        javadocJar = JavadocJar.Javadoc(),
        sourcesJar = SourcesJar.Sources()
    )
//    publishToMavenCentral()

//    signAllPublications()

    coordinates(group.toString(),"filesystem-jvm",version.toString())

    pom    {
      name.set("JVM FileSystem")
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
//  publications.withType<MavenPublication>  {
//    create<MavenPublication>("jvm")  {
//      from(components["kotlin"])
//      
//      artifactId = "filesystem-jvm"
//      groupId = project.group.toString()
//      version = project.version.toString()
//    }
//  }
//}
//
//signing {
//  sign(publishing.publications)
//}

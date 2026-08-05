# filesystem

![Kotlin](https://img.shields.io/badge/Kotlin-2.4-blue)
![Platform](https://img.shields.io/badge/platform-JVM%20%7C%20Android-green)
![License](https://img.shields.io/badge/license-MIT-orange)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.sifisofakude.filesystem/filesystem-jvm)](https://central.sonatype.com/artifact/io.github.sifisofakude.filesystem/filesystem-jvm)

A lightweight cross-platform filesystem abstraction library for JVM desktop and Android Storage Access Framework (SAF).

`filesystem` provides a consistent, object-oriented API for working with files and directories while hiding platform-specific implementations. The same application code can be used across **Windows, Linux, macOS, Android, and Termux**, with only the filesystem implementation changing.

---

# Features

- Cross-platform filesystem abstraction
- Object-oriented `FileOperation` API
- File metadata (`name`, `nameWithoutExtension`, `extension`, `length`, `lastModified`, `absolutePath`)
- JVM filesystem implementation
- Android Storage Access Framework (SAF) implementation
- Recursive file discovery with relative path preservation
- Stream-based file processing
- Text file utilities
- Platform detection utilities
- Materialization support for Android SAF resources
- Lightweight with minimal dependencies

---

# Design Philosophy

The goal of `filesystem` is to provide a lightweight and predictable abstraction over multiple filesystem implementations.

Core principles:

- **Platform independence** – write the same code for JVM and Android.
- **Object-oriented API** – interact with files through `FileOperation`.
- **Stream-first design** – efficiently process files of any size.
- **Minimal dependencies** – avoid unnecessary frameworks.
- **Developer control** – platform limitations remain explicit.

---

# Supported Platforms

| Platform | Support |
|----------|:-------:|
| Windows | ✅ |
| Linux | ✅ |
| macOS | ✅ |
| Android (SAF) | ✅ |
| Termux | ✅ |

---

# Installation

## JVM

```gradle
implementation("io.github.sifisofakude.filesystem:filesystem-jvm:0.4.0")
```

## Android

```gradle
implementation("io.github.sifisofakude.filesystem:filesystem-android:0.4.0")
```

Each platform artifact is self-contained and includes the common filesystem API.

---

# Quick Start (JVM)

```kotlin
import io.github.sifisofakude.filesystem.*

FileSystems.current = JvmFileSystem()

val file = FileOperation("notes.txt")

if (!file.exists()) {
    file.createNewFile()
}

file.writeText("Hello, World!")

println(file.readText())

println(file.name)
println(file.nameWithoutExtension)
println(file.extension)
println(file.length)
println(file.lastModified)
println(file.absolutePath)
```

---

# File Operations

```kotlin
import io.github.sifisofakude.filesystem.*

FileSystems.current = JvmFileSystem()

val file = FileOperation("src/Main.kt")

println(file.name)
println(file.nameWithoutExtension)
println(file.extension)
println(file.absolutePath)

if (file.exists()) {
    println(file.readText())
}

file.copy("backup")

file.copy("backup/MainCopy.kt")

file.move("archive")

file.delete()
```

## Copying Files

`copy()` accepts either a directory or a file path.

```kotlin
val file = FileOperation("notes.txt")

// Copies to backup/notes.txt
file.copy("backup")

// Copies to backup/copy.txt
file.copy("backup/copy.txt")
```

---

# Working with Directories

```kotlin
val directory = FileOperation("src")

directory.listFiles().forEach {
    println(it.name)
}
```

Create directories recursively:

```kotlin
FileOperation("build/output").mkdirs()
```

---

# Resolving Source Files

`resolveFiles()` recursively discovers files while preserving their relative paths.

```kotlin
val fs = JvmFileSystem()

val files = fs.resolveFiles(
    listOf("src"),
    setOf("kt", "java")
)

files.forEach {
    println(it.relativePath)
}
```

---

# Android Example

```kotlin
import io.github.sifisofakude.filesystem.*

val fs = AndroidSafFileSystem(context)

FileSystems.current = fs

fs.changeSelectedDirectory(userSelectedUri)

val root = FileOperation(userSelectedUri.toString())

root.listFiles().forEach {
    println(it.name)
}
```

---

# Platform Detection

```kotlin
val detector = PlatformDetector()

if (detector.isAndroid()) {
    println("Running on Android")
}

if (detector.isDesktop()) {
    println("Running on Desktop")
}
```

---

# Architecture

The library consists of two primary APIs.

## FileOperation

The high-level object-oriented API used by applications.

```kotlin
val file = FileOperation("example.txt")

file.exists()

file.readText()

file.writeText("Hello")

file.copy("backup")

file.move("archive")

file.delete()
```

## FileSystemUtil

The low-level filesystem abstraction implemented by platform-specific providers.

Available implementations:

- `JvmFileSystem`
- `AndroidSafFileSystem`

Most applications will only need `FileOperation`, while library authors can work directly with `FileSystemUtil`.

---

# Engineering Highlights

- Cross-platform filesystem abstraction
- Object-oriented file API
- JVM and Android SAF implementations
- Recursive file discovery
- Relative path preservation
- Stream-first file processing
- Android SAF materialization
- Platform detection
- Clean, documented Kotlin API
- Maven Central distribution

---

# Use Cases

`filesystem` is suitable for:

- Build tools
- Compilers
- Code generators
- Static analyzers
- Archive utilities
- Android storage tools
- CLI applications
- Automation tools
- Cross-platform libraries

---

# License

MIT License

---

# Author

**Sifiso Fakude**
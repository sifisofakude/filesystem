# filesystem

![Kotlin](https://img.shields.io/badge/Kotlin-2.4-blue)
![Platform](https://img.shields.io/badge/platform-JVM%20%7C%20Android%20%7C%20iOS-green)
![License](https://img.shields.io/badge/license-MIT-orange)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.sifisofakude.filesystem/filesystem-jvm)](https://central.sonatype.com/artifact/io.github.sifisofakude.filesystem/filesystem-jvm)

A lightweight filesystem abstraction library primarily designed for **Android**, with JVM and iOS support for compatibility.

`filesystem` provides a consistent, object-oriented API for working with files and directories while hiding platform-specific filesystem implementations.

The primary focus is simplifying filesystem operations on Android, particularly when working with the **Storage Access Framework (SAF)**.

JVM and iOS implementations allow the same filesystem-dependent code to be reused across platforms without rewriting it for each target.

---

# Features
- Kotlin Multiplatform support
- Object-oriented `FileOperation` API
- File metadata (`name`, `nameWithoutExtension`, `extension`, `length`, `lastModified`, `absolutePath`)
- JVM filesystem implementation
- Android Storage Access Framework (SAF) implementation
- iOS filesystem implementation
- Recursive file discovery with relative path preservation
- Stream-based file processing
- Text file utilities
- SAF ↔ normal filesystem copy and move operations
- Android SAF materialization support
- Relative paths with selectable filesystem roots
- Lightweight with minimal dependencies
- Maven Central distribution

---

# Design Philosophy

The goal of `filesystem` is to provide a lightweight and predictable abstraction over multiple filesystem implementations.

Core principles:

- **Platform independence** – write filesystem code against a common API.
- **Object-oriented API** – interact with files through `FileOperation`.
- **Stream-first design** – efficiently process files of any size.
- **Minimal dependencies** – avoid unnecessary frameworks.
- **Platform-native implementations** – use the filesystem APIs appropriate to each platform.
- **Developer control** – platform limitations remain explicit.

---

# Supported Platforms

| Platform | Implementation | Support |
|----------|----------------|:-------:|
| Windows | JVM | ✅ |
| Linux | JVM | ✅ |
| macOS | JVM | ✅ |
| Android | Storage Access Framework (SAF) | ✅ |
| Termux | JVM | ✅ |
| iOS | Native filesystem | ✅ |

---

# Installation

`filesystem` is distributed through Maven Central.

## JVM

```kotlin
implementation("io.github.sifisofakude.filesystem:filesystem-jvm:1.0.0")
```

## Android

```kotlin
implementation("io.github.sifisofakude.filesystem:filesystem-android:1.0.0")
```

The platform artifacts provide the common filesystem API together with their platform-specific implementation.

For Kotlin Multiplatform projects, use the appropriate platform dependency for the target you are building.

---

# Quick Start

The library provides `FileOperation` as the primary object-oriented API.

```kotlin
import io.github.sifisofakude.filesystem.FileOperation

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

`FileSystems.current` is provided automatically by the active platform implementation. Applications do not need to manually assign a filesystem implementation.

---

# File Operations

```kotlin
import io.github.sifisofakude.filesystem.FileOperation

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

---

# Copying Files

`copy()` accepts either a destination directory or a destination path.

```kotlin
val file = FileOperation("notes.txt")

// Copies to backup/notes.txt
file.copy("backup")

// Copies to backup/copy.txt
file.copy("backup/copy.txt")
```

The same API can be used with files and directories.

```kotlin
val source = FileOperation("project")

source.copy("backup")
```

---

# Moving Files

Files and directories can be moved using the same object-oriented API.

```kotlin
val file = FileOperation("notes.txt")

file.move("archive")
```

For platform implementations where native filesystem copy or move operations are unavailable or provider-dependent, the library can perform the operation through its stream-based filesystem abstraction.

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

Check the type of a filesystem resource:

```kotlin
val file = FileOperation("notes.txt")

if (file.isFile()) {
    println("File")
}

if (file.isDirectory()) {
    println("Directory")
}
```

---

# Resolving Source Files

`resolveFiles()` recursively discovers files while preserving their relative paths.

```kotlin
import io.github.sifisofakude.filesystem.JvmFileSystem

val fs = JvmFileSystem()

val files = fs.resolveFiles(
    listOf("src"),
    setOf("kt", "java")
)

files.forEach {
    println(it.relativePath)
    println(it.absolutePath)
}
```

`FileSource` provides both the path relative to the resolution root and the resolved filesystem path.

---

# Android

The Android implementation uses the **Storage Access Framework (SAF)**, allowing applications to work with user-selected directories and documents without requiring direct filesystem access.

```kotlin
import io.github.sifisofakude.filesystem.FileOperation
import io.github.sifisofakude.filesystem.FileSystems

val root = FileOperation(userSelectedUri.toString())

root.listFiles().forEach {
    println(it.name)
}
```

## Selecting an Android Directory

The Android filesystem can use a user-selected SAF directory as the root for relative paths.

```kotlin
val fs = FileSystems.current as AndroidSafFileSystem

fs.changeSelectedDirectory(userSelectedUri)
```

Relative paths can then be used against the selected directory:

```kotlin
val file = FileOperation("documents/notes.txt")

file.writeText("Hello from Android")
```

Absolute SAF URIs remain independent of the selected directory.

The Android implementation uses `DocumentsContract` and `ContentResolver` rather than relying on provider-specific `DocumentFile` operations.

---

# SAF ↔ Normal Filesystem

The Android implementation supports copying and moving files and directories between **Storage Access Framework (SAF)** resources and the normal filesystem.

The same `copy()` and `move()` API can be used in either direction.

| Source | Destination | Copy | Move |
|--------|-------------|:----:|:----:|
| SAF | Normal filesystem | ✅ | ✅ |
| Normal filesystem | SAF | ✅ | ✅ |
| SAF | SAF | ✅ | ✅ |
| Normal filesystem | Normal filesystem | ✅ | ✅ |

## SAF → Normal Filesystem

A SAF resource can be copied to a normal filesystem path:

```kotlin
val source = FileOperation("content://...")

source.copy("/path/to/output.txt")
```

Or moved:

```kotlin
source.move("/path/to/output.txt")
```

Directories are supported as well:

```kotlin
val source = FileOperation("content://...")

source.copy("/path/to/backup")
```

## Normal Filesystem → SAF

A normal filesystem resource can also be copied to a SAF resource:

```kotlin
val source = FileOperation("/path/to/output.txt")

source.copy("content://...")
```

Or moved:

```kotlin
source.move("content://...")
```

Directories can be transferred in the same way:

```kotlin
val source = FileOperation("/path/to/backup")

source.copy("content://...")
```

These operations allow applications to transfer files between Android's conventional filesystem and user-selected SAF storage without manually implementing the underlying stream and `ContentResolver` operations.

---

# iOS

The iOS implementation uses Apple's Foundation filesystem APIs.

```kotlin
import io.github.sifisofakude.filesystem.FileOperation
import io.github.sifisofakude.filesystem.FileSystems

val file = FileOperation("documents/notes.txt")

file.writeText("Hello from iOS")

println(file.readText())
```

A selected directory can also be used as the root for relative paths:

```kotlin
val fs = FileSystems.current as IosFileSystem

fs.changeSelectedDirectory("/path/to/directory")
```

Relative paths are resolved against the selected directory, while absolute paths are treated as direct filesystem paths.

---

# Architecture

The library provides a common filesystem abstraction implemented by platform-specific backends.

## FileOperation

`FileOperation` is the primary object-oriented API used by applications.

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

`FileSystemUtil` is the lower-level filesystem abstraction implemented by platform-specific providers.

Available implementations include:

- `JvmFileSystem`
- `AndroidSafFileSystem`
- `IosFileSystem`

Most applications will only need `FileOperation`, while library authors and advanced applications can work directly with `FileSystemUtil`.

## FileSystems

`FileSystems.current` exposes the filesystem implementation provided by the current platform.

```kotlin
val fs = FileSystems.current
```

Applications do not need to manually initialize or assign the platform filesystem.

---

# Stream-Based File Processing

The library exposes `kotlinx.io` sources and sinks for stream-based file processing.

```kotlin
val file = FileOperation("data.txt")

file.openSource()?.use {
    // Read from the file
}

file.openSink()?.use {
    // Write to the file
}
```

Stream-based operations allow files to be processed without loading their entire contents into memory.

---

# Materializing Android SAF Files

Android SAF resources can be materialized into the application's private filesystem when a conventional filesystem path is required.

This is useful when working with APIs that cannot directly consume SAF `content://` resources.

After processing, materialized files can be cleared through the filesystem API.

---

# Use Cases

`filesystem` is suitable for:

- Android applications
- Build tools
- Compilers
- Code generators
- Static analyzers
- Archive utilities
- Android storage tools
- CLI applications
- Automation tools
- Cross-platform libraries
- File processing applications

---

# Engineering Highlights

- Android-focused filesystem abstraction
- Kotlin Multiplatform architecture
- JVM, Android SAF, and iOS implementations
- Object-oriented file API
- Recursive file discovery
- Relative path preservation
- Stream-first file processing
- SAF ↔ normal filesystem transfers
- Android SAF support
- Android SAF materialization
- Native iOS filesystem support
- Clean, documented Kotlin API
- Maven Central distribution

---

# Maven Central

`filesystem` is published to Maven Central under:

```text
io.github.sifisofakude.filesystem
```

Current release:

```text
1.0.0
```

---

# License

MIT License

---

# Author

**Sifiso Fakude**

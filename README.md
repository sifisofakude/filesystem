# filesystem

![Kotlin](https://img.shields.io/badge/Kotlin-2.2-blue)
![Platform](https://img.shields.io/badge/platform-JVM%20%7C%20Android-green)
![License](https://img.shields.io/badge/license-MIT-orange)
![Maven Central](https://img.shields.io/maven-central/v/io.github.sifisofakude/filesystem)

A lightweight cross-platform file system abstraction library for JVM desktop and Android SAF.

filesystem provides a unified API for discovering, reading, and writing files across different environments while hiding platform-specific implementations.

It allows developers to work with files using the same interface whether running on **Windows, Linux, Mac, Android, or Termux**.

---

## Features

- Cross-platform file access abstraction

- JVM desktop file system support

- Android Storage Access Framework (SAF) support

- Recursive file discovery with extension filtering

- Stream-based file processing

- Platform detection utilities

- Designed for library integration and automation tools

---

## Design Philosophy

The goal of `filesystem` is to provide a **minimal and predictable abstraction** for file system access across platforms.

Key principles:

- **Platform separation** – JVM and Android implementations remain independent
- **Stream-first design** – files are exposed as streams to support large data processing
- **Minimal dependencies** – avoids heavy frameworks
- **Developer control** – does not hide platform limitations

---

## Supported Platforms
| Platform |   Support |
|---|---|
| Windows |  ✅ |
| Linux | ✅ |
| macOS |  ✅ |
| Android (SAF) |  ✅ |
| Termux |   ✅ |

---

## Installation
**Maven Central**
```gradle
implementation("io.github.sifisofakude:filesystem-common:0.1.1")
```

---

## Quick Example (JVM)

```kotlin
import io.github.sifisofakude.filesystem.JvmFileSystem

val fs = JvmFileSystem()

val files = fs.resolveFiles(
    listOf("src"),
    setOf("kt", "java")
)

files.forEach { file ->
    println(file.relativePath)
}
```

---

## Android Example

```kotlin
val fs = AndroidSafFileSystem(context)

fs.changeSelectedDirectory(userSelectedUri)

val files = fs.resolveFiles(
    listOf(userSelectedUri),
    setOf("txt")
)

files.forEach {
    println(it.relativePath)
}
```

---

## Core API

The library revolves around the FileSystemUtil interface.

```kotlin
interface FileSystemUtil {

    fun getCurrentDirectory(): String?

    fun createDirectory(path: String): String?

    fun openOutputStream(path: String): OutputStream?

    fun findFiles(directory: String, extensions: Set<String>): List<String>

    fun resolveFiles(inputFiles: List<Any>, extensions: Set<String>): List<FileSource>
}
```

Implementations:

- JvmFileSystem → Desktop environments

- AndroidSafFileSystem → Android SAF environments

---

## Platform Detection

The library includes a lightweight **PlatformDetector** utility.

```kotlin
val detector = PlatformDetector()

if (detector.isAndroid()) {
    println("Running on Android")
}

if (detector.isDesktop()) {
    println("Desktop environment")
}
```

---

## Engineering Highlights

This project demonstrates several software engineering concepts:

- Cross-platform abstraction layer separating platform APIs from application logic

- SAF directory traversal without recursion using stack-based iteration

- Stream-first file pipeline for efficient file processing

- Extension-filtered recursive file discovery

- Minimal dependency design

- Maven Central publishing

- Clean, documented Kotlin API design

---

## Use Cases

`filesystem` is useful for:

- Build tools

- Code generators

- Static analyzers

- File processing pipelines

- Android storage tools

- CLI utilities

- Cross-platform automation tools

---

## License

MIT License

---

## Author

Sifiso Fakude

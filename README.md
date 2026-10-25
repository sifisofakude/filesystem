# filesystem

[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
![Platforms](https://img.shields.io/badge/Platforms-JVM%20%7C%20Android%20%7C%20iOS-blue)
[![License](https://img.shields.io/github/license/sifisofakude/filesystem)](https://github.com/sifisofakude/filesystem/blob/master/LICENSE)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.sifisofakude.filesystem/filesystem-jvm/1.0.0)](https://central.sonatype.com/artifact/io.github.sifisofakude.filesystem/filesystem-jvm/1.0.0)

A Kotlin Multiplatform file-system abstraction for working with files
and directories across **JVM, Android, and iOS** through a consistent
API.

`filesystem` provides a common way to perform file operations while
allowing each platform to use its native storage mechanisms. On Android,
this includes regular filesystem paths and Storage Access Framework
(SAF) documents. On iOS, it uses Foundation file APIs.

## Features

-   Kotlin Multiplatform support for JVM, Android, and iOS.
-   Common API for files, directories, streams, copying, moving, and
    discovery.
-   `FileOperation` wrapper for common file metadata and operations.
-   `FileSystemUtil` abstraction for platform-specific filesystem
    implementations.
-   Android SAF support for user-selected documents and directories.
-   Selected-directory roots for working with relative paths.
-   Stream-based operations using `kotlinx-io`.
-   Recursive file discovery and extension filtering.
-   Platform-specific APIs remain available where needed.

## Installation

Add the published artifact for the platform you are targeting. Check the
project's Maven Central page or repository README for the current
coordinates and version:

-   GitHub repository: https://github.com/sifisofakude/filesystem
-   Maven Central:
    https://central.sonatype.com/search?q=io.github.sifisofakude.filesystem

For example, in a Gradle Kotlin DSL dependency block, use the
appropriate published artifact:

``` kotlin
dependencies {
    implementation("io.github.sifisofakude.filesystem:<platform-artifact>:<version>")
}
```

Replace `<platform-artifact>` and `<version>` with the artifact and
version appropriate to your target. Do not use a JVM artifact for
Android or iOS unless the published artifact explicitly supports that
target.

## Quick start

The library exposes a current platform filesystem through
`FileSystems.current`. The platform implementation is expected to
initialize this property; applications generally should not assign it
themselves.

``` kotlin
import io.github.sifisofakude.filesystem.FileOperation
import io.github.sifisofakude.filesystem.FileSystems

val fs = FileSystems.current

val file = FileOperation("documents/hello.txt")

file.parent
file.name
file.extension
file.length()

file.writeText("Hello from filesystem")
val contents = file.readText()
```

The exact path interpretation depends on the platform and filesystem
configuration. On Android SAF and iOS, configure a selected directory
when you want relative paths to be rooted in a user-selected or
application-selected directory.

## `FileOperation`

`FileOperation` provides a convenient wrapper around a path and exposes
common metadata and operations.

``` kotlin
val file = FileOperation("documents/notes.txt")

val fileName = file.name
val nameWithoutExtension = file.nameWithoutExtension
val extension = file.extension
val parent = file.parent
val size = file.length()
val modified = file.lastModified()

if (file.exists()) {
    println("File exists: $fileName")
}
```

Commonly used properties and operations include:

-   `path` --- the path represented by the operation.
-   `name` --- the final name component.
-   `nameWithoutExtension` --- the name without its final extension.
-   `extension` --- the file extension.
-   `parent` --- the parent path, when available.
-   `length()` --- the file length.
-   `lastModified()` --- last-modified timestamp as provided by the
    platform implementation.
-   `exists()` --- whether the target exists.
-   `isFile()` --- whether the target is a file.
-   `isDirectory()` --- whether the target is a directory.
-   `mkdir()` --- create a directory when its parent already exists.
-   `mkdirs()` --- create the directory and missing parent directories.
-   `delete()` --- delete the target.
-   `copy(dst)` --- copy the target to a destination.
-   `move(dst)` --- move the target to a destination.
-   `renameTo(...)` --- rename the target where supported.
-   `listFiles()` --- list child entries where supported.

Check the implementation for your target platform for exact return
values and overwrite behavior.

### Creating directories and files

``` kotlin
val directory = FileOperation("documents/reports")
directory.mkdirs()

val report = FileOperation("documents/reports/summary.txt")
report.writeText("Report contents")
```

`mkdir()` is intended for creating a directory whose parent already
exists. Use `mkdirs()` when the parent directories may also need to be
created.

### Copying, moving, and renaming

``` kotlin
val source = FileOperation("documents/source.txt")

source.copy("documents/copy.txt")
source.move("documents/archive/source.txt")
```

Destination interpretation can differ by platform. Confirm whether a
destination is treated as a final file path or an existing destination
directory in the implementation you are using. Do not assume that copy
or move is atomic across storage providers.

## `FileSystemUtil`

`FileSystemUtil` defines the common filesystem abstraction implemented
by platform-specific filesystem classes.

``` kotlin
import io.github.sifisofakude.filesystem.FileSystems

val fs = FileSystems.current
```

Use the current implementation for operations that need to work with the
active platform's storage provider. The implementation may use regular
filesystem paths, SAF document URIs, or platform-native APIs.

Typical responsibilities of the filesystem abstraction include:

-   Resolving and normalizing paths.
-   Checking whether a path exists and whether it represents a file or
    directory.
-   Creating and deleting files and directories.
-   Listing directory contents.
-   Copying and moving entries.
-   Opening input and output streams or `kotlinx-io` sources and sinks.
-   Finding files recursively.

Not every platform or storage provider has identical capabilities. For
example, Android SAF providers may impose restrictions that do not apply
to ordinary filesystem paths.

### Raw `FileSystemUtil` examples

`FileOperation` is the convenient wrapper; `FileSystemUtil` is the lower-level API when you want to call the filesystem implementation directly.

#### Get the active filesystem

```kotlin
import io.github.sifisofakude.filesystem.FileSystems
import io.github.sifisofakude.filesystem.FileSystemUtil

val fs: FileSystemUtil = FileSystems.current
```

`FileSystems.current` is initialized by the platform-specific implementation. In normal application code, retrieve it rather than replacing it.

#### Create files and directories

```kotlin
val directoryCreated = fs.createDirectory("project/src")
val fileCreated = fs.createFile("project/src/Main.kt")
```

Use the return values to check whether the operation succeeded. For nested paths, use the directory-creation behavior provided by the implementation.

#### Read and write text

```kotlin
val success = fs.writeText(
    outputFile = "project/README.txt",
    text = "# My project"
)

val appended = fs.appendText(
    outputFile = "project/README.txt",
    text = "\nMore information"
)

val contents = fs.readText("project/README.txt")
```

These methods operate on paths understood by the active implementation. On Android, a path may refer to a SAF-backed document; on JVM it normally refers to a conventional filesystem path.

#### Open streams and copy data

The JVM-compatible stream API exposes `openInputStream` and `openOutputStream`:

```kotlin
val input = fs.openInputStream("project/input.bin")
val output = fs.openOutputStream("project/output.bin")

if (input != null && output != null) {
    input.use { source ->
        output.use { destination ->
            fs.copy(source, destination)
        }
    }
}
```

The API signatures include:

```kotlin
openInputStream(path: String): InputStream?
openOutputStream(path: String, append: Boolean = false): OutputStream?
copy(input: InputStream, output: OutputStream)
```

These Java stream methods are for compatible platform/source sets; do not call `InputStream` APIs from common code that must compile for iOS.

#### Copy, move, rename, and delete paths

```kotlin
val copied = fs.copy(
    src = "project/README.txt",
    dst = "project/README-backup.txt"
)

val moved = fs.move(
    src = "project/README-backup.txt",
    dst = "archive/README-backup.txt"
)

val renamed = fs.rename(
    src = "project/README.txt",
    dst = "project/README-old.txt"
)

val deleted = fs.delete("project/README-old.txt")
```

The path-operation overloads and their return types can vary with the published API version. Check your version's declaration if your IDE reports a different signature. Copy/move operations across providers are not necessarily atomic.

#### Check paths and read metadata

```kotlin
val exists = fs.exists("project/src/Main.kt")
val isFile = fs.isFile("project/src/Main.kt")
val isDirectory = fs.isDirectory("project/src")

val name = fs.getName("project/src/Main.kt")
val extension = fs.getExtension("project/src/Main.kt")
val parent = fs.getParentFile("project/src/Main.kt")
val size = fs.size("project/src/Main.kt")
val modified = fs.lastModified("project/src/Main.kt")
```

Use these calls when you need metadata or checks without wrapping every path in `FileOperation`.

#### Find and resolve files

For direct discovery, the API includes `findFiles` and `resolveFiles`:

```kotlin
val kotlinFiles = fs.findFiles(
    directory = "project/src",
    extensions = setOf("kt")
)

val sources = fs.resolveFiles(
    inputFiles = listOf("project/src"),
    extensions = setOf("kt", "java")
)

sources.forEach { source ->
    println("Relative: ${source.relativePath}")
    println("Absolute: ${source.absolutePath}")
}
```

`findFiles` returns matching paths. `resolveFiles` returns `FileSource` entries, which preserve relative and absolute path information for later processing.

#### Work with `kotlinx-io` sources and sinks

In common/platform-neutral code, prefer the `Source`/`Sink` API rather than Java streams:

```kotlin
val source = fs.openSource("project/input.txt")
val sink = fs.openSink("project/output.txt")

if (source != null && sink != null) {
    source.buffered().use { input ->
        sink.buffered().use { output ->
            input.transferTo(output)
        }
    }
}
```

The lower-level API includes these declarations:

```kotlin
abstract fun openSource(path: String): Source?
abstract fun openSink(path: String, append: Boolean = false): Sink?
```

Use the exact stream helpers available in the version you have installed. Close sources and sinks when finished.

#### Android-specific materialization

The Android SAF implementation can expose helpers for materializing SAF content into app-managed local storage when a downstream API requires a conventional file path. The available API also includes materialization cleanup helpers such as `clearMaterialized` in versions that expose them. Refer to the Android implementation's actual method signatures for the destination and cleanup behavior.

## `FileSource`

`FileSource` represents a discovered file and can carry both a relative
path and an absolute path.

``` kotlin
// Example shape; obtain FileSource instances from the library's discovery APIs.
val relativePath = fileSource.relativePath
val absolutePath = fileSource.absolutePath
```

The relative path is useful when preserving a directory structure, while
the absolute path identifies the resolved location for the current
filesystem implementation.

## File discovery and filtering

Use the filesystem discovery APIs to find files under a directory. Where
recursive discovery and extension filtering are supported, they can help
locate only the files relevant to an operation.

Conceptually, a discovery workflow looks like this:

``` kotlin
val root = FileOperation("documents")
val entries = root.listFiles()

entries.forEach { entry ->
    println(entry.name)
}
```

Use the actual discovery method exposed by the version and platform
artifact you have installed. Directory enumeration and recursion may be
limited by permissions or by the backing document provider.

## Streams and text I/O

Use the common stream APIs when you want to process content without
loading an entire file into memory.

``` kotlin
val file = FileOperation("documents/data.txt")
file.writeText("Some content")
val text = file.readText()
```

The JVM implementation also provides JVM-specific `InputStream` and
`OutputStream` helpers for code that needs Java stream interoperability.
These APIs are platform-specific and should be used only from compatible
source sets.

For large files, prefer streaming rather than `readText()` or other APIs
that load the whole file into memory.

## Android: Storage Access Framework (SAF)

The Android implementation can work with ordinary paths and SAF-backed
documents. SAF is useful when users choose files or directories through
Android's document picker, including storage locations that the
application cannot access through ordinary filesystem paths.

### Selecting a directory

The Android SAF implementation supports configuring a selected directory
using a tree URI:

``` kotlin
// treeUri is the URI returned by the Android directory picker.
// Persist the URI permission when appropriate before using it.
val safFileSystem = AndroidSafFileSystem(context)
safFileSystem.changeSelectedDirectory(treeUri)
```

Use the actual Android implementation class and imports provided by your
selected artifact. The application must obtain the appropriate URI
permissions from Android; a URI by itself does not grant permanent
access.

Once a directory is selected, relative paths can be resolved against
that selected directory according to the implementation's path rules.

### SAF path representation

The implementation may use an internal representation such as:

``` text
treeUri||relative/path/to/file.txt
```

This representation associates a relative path with a selected
document-tree URI. Treat it as an implementation detail rather than a
user-facing path format.

### Working with SAF files

Use the library's common operations where possible rather than manually
repeating `DocumentFile` or `DocumentsContract` handling throughout your
application. The backing provider may still restrict operations such as
renaming, deleting, or creating files.

### Materializing content

Some workflows need a regular local file instead of a SAF URI---for
example, when a third-party API requires a filesystem path. The Android
implementation includes materialization support for copying accessible
SAF content into app-managed storage. Check the API available in your
artifact for the exact method and destination behavior.

Remember that materializing a file creates a local copy; it does not
make that copy automatically synchronize with the original document.

## iOS

The iOS implementation uses Foundation filesystem APIs and supports a
selected directory as the root for relative paths.

Configure the selected directory before using relative paths:

``` kotlin
// Example using the iOS filesystem implementation.
val fs = IosFileSystem()
fs.changeSelectedDirectory("/path/to/directory")
```

Use the actual public API and path format exposed by the iOS artifact.
Absolute paths are handled separately from paths resolved against the
selected root. Passing `null` to clear the selected directory is
supported only if the version you use explicitly implements that
behavior.

The implementation integrates with `kotlinx-io` sources and sinks for
stream-oriented operations.

## JVM

The JVM implementation works with conventional filesystem paths and can
expose Java stream interoperability helpers such as `InputStream` and
`OutputStream`.

``` kotlin
val file = FileOperation("documents/example.txt")

// Common text operations
file.writeText("Hello")
val contents = file.readText()
```

JVM-only helpers should remain in JVM-compatible source sets rather than
common Kotlin Multiplatform code.

## Platform detection

The library has a `PlatformDetector` helper for determining platform
information where that API is available. Platform-specific behavior
should generally be isolated in the appropriate source set instead of
spreading platform checks throughout shared business logic.

## API and behavior notes

-   **Paths are provider-dependent.** A path that is valid for a JVM
    filesystem may not be valid for an Android SAF provider.
-   **Permissions matter.** Android SAF access requires URI permissions
    from the document picker.
-   **Relative paths need a root.** Configure the selected directory
    where the platform implementation uses one to resolve relative
    paths.
-   **Copy and move are not necessarily atomic.** Stream-based
    implementations can partially complete before an error occurs.
-   **Overwrite semantics can vary.** Check the implementation and
    version before relying on replacement behavior.
-   **Metadata is platform-dependent.** Timestamp and file-length values
    depend on what the underlying provider exposes.
-   **Use streaming for large content.** Avoid reading large files fully
    into memory when a stream-based operation is suitable.

## Use cases

-   Sharing file-handling code across JVM, Android, and iOS.
-   Building file managers and document workflows.
-   Working with user-selected Android documents and folders.
-   Reading, writing, copying, or moving files through a common API.
-   Finding files recursively and filtering by extension.
-   Adapting existing code to different storage providers without
    duplicating every operation.

## Project

-   Source code and issues: https://github.com/sifisofakude/filesystem
-   Maven Central search:
    https://central.sonatype.com/search?q=io.github.sifisofakude.filesystem

## License

See the repository's `LICENSE` file for the license terms.

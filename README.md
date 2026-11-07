# filesystem

[![Maven
Central](https://img.shields.io/maven-central/v/io.github.sifisofakude.filesystem/filesystem-jvm)](https://central.sonatype.com/artifact/io.github.sifisofakude.filesystem/filesystem-jvm)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.0-7F52FF.svg?logo=kotlin)](https://kotlinlang.org/)
[![Platforms](https://img.shields.io/badge/platforms-JVM%20%7C%20Android%20%7C%20iOS-blue)](#supported-platforms)
[![License](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)

**filesystem** is a Kotlin Multiplatform filesystem abstraction for
working with files and directories through a consistent API across
supported platforms.

It provides a common set of operations for conventional filesystem paths
and platform-specific resources. On Android, this includes Storage
Access Framework (SAF) document URIs and paths relative to a
user-selected directory. The abstraction also supports stream-based
transfers, allowing implementations to copy or move resources without
depending on a provider's native copy or move support.

## Features

-   A shared filesystem API for JVM, Android, and iOS targets.
-   `FileOperation`, an object-oriented wrapper around filesystem paths
    and resources.
-   Android SAF support for document URIs and selected document trees.
-   Provider-aware path resolution, including traversal for providers
    whose document IDs are opaque.
-   Stream-based file and directory copying and moving.
-   Read and write access checks.
-   File discovery and recursive resolution into `FileSource` entries.
-   Text and byte-stream operations.
-   Platform-specific implementations behind a common interface.

## Supported platforms

  ------------------------------------------------------------------------
  Platform                Filesystem               Resource model
                          implementation           
  ----------------------- ------------------------ -----------------------
  JVM                     `JvmFileSystem`          Conventional filesystem
                                                   paths

  Android                 `AndroidSafFileSystem`   Filesystem paths, SAF
                                                   document URIs, and
                                                   paths relative to a
                                                   selected SAF directory

  iOS                     iOS filesystem           Foundation-backed
                          implementation           filesystem resources
  ------------------------------------------------------------------------

The active implementation is exposed through `FileSystems.current`.
`FileOperation` delegates its operations to that active filesystem.

## Installation

Add the JVM artifact to your Gradle dependencies:

``` kotlin
dependencies {
    implementation("io.github.sifisofakude.filesystem:filesystem-jvm:1.0.0")
}
```

For Android and iOS, use the artifact published for the target you are
compiling. See the [Maven Central
artifacts](https://central.sonatype.com/search?q=io.github.sifisofakude.filesystem)
for the currently published modules.

## Quick start

``` kotlin
import io.github.sifisofakude.filesystem.FileOperation

val directory = FileOperation("/storage/emulated/0/Documents")
val file = FileOperation(directory, "notes.txt")

// Create the directory and file.
directory.mkdirs()
file.createNewFile()

// Write and read text.
file.writeText("Hello from FileSystem")
val contents = file.readText()

// Inspect the resource.
println(file.name)
println(file.extension)
println(file.length)
println(file.absolutePath)
println(file.exists())
```

The meaning of a path depends on the active filesystem implementation. A
path may be a conventional filesystem path, a SAF URI, or a path
relative to a selected root.

## `FileOperation`

`FileOperation` provides an object-oriented interface for working with a
file or directory. It delegates filesystem work to
`FileSystems.current`.

### Creating file operations

``` kotlin
val file = FileOperation("/path/to/file.txt")

val directory = FileOperation("/path/to/directory")
val child = FileOperation(directory, "child.txt")
```

The parent-aware constructor combines the parent path with the child
path and retains the parent operation as logical context. This is useful
with providers where a child's physical URI does not contain enough
information to reconstruct its parent.

### Properties

  -----------------------------------------------------------------------
  Property                            Description
  ----------------------------------- -----------------------------------
  `path`                              Path or resource identifier
                                      supplied to the operation

  `name`                              Display name of the resource

  `extension`                         File extension without the leading
                                      dot

  `nameWithoutExtension`              Name without its final extension

  `parent`                            Parent operation when it can be
                                      resolved or is retained as logical
                                      context

  `length`                            Resource size in bytes

  `lastModified`                      Last-modified timestamp in
                                      milliseconds since the Unix epoch

  `absolutePath`                      Resolved path or platform-specific
                                      resource identifier

  `canRead`                           Whether the resource can be read

  `canWrite`                          Whether the resource can be written
  -----------------------------------------------------------------------

### Common operations

``` kotlin
val file = FileOperation("/path/to/notes.txt")

file.exists()
file.isFile()
file.isDirectory()

file.createNewFile()
file.writeText("Some text")
val text = file.readText()

file.openSource()?.use { source ->
    // Read from the source.
}

file.openSink()?.use { sink ->
    // Write to the sink.
}

file.delete()
```

`openSource()` and `openSink()` return `kotlinx.io.Source` and
`kotlinx.io.Sink` respectively. The caller is responsible for closing
them; use `use` to close them automatically.

### Directories and child operations

``` kotlin
val root = FileOperation("/path/to/project")

root.mkdirs()

val children: List<FileOperation> = root.listFiles()

for (child in children) {
    println(child.name)
    println(child.absolutePath)
}
```

Children returned by `listFiles()` are created with their parent
operation as context. This preserves the logical parent-child
relationship even when a storage provider uses opaque resource
identifiers.

`mkdir()` creates a directory when its parent exists. `mkdirs()` creates
the directory using the filesystem implementation's directory-creation
behavior.

### Copying and moving

``` kotlin
val source = FileOperation("/path/to/source.txt")
val destinationDirectory = FileOperation("/path/to/backup")

destinationDirectory.mkdirs()

val copied = source.copy(destinationDirectory)
val moved = source.move(FileOperation("/path/to/archive/source.txt"))
```

The `copy` and `move` methods accept either a destination path or
another `FileOperation`. The `copy` overload also accepts an `overwrite`
argument.

When the destination is an existing directory, the source is placed
inside it using the source resource's name. The returned
`FileOperation`, when successful, identifies the resulting destination.

## `FileSystemUtil`

`FileSystemUtil` defines the operations used by `FileOperation` and
provides default implementations for higher-level functionality.

An implementation supplies primitive operations such as:

-   `createFile()` and `createDirectory()`
-   `openSource()` and `openSink()`
-   `listFiles()` and `exists()`
-   `delete()` and `rename()`
-   `getName()` and `getParentFile()`
-   `isFile()` and `isDirectory()`
-   `resolvePath()` and `combinePath()`

It also exposes access checks through `canRead(path)` and
`canWrite(path)`.

### Stream-based copy

`copyByStream(src, dst, overwrite)` copies files through `openSource()`
and `openSink()`. Directories are traversed recursively using
`listFiles()` and created at the destination.

This implementation is useful when the underlying provider does not
offer a native copy operation or when copying between different storage
representations. The default `copy()` operation is part of the interface
contract; implementations may provide their own implementation.

``` kotlin
val copiedPath = FileSystems.current.copyByStream(
    src = sourcePath,
    dst = destinationPath,
    overwrite = false
)
```

### Stream-based move

`moveByStream(src, dst)` uses stream-based copying and deletes the
source after a successful copy. Directory moves are performed
recursively.

A move across different storage providers is therefore implemented as a
transfer followed by deletion, rather than assuming that the providers
support a native move operation.

``` kotlin
val movedPath = FileSystems.current.moveByStream(
    src = sourcePath,
    dst = destinationPath
)
```

A failed recursive move can leave already-moved children at the
destination. The source directory is only deleted after its children
have been processed successfully.

### Reading and writing text

``` kotlin
val fs = FileSystems.current

val contents = fs.readText(path)
val written = fs.writeText(path, "Hello")
val appended = fs.appendText(path, "\nMore text")
```

`readText`, `writeText`, and `appendText` use the stream operations
provided by the active implementation.

### Finding and resolving files

`findFiles(directory, extensions)` recursively finds files under a
directory. An empty extension set includes files of any extension.

``` kotlin
val matches = FileSystems.current.findFiles(
    directory = "/path/to/project",
    extensions = setOf("kt", "kts")
)
```

`resolveFiles(inputFiles, extensions)` resolves a collection of input
resources into `FileSource` values.

``` kotlin
val sources = FileSystems.current.resolveFiles(
    inputFiles = listOf("/path/to/project", "/path/to/another.kt"),
    extensions = setOf("kt")
)

for (source in sources) {
    println("Relative: ${source.relativePath}")
    println("Resolved: ${source.absolutePath}")
}
```

## `FileSource`

`FileSource` represents a resolved file using two paths:

  -----------------------------------------------------------------------
  Property                            Description
  ----------------------------------- -----------------------------------
  `relativePath`                      File location relative to the root
                                      from which it was resolved

  `absolutePath`                      Resolved path or platform-specific
                                      resource identifier used to access
                                      the file
  -----------------------------------------------------------------------

Keeping both values is useful when processing files while preserving
their directory structure, for example when building archives or
compiling a group of source files.

## Android Storage Access Framework

Android's `AndroidSafFileSystem` extends `JvmFileSystem` and adds
support for SAF document resources.

It supports working with:

-   SAF document URIs.
-   A user-selected document tree.
-   Paths relative to the selected directory.
-   Provider-backed resources whose document IDs cannot be derived from
    conventional path segments.
-   Stream-based reads, writes, copies, and moves.

### Selecting a directory

After the user selects a directory through the Storage Access Framework,
pass its tree URI to the filesystem:

``` kotlin
val fs = AndroidSafFileSystem(context)

fs.changeSelectedDirectory(treeUri.toString())
FileSystems.current = fs
```

The selected directory becomes the base for relative paths handled by
this filesystem. The application must obtain and retain the appropriate
SAF URI permission for later access.

### Working with relative paths

``` kotlin
val documents = FileOperation("reports")
val report = FileOperation(documents, "summary.txt")

documents.mkdirs()
report.writeText("Quarterly summary")

val contents = report.readText()
```

Relative paths are interpreted against the selected directory. Absolute
SAF document URIs can also be passed directly where supported by the
implementation.

### Provider-aware resolution

Not all document providers represent resources in the same way.

Some providers use document IDs that resemble paths, while others use
opaque IDs that cannot be constructed by appending a filename to a URI.
`AndroidSafFileSystem` handles these cases differently:

-   For supported local storage document URIs, it can resolve paths
    using the provider's document ID structure.
-   For cloud-style providers, it traverses directory children and
    matches display names to discover the provider-issued document IDs.

This distinction allows the filesystem to work with provider-backed
resources without assuming that every URI follows local filesystem path
rules. Actual operations remain subject to the provider's permissions
and capabilities.

### Materializing resources

The Android implementation also provides materialization helpers for
making SAF resources available as ordinary app-local files when an API
requires a filesystem path.

Use `materialize()` to obtain a local copy of a SAF resource, and
`clearMaterialized()` to remove a materialized copy when it is no longer
needed. These helpers are specific to the Android implementation.

## JVM streams

The JVM implementation uses conventional filesystem paths and provides
access through `kotlinx.io.Source` and `kotlinx.io.Sink`.

This keeps stream-based operations consistent with the API used by
Android and other platform implementations.

## Design notes

-   Paths are strings because different platforms and providers expose
    different resource identifiers.
-   `resolvePath()` returns a representation understood by the active
    implementation; it is not guaranteed to be a conventional
    operating-system path.
-   `getParentFile()` may return `null` when a provider does not expose
    enough information to determine a resource's parent. `FileOperation`
    can retain logical parent context when constructed from another
    `FileOperation`.
-   Copy and move behavior depends on the primitive capabilities and
    permissions of the active filesystem.
-   Stream operations return nullable sources or sinks when a resource
    cannot be opened.

## Use cases

-   Reading and writing files across supported Kotlin platforms.
-   Managing Android shared storage through the Storage Access
    Framework.
-   Working with user-selected directories and document trees.
-   Copying or moving files between filesystem roots or provider-backed
    resources.
-   Recursively discovering files for archive creation, compilation,
    import, or processing.
-   Keeping application code independent of platform-specific storage
    APIs.

## Project

Source code, issues, and releases:

-   Maven Central:
    https://central.sonatype.com/search?q=io.github.sifisofakude.filesystem

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE)
for details.

## Highlights

filesystem 0.4.0 introduces a new object-oriented file API while maintaining the existing cross-platform filesystem abstraction.

### What's New

- Added `FileOperation` for intuitive file and directory operations.
- Added file metadata properties:
  - `name`
  - `nameWithoutExtension`
  - `extension`
  - `length`
  - `lastModified`
  - `absolutePath`
- Improved copy operations to support both destination directories and destination file paths.
- Improved API documentation across the library.
- Updated README with new examples and architecture overview.

### Supported Platforms

- JVM
- Android Storage Access Framework (SAF)

### Maven

```gradle
implementation("io.github.sifisofakude.filesystem:filesystem-jvm:0.4.0")
```

or

```gradle
implementation("io.github.sifisofakude.filesystem:filesystem-android:0.4.0")
```

Thank you to everyone using filesystem!
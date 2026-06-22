package io.github.sifisofakude.filesystem

import android.net.Uri
import android.content.Context
import android.provider.DocumentsContract

import androidx.documentfile.provider.DocumentFile

import java.util.Stack

import java.io.OutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.BufferedReader
import java.nio.file.Paths
import kotlin.io.normalize

/**
 * Android Storage Access Framework (SAF) implementation of [FileSystemUtil].
 *
 * This implementation provides filesystem-like access to Android's SAF
 * using a hybrid approach:
 *
 * - [`DocumentFile`] for safe, high-level tree traversal
 * - [`DocumentsContract`] for low-level, performance-critical operations
 *
 * ## Design goals
 *
 * - Work entirely within Android's scoped storage model
 * - Support recursive directory traversal
 * - Provide consistent abstraction across file systems (JVM, Android, etc.)
 * - Balance safety (`DocumentFile`) and performance (`DocumentsContract`)
 *
 * ## Important limitations
 *
 * - Requires user-granted SAF tree permissions (`Uri`)
 * - Paths are not real filesystem paths — they are URI-based
 * - Performance is dependent on SAF provider (Google Drive, OEM storage, etc.)
 *
 * ## Usage
 *
 * ```kotlin
 * val fs = AndroidSafFileSystem(context)
 * fs.changeSelectedDirectory(treeUri)
 * val files = fs.resolveFiles(listOf(treeUri), setOf("txt"))
 * ```
 *
 * @param context Android application or activity context used for SAF access
 */
class AndroidSafFileSystem(context: Context) : FileSystemUtil	{
	private val context = context
	private var selectedParentUri: Uri? = null
	private val contentResolver = context.contentResolver

	/**
	 * Sets the active SAF root directory used for all relative file operations.
	 *
	 * All directory creation and relative resolution operations will be
	 * anchored to this URI.
	 *
	 * @param newParentUri SAF tree URI representing a user-granted directory
	 */
	fun changeSelectedDirectory(newParentUri: Uri)	{
		selectedParentUri = newParentUri
	}

	/**
	 * Returns the currently selected SAF root directory.
	 *
	 * @return URI string of the selected directory, or null if none is set
	 */
	override fun getCurrentDirectory(): String?	{
		return selectedParentUri?.toString()
	}

	/**
	 * Resolves a list of SAF inputs into structured [FileSource] entries.
	 *
	 * Supports:
	 * - [DocumentFile]
	 * - [Uri]
	 * - String URIs
	 *
	 * If a directory is provided, all nested files are recursively discovered.
	 * Files that do not match the provided extensions are excluded.
	 *
	 * @param inputFiles list of files, directories, or URIs
	 * @param extensions allowed file extensions (empty = all files)
	 * @return list of resolved file entries with relative and absolute URIs
	 */
	override fun resolveFiles(
    inputFiles: List<Any>,
    extensions: Set<String>
	): List<FileSource> {
	
    val results = mutableListOf<FileSource>()

    for (input in inputFiles) {

      val root = when (input) {
        is DocumentFile -> input
        is Uri -> DocumentFile.fromTreeUri(context, input)
        is String -> DocumentFile.fromTreeUri(context, Uri.parse(input))
        else -> null
      } ?: continue

      if (root.isFile) {
        val name = root.name ?: continue

        if (extensions.isNotEmpty()) {
          val ext = name.substringAfterLast('.', "")
          if (ext !in extensions) continue
        }

        results.add(
          FileSource(
            relativePath = name,
            absolutePath = root.uri.toString()
          )
        )
        continue
      }

      if (root.isDirectory) {
        walkSaf(root, "", extensions, results)
      }
    }

    return results
	}

	/**
	 * Recursively traverses a SAF directory tree.
	 *
	 * Builds a flat list of [FileSource] objects using a depth-first traversal.
	 *
	 * @param dir starting directory
	 * @param basePath relative path accumulator
	 * @param extensions allowed file extensions filter
	 * @param out output list accumulator
	 */
	private fun walkSaf(
	    dir: DocumentFile,
	    basePath: String,
	    extensions: Set<String>,
	    out: MutableList<FileSource>
	) {
    dir.listFiles().forEach { file ->

      val name = file.name ?: return@forEach

      val rel = if (basePath.isEmpty()) name else "$basePath/$name"

      if (file.isDirectory) {
          walkSaf(file, rel, extensions, out)
      } else {
        if (extensions.isEmpty() ||
          name.substringAfterLast('.') in extensions
        ) {
          out.add(
            FileSource(
              relativePath = rel,
              absolutePath = file.uri.toString()
            )
          )
        }
      }
    }
	}

	/**
	 * Recursively searches for files in a SAF directory using URI traversal.
	 *
	 * Only files matching the given extensions are returned.
	 *
	 * @param directory SAF tree URI string
	 * @param extensions allowed file extensions (empty = all files)
	 * @return list of file URIs as strings
	 */
	override fun findFiles(directory: String, extensions: Set<String>): List<String> {
    val rootUri = Uri.parse(directory)
    val root = DocumentFile.fromTreeUri(context, rootUri) ?: return emptyList()

    val results = mutableListOf<String>()

    fun walk(dir: DocumentFile) {
      val children = dir.listFiles()

      for (file in children) {

        if (file.isDirectory) {
          walk(file)
          continue
        }

        if (!file.isFile) continue

        val name = file.name ?: continue

        val ext = name.substringAfterLast('.', "")

        if (extensions.isNotEmpty() && ext !in extensions) {
          continue
        }

        results.add(file.uri.toString())
      }
    }

    walk(root)

    return results
	}

	/**
	 * Creates a directory structure inside the selected SAF root.
	 *
	 * The provided path is treated as a relative path, and all missing
	 * intermediate directories will be created.
	 *
	 * If a file exists with the same name as a required directory segment,
	 * creation fails.
	 *
	 * @param path relative directory path (e.g. "a/b/c")
	 * @return URI string of the final directory, or null if creation failed
	 */
	override fun createDirectory(path: String): String? {
    val baseUri = selectedParentUri ?: return null

    var parent = DocumentFile.fromTreeUri(context, baseUri) ?: return null

    val segments = path.split("/")
        .filter { it.isNotBlank() }

    for (segment in segments) {

      val existing = parent.findFile(segment)

      parent = when {
        existing != null && existing.isDirectory -> existing
        existing != null && existing.isFile -> return null
        else -> parent.createDirectory(segment) ?: return null
      }
    }

    return parent.uri.toString()
	}

	/**
	 * Creates a file inside the selected SAF root directory.
	 *
	 * Missing parent directories are automatically created.
	 * If a file already exists, its URI is returned instead of creating a new one.
	 *
	 * @param path relative file path (e.g. "a/b/file.txt")
	 * @return URI string of the file, or null if creation failed
	 */
	override fun createFile(path: String): String? {
    val lastSlash = path.lastIndexOf('/')
    if (lastSlash <= 0) return null

    val dirPath = path.substring(0, lastSlash)
    val fileName = path.substring(lastSlash + 1)

    val baseUri = selectedParentUri ?: return null
    var parent = DocumentFile.fromTreeUri(context, baseUri) ?: return null

    val segments = dirPath.split("/").filter { it.isNotBlank() }

    for (segment in segments) {
      val next = parent.findFile(segment)

      parent = when {
        next != null && next.isDirectory -> next
        next == null -> parent.createDirectory(segment) ?: return null
        else -> return null
      }
    }

    parent.findFile(fileName)?.let {
      if (it.isFile) return it.uri.toString()
    }

    val created = parent.createFile("application/octet-stream", fileName)
      ?: return null

    return created.uri.toString()
	}

	/**
	 * Copies one or more SAF files into a destination directory.
	 *
	 * Uses Android's [DocumentsContract.copyDocument] for efficiency.
	 *
	 * @param src list of source file URIs
	 * @param dst destination directory URI
	 * @param overwrite if false, existing files are skipped
	 */
	override fun copy(
		src: List<String>,
		dst: String,
		overwrite: Boolean
	)	{
		val targetUri = Uri.parse(dst)
		
		for (path in src) {
	    val sourceUri = Uri.parse(path)
	
	    val fileName = path.substringAfterLast('/')
	    val destCheck = Uri.parse("$dst/$fileName")
	
	    val exists = DocumentFile.fromTreeUri(context, destCheck)?.exists() == true
	
	    if (!overwrite && exists) continue
	
	    DocumentsContract.copyDocument(
        contentResolver,
        sourceUri,
        targetUri
	    )
		}
	}

	/**
	 * Opens an output stream for writing to a SAF file.
	 *
	 * @param path file URI string
	 * @return output stream or null if the file cannot be opened
	 */
	override fun openOutputStream(path: String): OutputStream?	{
		val uri = Uri.parse(path)

		return contentResolver.openOutputStream(uri)
	}

	/**
	 * Opens an input stream for reading from a SAF file.
	 *
	 * @param path file URI string
	 * @return input stream or null if inaccessible
	 */
	override fun openInputStream(path: String): InputStream?	{
		val fileUri = Uri.parse(path)
		
		return contentResolver.openInputStream(fileUri)
	}

	/**
	 * Reads the full text content of a SAF file using UTF-8 encoding.
	 *
	 * @param path file URI string
	 * @return file contents as string, or null if unreadable
	 */
	override fun readText(path: String): String? {
	    val stream = openInputStream(path) ?: return null
	
	    return stream.bufferedReader().use { it.readText() }
	}

	/**
	 * Lists immediate children of a SAF directory.
	 *
	 * @param path directory URI string
	 * @return list of child file URIs
	 */
	override fun listFiles(path: String): List<String>	{
		val document = DocumentFile.fromTreeUri(context,Uri.parse(path))

		return document?.listFiles()
			?.map	{ it.getUri().toString() }
			?.toList()
			?: emptyList()
	}

	/**
	 * Checks whether a SAF file or directory exists.
	 *
	 * @param path URI string
	 * @return true if accessible and exists, false otherwise
	 */
	override fun exists(path: String): Boolean	{
		val document = DocumentFile.fromTreeUri(context,Uri.parse(path))

		return document?.exists() ?: false
	}

	/**
	 * Deletes a SAF file or directory.
	 *
	 * @param path URI string
	 * @return true if deletion succeeded
	 */
	override fun delete(path: String): Boolean	{
		val document = DocumentFile.fromTreeUri(context,Uri.parse(path))

		return document?.delete() ?: false
	}

	/**
	 * Renames a SAF document.
	 *
	 * @param src source file URI
	 * @param target new display name
	 * @return URI string of renamed document, or null if failed
	 */
	override fun rename(src: String, target: String): String?	{
		return DocumentsContract
			.renameDocument(contentResolver,Uri.parse(src),target)
			?.toString()
	}

	/**
	 * Moves a SAF document to a new parent directory.
	 *
	 * @param src source file URI
	 * @param dst destination directory URI
	 * @return URI string of moved document, or null if failed
	 */
	override fun move(src: String, dst: String): String?	{
		val sourceUri = Uri.parse(src)
		val targetParentUri = Uri.parse(dst)

		val sourceDocument = DocumentFile
			.fromTreeUri(context, sourceUri) ?: return null

		DocumentFile.fromTreeUri(context,targetParentUri)
			?: return null

		val sourceParentUri = sourceDocument
			.getParentFile()
			?.getUri()
		 	?: return null

		return DocumentsContract.moveDocument(
			contentResolver,
			sourceUri,
			sourceParentUri,
			targetParentUri
		)?.toString()
	}

	/**
	 * Checks whether the given URI points to a file.
	 *
	 * @param path file URI string
	 * @return true if file
	 */
	override fun isFile(path: String): Boolean	{
		DocumentFile.fromTreeUri(context,Uri.parse(path))?.let	{
			if(it.isFile) return true
			else return false
		}
		return false
	}

	/**
	 * Checks whether the given URI points to a directory.
	 *
	 * @param path directory URI string
	 * @return true if directory
	 */
	override fun isDirectory(path: String): Boolean	{
		DocumentFile.fromTreeUri(context,Uri.parse(path))?.let	{
			if(it.isDirectory) return true
			else return false
		}
		return false
	}

	/**
	 * Returns last modified timestamp of a SAF file.
	 *
	 * @param path file URI string
	 * @return timestamp in millis, or -1 if unavailable
	 */
	override fun lastModified(path: String): Long	{
		DocumentFile.fromTreeUri(context,Uri.parse(path))?.let	{
			return it.lastModified()
		}
		return -1
	}

	/**
	 * Normalizes and reconstructs a SAF tree URI.
	 *
	 * Attempts to resolve and clean up relative segments such as ".."
	 * inside SAF document IDs.
	 *
	 * @param path SAF URI string
	 * @return normalized SAF URI string
	 */
	override fun resolvePath(path: String): String {
    val uri = Uri.parse(path)

    val docId = runCatching {
      DocumentsContract.getTreeDocumentId(uri)
    }.getOrNull() ?: return path

    val parts = docId.split(":")

    val root = parts.getOrNull(0) ?: return path
    val rest = parts.getOrNull(1) ?: ""

    val normalized = Paths.get(rest).normalize().toString()

    val resolvedDocId = "$root:$normalized"

    return DocumentsContract.buildTreeDocumentUri(
      uri.authority,
      resolvedDocId
    ).toString()
	}

	/**
	 * Returns the size of a SAF file in bytes.
	 *
	 * @param path file URI string
	 * @return file size or 0 if unavailable
	 */
	override fun size(path: String): Long	{
		return DocumentFile.fromSingleUri(context,Uri.parse(path))
			?.length() ?: 0L
	}
}

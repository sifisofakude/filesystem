package io.github.sifisofakude.filesystem

import android.net.Uri
import android.content.Context
import android.provider.DocumentsContract

import androidx.documentfile.provider.DocumentFile

import java.util.Stack

import java.io.File
import java.io.OutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.BufferedReader
import java.nio.file.Paths
import kotlin.io.normalize

/**
 * Android Storage Access Framework (SAF) implementation of [FileSystemUtil].
 *
 * This implementation extends [JvmFileSystem] and adds support for
 * Android Storage Access Framework (SAF) URIs (`content://`).
 *
 * Operations automatically route to either:
 *
 * - JVM filesystem APIs for regular file paths
 * - SAF APIs for `content://` document URIs
 *
 * This allows the same API to work with both storage models while
 * preserving compatibility with the default JVM implementation.
 *
 * ## Cross-filesystem operations
 *
 * Since high-level operations such as [copy], [move], [readText],
 * and directory traversal are inherited from [FileSystemUtil],
 * files can be transferred between SAF and regular filesystem paths
 * without additional code.
 *
 * Examples:
 *
 * ```kotlin
 * fs.copy(
 *     "/storage/emulated/0/source.txt",
 *     "content://..."
 * )
 *
 * fs.copy(
 *     "content://...",
 *     "/storage/emulated/0/output"
 * )
 * ```
 *
 * Supported combinations:
 *
 * - JVM → JVM
 * - SAF → SAF
 * - JVM → SAF
 * - SAF → JVM
 *
 * ## Implementation details
 *
 * This implementation uses:
 *
 * - [DocumentFile] for directory traversal and metadata access
 * - [DocumentsContract] for document operations such as rename
 *
 * ## Materialization support
 *
 * SAF documents can be materialized into the application's internal
 * storage using [materialize]. This is useful when a library or tool
 * requires a real filesystem path instead of a SAF URI.
 *
 * Materialized content can later be removed using
 * [clearMaterialized].
 *
 * ## Requirements
 *
 * - User-granted SAF permissions
 * - Valid tree or document URIs
 *
 * @param context Android context used to access SAF providers
 */
class AndroidSafFileSystem(context: Context) : JvmFileSystem()	{
	private val context = context
	private var selectedParentUri: Uri? = null
	private val contentResolver = context.contentResolver

	private var materializedDir: File? = null

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
        is String ->	{
        	if(input.startsWith("content://"))	{
        		DocumentFile.fromTreeUri(context, Uri.parse(input))
        	}else	{
        		results += super.resolveFiles(listOf(input),extensions)
        		continue
        	}
        }
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
    if(directory.startsWith("content://"))	{
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
		}else	{
			return super.findFiles(directory,extensions)
		}
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
		if(path.startsWith("content://"))	{
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
    }else	{
    	return super.createDirectory(path)
    }
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
		if(path.startsWith("content://"))	{
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
    }else	{
    	return super.createFile(path)
    }
	}

	/**
	 * Opens an output stream for writing to a SAF file.
	 *
	 * @param path file URI string
	 * @return output stream or null if the file cannot be opened
	 */
	override fun openOutputStream(path: String): OutputStream?	{
		return if(path.startsWith("content://"))	{
			contentResolver.openOutputStream(Uri.parse(path))
		}else	{
			super.openOutputStream(path)
		}
	}

	/**
	 * Opens an input stream for reading from a SAF file.
	 *
	 * @param path file URI string
	 * @return input stream or null if inaccessible
	 */
	override fun openInputStream(path: String): InputStream?	{
		return if(path.startsWith("content://"))	{
			contentResolver.openInputStream(Uri.parse(path))
		}else	{
			super.openInputStream(path)
		}
	}

	/**
	 * Lists immediate children of a SAF directory.
	 *
	 * @param path directory URI string
	 * @return list of child file URIs
	 */
	override fun listFiles(path: String): List<String>	{
		if(path.startsWith("content://"))	{
			val document = DocumentFile.fromTreeUri(context,Uri.parse(path))

			return document?.listFiles()
				?.map	{ it.getUri().toString() }
				?.toList()
				?: emptyList()
		}else	{
			return super.listFiles(path)
		}
	}

	/**
	 * Checks whether a SAF file or directory exists.
	 *
	 * @param path URI string
	 * @return true if accessible and exists, false otherwise
	 */
	override fun exists(path: String): Boolean	{
		if(path.startsWith("content://"))	{
			val document = DocumentFile.fromTreeUri(context,Uri.parse(path))

			return document?.exists() ?: false
		}else	{
			return super.exists(path)
		}
	}

	/**
	 * Deletes a SAF file or directory.
	 *
	 * @param path URI string
	 * @return true if deletion succeeded
	 */
	override fun delete(path: String): Boolean	{
		if(path.startsWith("content://"))	{
			val document = DocumentFile.fromTreeUri(context,Uri.parse(path))

			return document?.delete() ?: false
		}else	{
			return super.delete(path)
		}
	}

	/**
	 * Renames a SAF document.
	 *
	 * @param src source file URI
	 * @param target new display name
	 * @return URI string of renamed document, or null if failed
	 */
	override fun rename(src: String, target: String): String?	{
		if(src.startsWith("content://"))	{
			return DocumentsContract
				.renameDocument(contentResolver,Uri.parse(src),target)
				?.toString()
		}else	{
			return super.rename(src,target)
		}
	}

	/**
	 * Checks whether the given URI points to a file.
	 *
	 * @param path file URI string
	 * @return true if file
	 */
	override fun isFile(path: String): Boolean	{
		if(path.startsWith("content://"))	{
			DocumentFile.fromTreeUri(context,Uri.parse(path))?.let	{
				return it.isFile
			}
		}else	{
			return File(path).isFile
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
		if(path.startsWith("content://"))	{
			DocumentFile.fromTreeUri(context,Uri.parse(path))?.let	{
				return it.isDirectory
			}
		}else	{
			return File(path).isDirectory
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
		if(path.startsWith("content://"))	{
			DocumentFile.fromTreeUri(context,Uri.parse(path))?.let	{
				return it.lastModified()
			}
		}else	{
			return File(path).lastModified()
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
		if(path.startsWith("content://"))	{
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
    }else	{
    	return super.resolvePath(path)
    }
	}

	/**
	 * Returns the size of a SAF file in bytes.
	 *
	 * @param path file URI string
	 * @return file size or 0 if unavailable
	 */
	override fun size(path: String): Long	{
		if(path.startsWith("content://"))	{
			return DocumentFile.fromSingleUri(context,Uri.parse(path))
				?.length() ?: 0L
		}else	{
			return super.size(path)
		}
	}

	/**
	 * Returns the display name of a file or directory.
	 *
	 * For SAF documents this is the provider-reported document name.
	 * For regular filesystem paths this is equivalent to
	 * `File(path).name`.
	 *
	 * @param path filesystem path or SAF URI
	 * @return file or directory name, or an empty string if unavailable
	 */
	override fun getName(path: String): String	{
		if(path.startsWith("content://"))	{
			val doc = DocumentFile.fromTreeUri(context,Uri.parse(path))

			return doc?.getName() ?: ""
		}else	{
			return File(path).name
		}
	}

	/**
	 * Returns the application's internal files directory.
	 *
	 * This directory is typically used as the destination for
	 * materialized SAF documents.
	 *
	 * @return internal application files directory
	 */
	override fun getAndroidFilesDir(): File?	{
		return context.getFilesDir()
	}

	/**
	 * Converts a SAF document or directory into a real filesystem path
	 * located inside the application's internal storage.
	 *
	 * Files are copied into the specified output directory.
	 * Directories are recursively materialized while preserving their
	 * structure.
	 *
	 * If the supplied path is already a regular filesystem path,
	 * the original path is returned unchanged.
	 *
	 * Materialization is useful when working with tools or libraries
	 * that require direct filesystem access and cannot consume
	 * `content://` URIs.
	 *
	 * @param path filesystem path or SAF URI
	 * @param outDir internal output directory name
	 * @return path to the materialized file or directory
	 */
	override fun materialize(path: String, outDir: String): String {
		if(path.startsWith("content://"))	{
	    val baseDir = getAndroidFilesDir()
	        ?: throw IllegalStateException("Missing internal dir")

	    val outRoot = File(baseDir, outDir).apply { mkdirs() }

	    return if (isDirectory(path)) {
	      materializeDirectory(path, outRoot)
	    } else {
	      materializeFile(path, outRoot)
	    }
    }else	{
    	return path
    }
	}

	
	private fun materializeFile(path: String, outRoot: File): String {
    val name = File(path).nameWithoutExtension
    val ext = File(path).extension

    var fileName =	"${name}_${System.currentTimeMillis()}"
    if(ext.isNotEmpty()) fileName = "$fileName.$ext"
    
    val outFile = File(outRoot, fileName)

    if (!outFile.exists()) {
    	outFile.createNewFile()
    	
      openInputStream(path)!!.use { input ->
        outFile.outputStream().use { output ->
          input.copyTo(output)
        }
      }
    }
    return outFile.absolutePath
	}

	private fun materializeDirectory(path: String, outRoot: File): String {
    val dirName = getName(path)
    val targetDir = File(outRoot, dirName).apply { mkdirs() }

    listFiles(path).forEach { child ->
      if (isDirectory(child)) {
          materializeDirectory(child, targetDir)
      } else {
        val outFile = File(targetDir, getName(child))

        if (!outFile.exists()) {
        	outFile.createNewFile()
        	
          openInputStream(child)!!.use { input ->
            outFile.outputStream().use { output ->
              input.copyTo(output)
            }
          }
        }
      }
    }
    return targetDir.absolutePath
	}

	/**
	 * Removes files previously created by [materialize].
	 *
	 * The specified target is resolved relative to the application's
	 * internal files directory and deleted recursively.
	 *
	 * @param target materialized directory name
	 */
	override fun clearMaterialized(target: String) {
    val baseDir = getAndroidFilesDir() ?: return
    File(baseDir, target).deleteRecursively()
	}
}

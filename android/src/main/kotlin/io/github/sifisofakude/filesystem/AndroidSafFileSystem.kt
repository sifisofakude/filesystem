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
	private val context = context.applicationContext
	private val contentResolver = context.contentResolver

	@Volatile
	private var selectedParentUri: Uri? = null

	private var materializedDir: File? = null

	/**
	 * Sets the active SAF root directory used for all relative file operations.
	 *
	 * All directory creation and relative resolution operations will be
	 * anchored to this URI.
	 *
	 * @param newParentUri SAF tree URI representing a user-granted directory
	 */
	fun changeSelectedDirectory(newParentUri: Uri?)	{
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

	fun isSafUri(path: String): Boolean = path.startsWith("content://")

	fun isRelative(path: String): Boolean	{
		if(isSafUri(path))	{
			return false
		}
		return !File(path).isAbsolute
	}

	fun resolveRelativeUri(rootTreeUri: Uri, relativePath: String): String? {
    val treeDocumentId = DocumentsContract.getTreeDocumentId(rootTreeUri) ?: return null
    
    val segments = relativePath.split("/").filter { it.isNotEmpty() }
    if (segments.isEmpty()) {
        // If the path is empty, return a document URI pointing directly to the root
      return DocumentsContract
      	.buildDocumentUriUsingTree(rootTreeUri, treeDocumentId)
      	.toString()
    }
    
    val combinedIdBuilder = StringBuilder(treeDocumentId)
    for (segment in segments) {
      combinedIdBuilder.append("/").append(segment)
    }
    val targetDocumentId = combinedIdBuilder.toString()
    
    val finalUri = DocumentsContract.buildDocumentUriUsingTree(rootTreeUri, targetDocumentId)
    
    // val correctedUriString = finalUri.toString()
        // .replace("%3A", ":")
        // .replace("%2F", "/")
        
    return finalUri.toString()
	}

	private fun tempPath(path: String): String?	{
		return if(isSafUri(path))	{
			path
		}else	{
			selectedParentUri?.let	{ currentUri ->
				resolveRelativeUri(currentUri,path)
			}
		}
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
        	if(selectedParentUri != null || isSafUri(input))	{
        		var path: String? = input
        		selectedParentUri?.let	{
        			if(isRelative(path)) path = resolveRelativeUri(it,path)
        		}
        		DocumentFile.fromTreeUri(context, Uri.parse(path))
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
    if(selectedParentUri != null || isSafUri(directory))	{
    	val tmpDir = tempPath(directory) ?: return emptyList()
    	
	    val rootUri = Uri.parse(tmpDir)
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
		return super.findFiles(directory,extensions)
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
		if(isSafUri(path) || selectedParentUri != null)	{
			val currentUri = tempPath(path)?.let	{
				Uri.parse(it)
			} ?: return null

	    val docId = DocumentsContract.getDocumentId(currentUri)
	    val mainSplit = docId.split(':')
	    if(mainSplit.size < 2) return null

	    val volumeId = mainSplit[0]
	    val pathParts = mainSplit[1].split('/')

	    if(pathParts.size < 2) return null

    	val uri = DocumentsContract.buildTreeDocumentUri(
    		currentUri.getAuthority(),"${volumeId}:${pathParts[0]}"
    	)

    	DocumentFile.fromTreeUri(context,uri)?.let	{
    		var rootFolder = it
    		var currentFolder: DocumentFile? = null
    		
    		for(i in 1 until pathParts.size)	{
    			currentFolder = rootFolder.findFile(pathParts[i])

    			if(currentFolder == null)	{
    				currentFolder =	rootFolder.createDirectory(pathParts[i]) ?: return null
    			}else if(currentFolder.isFile)	{
    				return null
    			}
    			rootFolder = currentFolder
    		}
    		return rootFolder.uri.toString()
    	}
    	return null
    }
    return super.createDirectory(path)
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
		if(isSafUri(path) || selectedParentUri != null)	{
			val currentUri = tempPath(path) ?: return null

			val parentUri = getParentFile(currentUri) ?: return null
			if(!exists(parentUri)) createDirectory(parentUri) ?: return null
			
	    var parent = DocumentFile.fromTreeUri(context, Uri.parse(parentUri)) ?: return null

	    val fileName = getName(path)

	    parent.findFile(fileName)?.let {
	      if (it.isFile) return it.uri.toString()
	      else if(it.isDirectory) return null
	    }

	    return parent.createFile("application/octet-stream", fileName)?.uri?.toString()
    }
    return super.createFile(path)
	}

	/**
	 * Opens an output stream for writing to a SAF file.
	 *
	 * @param path file URI string
	 * @return output stream or null if the file cannot be opened
	 */
	override fun openOutputStream(path: String): OutputStream?	{
		return if(isSafUri(path) || selectedParentUri != null)	{
			val tmpPath = tempPath(path) ?: return null
			contentResolver.openOutputStream(Uri.parse(tmpPath))
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
		return if(isSafUri(path) || selectedParentUri != null)	{
			val tmpPath = tempPath(path) ?: return null
			contentResolver.openInputStream(Uri.parse(tmpPath))
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
		if(isSafUri(path) || selectedParentUri != null)	{
			val tmpPath = tempPath(path) ?: return emptyList()
			
			val document = DocumentFile.fromTreeUri(context,Uri.parse(tmpPath))

			return document?.listFiles()
				?.map	{ it.getUri().toString() }
				?.toList()
				?: emptyList()
		}
		return super.listFiles(path)
	}

	/**
	 * Checks whether a SAF file or directory exists.
	 *
	 * @param path URI string
	 * @return true if accessible and exists, false otherwise
	 */
	override fun exists(path: String): Boolean	{
		if(isSafUri(path) || selectedParentUri != null)	{
			val tmpPath = tempPath(path) ?: return false
			
			val document = DocumentFile.fromTreeUri(context,Uri.parse(tmpPath))

			return document?.exists() ?: false
		}
		return super.exists(path)
	}

	override fun getParentFile(path: String): String?	{
		if(isSafUri(path) || selectedParentUri != null)	{
			val tmpPath = tempPath(path) ?: return null

			val uri = Uri.parse(tmpPath)
			val docId = DocumentsContract.getTreeDocumentId(uri)

			val lastSlashIndex = docId.lastIndexOf("/")
			if(lastSlashIndex == -1) return null

			return DocumentsContract
				.buildTreeDocumentUri(
					uri.getAuthority(),
					docId.substring(0,lastSlashIndex)
				)
				.toString()
		}
		return super.getParentFile(path)
	}

	/**
	 * Deletes a SAF file or directory.
	 *
	 * @param path URI string
	 * @return true if deletion succeeded
	 */
	override fun delete(path: String): Boolean	{
		if(isSafUri(path) || selectedParentUri != null)	{
			val tmpPath = tempPath(path) ?: return false
			val document = DocumentFile.fromTreeUri(context,Uri.parse(tmpPath))

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
		if(isSafUri(src) || selectedParentUri != null)	{
			val tmpPath = tempPath(src) ?: return null
			
			return DocumentsContract
				.renameDocument(contentResolver,Uri.parse(tmpPath),target)
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
		if(isSafUri(path) || selectedParentUri != null)	{
			val tmpPath = tempPath(path) ?: return false
			DocumentFile.fromSingleUri(context,Uri.parse(tmpPath))?.let	{
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
		if(isSafUri(path) || selectedParentUri != null)	{
			val tmpPath = tempPath(path) ?: return false
			
			DocumentFile.fromSingleUri(context,Uri.parse(tmpPath))?.let	{
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
		if(isSafUri(path) || selectedParentUri != null)	{
			val tmpPath = tempPath(path) ?: return -1
			
			DocumentFile.fromSingleUri(context,Uri.parse(tmpPath))?.let	{
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
		if(isSafUri(path) || selectedParentUri != null)	{
			val tmpPath = tempPath(path) ?: return path
	    val uri = Uri.parse(tmpPath)

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
		if(isSafUri(path) || selectedParentUri != null)	{
			val tmpPath = tempPath(path) ?: return 0L
			
			return DocumentFile.fromSingleUri(context,Uri.parse(tmpPath))
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
		if(isSafUri(path))	{
			val docId = DocumentsContract.getTreeDocumentId(Uri.parse(path))
			
			val lastSlashIndex = docId.lastIndexOf('/')
			if(lastSlashIndex == -1) return ""
			
			return docId.substring(lastSlashIndex+1)
		}
		return File(path).name
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
		if(isSafUri(path) || selectedParentUri != null)	{
			val tmpPath = tempPath(path) ?: return path
			
	    val baseDir = getAndroidFilesDir()
	        ?: throw IllegalStateException("Missing internal dir")

	    val outRoot = File(baseDir, outDir).apply { mkdirs() }

	    return if (isDirectory(tmpPath)) {
	      materializeDirectory(tmpPath, outRoot)
	    } else {
	      materializeFile(tmpPath, outRoot)
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
        	
          openInputStream(child)?.use { input ->
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
	override fun clearMaterialized(path: String) {
    val baseDir = getAndroidFilesDir() ?: return
    File(baseDir, path).deleteRecursively()
	}
}

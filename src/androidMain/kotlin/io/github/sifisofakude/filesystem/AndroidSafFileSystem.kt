package io.github.sifisofakude.filesystem

import android.net.Uri
import android.content.Context
import android.provider.DocumentsContract

import java.io.File

import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered

/**
 * Android Storage Access Framework (SAF) implementation of [FileSystemUtil].
 *
 * [AndroidSafFileSystem] extends [JvmFileSystem] and provides filesystem
 * operations for Android Storage Access Framework resources while retaining
 * support for conventional JVM filesystem paths.
 *
 * SAF resources are identified by `content://` URIs and are accessed through
 * Android's [DocumentsContract] API. Depending on the
 * document provider, an SAF resource may represent local storage, removable
 * storage, cloud storage, or another provider-backed document tree.
 *
 * ### SAF path resolution
 *
 * A path can be represented in several forms:
 *
 * - A regular filesystem path, such as `/data/local/tmp/file.txt`.
 * - A relative path, such as `src/main.kt`, when a SAF directory has been
 *   selected with [changeSelectedDirectory].
 * - An explicit SAF URI, such as `content://...`.
 * - An internal SAF path consisting of an SAF root followed by `||` and a
 *   relative path.
 *
 * The `||` representation is an internal path format used by this
 * implementation to associate a relative path with a specific SAF root.
 * Applications normally do not need to construct this representation
 * themselves.
 *
 * ### Selected SAF directory
 *
 * [changeSelectedDirectory] establishes the directory against which relative
 * paths are resolved.
 *
 * For example:
 *
 * ```kotlin
 * fs.changeSelectedDirectory(treeUri)
 *
 * fs.createFile("project/src/Main.kt")
 * fs.openSource("project/src/Main.kt")
 * ```
 *
 * An explicit `content://` URI is resolved independently of the currently
 * selected directory.
 *
 * ### SAF and regular filesystem interoperability
 *
 * Operations are delegated to the appropriate backend based on the supplied
 * path. This allows regular filesystem paths and SAF resources to be used
 * through the same [FileSystemUtil] API where the underlying operation
 * supports both.
 *
 * ### Materialization
 *
 * Some libraries and APIs require a conventional filesystem path and cannot
 * consume a `content://` URI directly. [materialize] copies an SAF resource
 * into the application's private files directory and returns the resulting
 * filesystem path.
 *
 * Materialized resources can be removed using [clearMaterialized].
 *
 * @param context Android context used to access the ContentResolver and
 * document providers.
 *
 * @see FileSystemUtil
 * @see JvmFileSystem
 * @see DocumentsContract
 */
class AndroidSafFileSystem(context: Context) : JvmFileSystem()	{
	private val context = context.applicationContext
	private val contentResolver = context.contentResolver

	@Volatile
	private var selectedParentUri: Uri? = null

	/**
	 * Sets the active SAF root directory used for all relative file operations.
	 *
	 * All directory creation and relative resolution operations will be
	 * anchored to this URI.
	 *
	 * @param newParentUri SAF tree URI representing a user-granted directory
	 */
	fun changeSelectedDirectory(newParentUri: Uri?)	{
		newParentUri?.let	{ parent ->
			if(isTreeUri("$parent") && isDirectory("$parent"))	{
				selectedParentUri = parent
			}else	{
				selectedParentUri = null
			}
		}
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
	 * Determines whether the supplied path is an Android Storage Access Framework
	 * URI.
	 *
	 * @param path path or URI to inspect.
	 * @return `true` if [path] starts with the `content://` URI scheme,
	 * otherwise `false`.
	 */
	fun isSafUri(path: String): Boolean = path.startsWith("content://")

	/**
	 * Determines whether the supplied path should be interpreted in the
	 * currently active SAF context.
	 *
	 * A path is considered to be in SAF context when it is either an SAF
	 * `content://` URI or when a SAF directory has been selected using
	 * [changeSelectedDirectory].
	 *
	 * @param path path or URI to evaluate.
	 * @return `true` if SAF resolution should be used, otherwise `false`.
	 */
	fun isSafContext(path: String): Boolean	{
		return if(isSafUri(path))	{
			true
		}else	{
			if(isRelative(path)) selectedParentUri != null
			else false
		}
	}

	/**
	 * Determines whether the supplied path represents a relative location.
	 *
	 * Conventional filesystem paths are evaluated using [File.isAbsolute].
	 * For SAF URIs, the URI is resolved relative to its discoverable SAF tree
	 * root. A URI representing the tree root itself is not considered relative.
	 *
	 * @param path path or SAF URI to evaluate.
	 * @return `true` if the path represents a relative location, otherwise `false`.
	 */
	override fun isRelative(path: String): Boolean	{
		return if(path.startsWith("content://") || path.startsWith("file://"))	{
			!path.substringAfter("||","").isBlank()
		}else	{
			!File(path).isAbsolute
		}
	}

	/**
	 * Returns whether the supplied URI represents a SAF tree URI.
	 *
	 * @param uri URI string to inspect.
	 * @return `true` when the URI represents a document tree.
	 */
	fun isTreeUri(uri: String): Boolean	= DocumentsContract.isTreeUri(Uri.parse(uri))

	/**
	 * Resolves a relative path against an SAF tree or document URI.
	 *
	 * The relative path is converted into a SAF document ID and resolved using
	 * [DocumentsContract]. The returned URI identifies the corresponding
	 * document within the provider's tree.
	 *
	 * @param uri SAF tree or document URI used as the resolution root.
	 * @param relativePath path relative to [uri].
	 * @return resolved document URI, or `null` if [uri] cannot be used as a
	 * resolution root or does not represent a directory when a relative path
	 * is supplied.
	 */
	fun resolveRelativeUri(uri: Uri, relativePath: String): String?	{
		if(!isSafUri(uri.toString())) return null

		val isTree = DocumentsContract.isTreeUri(uri)
		val isDocument = DocumentsContract.isDocumentUri(context,uri)
		val isTreeDocument = isTree && isDocument

		val docId = if(isTreeDocument)	{
			DocumentsContract.getTreeDocumentId(uri)
		}else if(isDocument)	{
			DocumentsContract.getDocumentId(uri)
		}else	{
			return null
		}

		val completeDocId = if(isTreeDocument)	{
			val metadata = DocumentsContract.Document.COLUMN_MIME_TYPE
			val mime = getDocumentMetadata(uri,listOf(metadata))[metadata]

			if(mime != DocumentsContract.Document.MIME_TYPE_DIR) return null

			if(!relativePath.isBlank())	{
				if(isTreeDocument)	{
					"${DocumentsContract.getDocumentId(uri)}/${relativePath.trim('/')}"
				}else	{
					"$docId/${relativePath.trim('/')}"
				}
			}else	{
				docId
			}
		}else	{
			return uri.toString()
		}

		val treeUri = DocumentsContract.buildTreeDocumentUri(uri.authority,docId)

		return DocumentsContract
			.buildDocumentUriUsingTree(treeUri,completeDocId)
			.toString()
	}

	/**
	 * Queries metadata columns for an SAF document.
	 *
	 * @param rootUri SAF tree or document URI.
	 * @param metadata document metadata columns to query.
	 * @return map of requested column names to their values. Returns an empty map
	 * if the document cannot be queried or the requested metadata is unavailable.
	 */
	fun getDocumentMetadata(rootUri: Uri,metadata: List<String>): Map<String,String>	{
		val isTree = DocumentsContract.isTreeUri(rootUri)
		val isDocument = DocumentsContract.isDocumentUri(context,rootUri)
		val isTreeDocument = isTree && isDocument
		
		val resolvedUri = if(isTreeDocument || isDocument)	{
			rootUri
		}else	{
			val treeDocId = DocumentsContract.getTreeDocumentId(rootUri)

			DocumentsContract
				.buildDocumentUriUsingTree(rootUri,treeDocId)
		}

		val projection = metadata.toTypedArray()
		val result = mutableMapOf<String,String>()

		contentResolver.query(resolvedUri,projection,null,null,null)?.use { cursor ->
			if(cursor.count > 0)	{
				cursor.moveToFirst()
				
				val metadataIndexes = mutableMapOf<String,Int>()

				metadata.forEach	{
					val index = cursor.getColumnIndex(it)
					if(index > -1)	{
						metadataIndexes[it] = index
					}
				}

				do	{
					for((k,v) in metadataIndexes)	{
						val md = cursor.getString(v)

						result[k] = md
					}
				}while(cursor.moveToNext())
			}
		}

		return result
	}

	/**
	 * Splits an SAF path into its root URI and relative path components.
	 *
	 * SAF paths may use the internal `||` separator to represent a path relative
	 * to a tree URI:
	 *
	 * ```
	 * content://...||directory/file.txt
	 * ```
	 *
	 * For ordinary relative paths, the currently selected SAF directory is used
	 * as the root.
	 *
	 * @param uri SAF URI or relative path.
	 * @return parsed SAF root and relative path, or an empty [SafRelativePath]
	 * if the path is outside the SAF context.
	 */
	fun relativePathFromUri(uri: String): SafRelativePath	{
		if(isSafContext(uri))	{
			val root = if(isSafUri(uri))	{
				uri.substringBefore("||",uri)
			}else	{
				selectedParentUri.toString()
			}

			val relative = if(isSafUri(uri))	{
				uri.substringAfter("||","")
			}else	{
				uri
			}
			
			return SafRelativePath(
				rootUri = root,
				relativePath = relative
			)
		}
		return SafRelativePath("","")
	}

	/**
	 * Returns the document ID associated with an SAF URI.
	 *
	 * Tree URIs are resolved using [DocumentsContract.getTreeDocumentId], while
	 * document URIs are resolved using [DocumentsContract.getDocumentId].
	 *
	 * @param uri SAF URI to inspect.
	 * @return document ID, or `null` when the URI cannot be interpreted as a
	 * supported SAF URI.
	 */
	fun getDocumentId(uri: Uri): String?	{
		return if(isTreeUri(uri.toString()))	{
			DocumentsContract.getTreeDocumentId(uri)
		}else	{
			DocumentsContract.getDocumentId(uri)
		}
	}

	/**
	 * Constructs the intermediate URI representation used to resolve a path
	 * within the current SAF context.
	 *
	 * Explicit SAF URIs are returned unchanged. Relative paths are combined
	 * with the currently selected SAF directory so that the resulting URI can
	 * be decomposed by [relativePathFromUri] and resolved through
	 * [DocumentFile].
	 *
	 * The resulting value is an internal representation and is not assumed to
	 * be the final document URI exposed by the SAF provider.
	 *
	 * @param path SAF URI or relative path.
	 * @return intermediate SAF URI representation, or `null` when no SAF root
	 * is available for a relative path.
	 */
	private fun tempPath(path: String): String?	{
		return if(isSafUri(path))	{
			path
		}else	{
			selectedParentUri?.let	{
				if(path == "." || path.isBlank())	{
					it.toString()
				}else	{
					"${it.toString()}||$path"
				}
			}
		}
	} 

	override fun combinePath(parent: String, child: String): String	{
		if(isSafUri(parent))	{
			return if(isSafUri(parent))	{
				if(parent.contains("||")) "${parent.trim('/')}/${child.trim('/')}"
				else "$parent||${child.trim('/')}"
			}else	{
				super.combinePath(parent,child)
			}
		}
		return super.combinePath(parent,child)
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
        is Uri -> input
        is String ->	{
        	if(isSafContext(input))	{
        		val tmpPath = tempPath(input) ?: continue
        		val relativeUri = relativePathFromUri(tmpPath)
        		val resolvedUri = resolveRelativeUri(Uri.parse(relativeUri.rootUri),relativeUri.relativePath)
        			?: return emptyList()

        		Uri.parse(resolvedUri)
        	}else	{
        		results += super.resolveFiles(listOf(input),extensions)
        		continue
        	}
        }
        else -> null
      } ?: continue

      if (isFile("$root")) {
        val name = getName("$root")

        if (extensions.isNotEmpty()) {
          val ext = getExtension(name)
          if (ext !in extensions) continue
        }

        results.add(
          FileSource(
            relativePath = name,
            absolutePath = "$root"
          )
        )
        continue
      }

      if (isDirectory("$root")) {
        walkSaf(root, "", extensions, results)
      }
    }

    return results
	}

	/**
	 * Moves a file or directory to a destination.
	 *
	 * This implementation delegates to [FileSystemUtil.moveByStream], providing
	 * a provider-independent move operation for SAF resources. This avoids
	 * relying on provider-specific support for native SAF move operations.
	 *
	 * Directories are moved recursively.
	 *
	 * @param src source file or directory path.
	 * @param dst destination  path.
	 * @return the resulting path of the moved resource, or `null` if the
	 * operation fails.
	 *
	 * @see FileSystemUtil.moveByStream
	 */
	override fun move(src: String, dst: String): String?	{
		return moveByStream(src,dst)
	}

	/**
	 * Copies a file or directory to a destination.
	 *
	 * This implementation delegates to [FileSystemUtil.copyByStream], providing
	 * a provider-independent copy operation for SAF resources. This avoids
	 * relying on provider-specific support for native SAF copy operations.
	 *
	 * Directories are copied recursively.
	 *
	 * @param src source file or directory path.
	 * @param dst destination path.
	 * @param overwrite whether an existing destination may be replaced.
	 * @return the resulting path of the copied resource, or `null` if the
	 * operation fails.
	 *
	 * @see FileSystemUtil.copyByStream
	 */
	override fun copy(src: String, dst: String, overwrite: Boolean): String?	{
		return copyByStream(src,dst,overwrite)
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
	    dir: Uri,
	    basePath: String,
	    extensions: Set<String>,
	    out: MutableList<FileSource>
	) {
		if(isDirectory("$dir"))	{
			listFiles("$dir").forEach	{ file ->
				val rel = if(basePath.isEmpty()) file else "$basePath/$file"

				if(isDirectory("$dir||$file"))	{
					val resolvedUri = resolveRelativeUri(dir,file) ?: return@forEach
					walkSaf(Uri.parse(resolvedUri),rel,extensions,out)
				}else	{
					if(extensions.isEmpty() || getExtension(file) in extensions)	{
						out += FileSource(
							relativePath = rel,
							absolutePath = resolveRelativeUri(dir,file) ?: return@forEach
						)
					}
				}
			}
		}
	}

	/**
	 * Recursively finds files beneath a directory.
	 *
	 * SAF directories are traversed recursively and matching files are returned
	 * as paths relative to the supplied directory.
	 *
	 * For regular filesystem paths, the implementation delegates to
	 * [JvmFileSystem.findFiles].
	 *
	 * @param directory directory to search.
	 * @param extensions allowed file extensions. An empty set includes all files.
	 * @return list of relative paths for matching files.
	 */
	override fun findFiles(directory: String, extensions: Set<String>): List<String> {
    if(isSafContext(directory))	{
    	val tmpPath = tempPath(directory) ?: return emptyList()
			val relativeUri = relativePathFromUri(tmpPath)
			val resolvedUri = resolveRelativeUri(Uri.parse(relativeUri.rootUri),relativeUri.relativePath)
				?: return emptyList()
				
   		val results = mutableListOf<FileSource>()

	    walkSaf(Uri.parse(resolvedUri),"",extensions,results)
	    
    	return results.map { it.relativePath }.toList()
		}
		return super.findFiles(directory,extensions)
	}

	/**
	 * Creates a directory structure in the current SAF context.
	 *
	 * Missing intermediate directories are created automatically. Existing
	 * directories are reused, while an existing file occupying a required
	 * directory name causes the operation to fail.
	 *
	 * For regular filesystem paths, the implementation delegates to
	 * [JvmFileSystem.createDirectory].
	 *
	 * @param path directory path to create.
	 * @return the supplied [path] when the directory exists or was successfully
	 * created, or `null` when creation fails.
	 */
	override fun createDirectory(path: String): String? {
		if(isSafContext(path))	{
			val relativeUri = if(isSafUri(path))	{
				relativePathFromUri(path)
			}else	{
				SafRelativePath(selectedParentUri.toString(),path)
			}

			val relativePath = relativeUri.relativePath.trim('/')

			var currentUri = resolveRelativeUri(Uri.parse(relativeUri.rootUri),"") ?: return null

			if(!isDirectory(currentUri)) return null

			for(segment in relativePath.split('/'))	{
				if(exists("$currentUri||$segment"))	{
					if(!isDirectory("$currentUri||$segment")) {
						return null
					}else	{
						currentUri = resolveRelativeUri(Uri.parse(currentUri),segment) ?: return null
						continue
					}
				}
				
				val mimeType = DocumentsContract.Document.MIME_TYPE_DIR

				val newUri = DocumentsContract.createDocument(
					contentResolver,
					Uri.parse(currentUri),
					mimeType,
					segment
				)

				if(newUri != null) {
					currentUri = resolveRelativeUri(Uri.parse(currentUri),segment) ?: return null
				}else	{
					return null
				}
			}
			return path
		}
		return super.createDirectory(path)
	}

	/**
	 * Creates a file in the current SAF context.
	 *
	 * Missing parent directories are created automatically. If a file with the
	 * requested name already exists, its path is returned. If a directory already
	 * occupies that path, creation fails.
	 *
	 * For regular filesystem paths, the implementation delegates to
	 * [JvmFileSystem.createFile].
	 *
	 * @param path file path to create.
	 * @return the supplied [path] when the file exists or was successfully
	 * created, or `null` when creation fails.
	 */
	override fun createFile(path: String): String? {
		if(isSafContext(path))	{
			val fileName = getName(path)
			val relativeUri = relativePathFromUri(path)
			val rootUri = Uri.parse(relativeUri.rootUri)
			val relativePath = relativeUri.relativePath
			val relativeParent = super.getParentFile(relativePath) ?: ""

			if(!DocumentsContract.isTreeUri(rootUri) || relativePath.isBlank()) return null

			if(exists(path))	{
				return if(isFile(path))	{
					path
				}else	{
					null
				}
			}

			val parentUri = if(relativeParent.isBlank())	{
				rootUri.toString()
			}else	{
				"$rootUri||$relativeParent"
			}

			return createDirectory(parentUri)?.let	{
				val resolvedParent = resolveRelativeUri(rootUri,relativeParent) ?: return null

				val fileUri = DocumentsContract.createDocument(
					contentResolver,
					Uri.parse(resolvedParent),
					"application/octet-stream",
					fileName
				)

				if(fileUri != null) path
				else null
			}
    }
    return super.createFile(path)
	}

	/**
	 * Opens a buffered sink for writing to a file.
	 *
	 * For SAF resources, writing is performed through the Android
	 * [ContentResolver]. When [append] is `true`, existing content is preserved
	 * and new data is appended. Otherwise, the existing content is replaced.
	 *
	 * Conventional filesystem paths are delegated to [JvmFileSystem].
	 *
	 * @param path filesystem path or SAF URI.
	 * @param append whether data should be appended to the existing file.
	 * @return buffered sink, or `null` if the file cannot be opened.
	 */
	override fun openSink(path: String, append: Boolean): Sink?	{
		return if(isSafContext(path))	{
			val mode = if(append) "wa" else "w"
			val tmpPath = relativePathFromUri(path)
			val resolvedUri = resolveRelativeUri(Uri.parse(tmpPath.rootUri),tmpPath.relativePath)
				?: return null

			if(!exists(resolvedUri)) createFile(path) ?: return null
			
			contentResolver
				.openOutputStream(Uri.parse(resolvedUri),mode)
				?.asSink()?.buffered()
		}else	{
			super.openSink(path,append)
		}
	}

	/**
	 * Opens a buffered source for reading from a file.
	 *
	 * For SAF resources, data is read through the Android [ContentResolver].
	 * Conventional filesystem paths are delegated to [JvmFileSystem].
	 *
	 * @param path filesystem path or SAF URI.
	 * @return buffered source, or `null` if the file cannot be opened.
	 */
	override fun openSource(path: String): Source?	{
		return if(isSafContext(path))	{
			val tmpPath = relativePathFromUri(path)
			val resolvedUri = resolveRelativeUri(Uri.parse(tmpPath.rootUri),tmpPath.relativePath)

			contentResolver
				.openInputStream(Uri.parse(resolvedUri))
				?.asSource()?.buffered()
		}else	{
			super.openSource(path)
		}
	}

	/**
	 * Lists immediate children of a SAF directory.
	 *
	 * @param path directory URI string
	 * @return list of child file URIs
	 */
	override fun listFiles(path: String): List<String>	{
		if(isSafContext(path))	{
			val fileList = mutableListOf<String>()

			val relativeUri = relativePathFromUri(path)
			val resolvedUri = resolveRelativeUri(Uri.parse(relativeUri.rootUri),relativeUri.relativePath)

			try	{
				val parentDocId = DocumentsContract.getDocumentId(Uri.parse(resolvedUri))
				val childrenUri = DocumentsContract
					.buildChildDocumentsUriUsingTree(Uri.parse(relativeUri.rootUri),parentDocId)

				val projection = arrayOf(
					DocumentsContract.Document.COLUMN_DISPLAY_NAME,
				)

				contentResolver.query(childrenUri,projection,null,null,null)?.use { cursor ->
					if(cursor.count > 0)	{
						cursor.moveToFirst()
						
						val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)

						do	{
							val name = cursor.getString(nameIndex)
							fileList.add("$name")
						}while(cursor.moveToNext())
					}
				}
			}catch(_: Exception) {}

			return fileList
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
		if(isSafContext(path))	{
			val relativeUri = relativePathFromUri(path)
			val resolvedUri = resolveRelativeUri(Uri.parse(relativeUri.rootUri),relativeUri.relativePath)
				?: return false

			val metadata = DocumentsContract.Document.COLUMN_DOCUMENT_ID
			return getDocumentMetadata(Uri.parse(resolvedUri),listOf(metadata))[metadata] != null
		}
		return super.exists(path)
	}

	/**
	 * Returns the parent of the specified file or directory.
	 *
	 * For SAF resources, the parent is resolved relative to the resource's
	 * SAF tree root when necessary. For conventional filesystem paths, the
	 * implementation delegates to [JvmFileSystem].
	 *
	 * @param path filesystem path or SAF URI.
	 * @return URI or filesystem path of the parent, or `null` if the parent
	 * cannot be resolved.
	 */
	override fun getParentFile(path: String): String?	{
		if(isSafContext(path))	{
			val relativeUri = relativePathFromUri(path)
			val resolvedUri = resolveRelativeUri(Uri.parse(relativeUri.rootUri),relativeUri.relativePath)
				?: return null

			val isTree = DocumentsContract.isTreeUri(Uri.parse(resolvedUri))

			return if(isTree)	{
				val uri = Uri.parse(resolvedUri)
				val treeId = DocumentsContract.getTreeDocumentId(uri)
				val docId = DocumentsContract.getDocumentId(uri)

				if(treeId == docId)	{
					null
				}else	{
					val relativePath = relativeUri.relativePath
					if(relativePath.isBlank() || !relativePath.contains("/"))	{
						val parentDocId = docId.substringBeforeLast('/')
						val treeUri = DocumentsContract.buildTreeDocumentUri(uri.authority,treeId)
						
						DocumentsContract
							.buildDocumentUriUsingTree(treeUri,parentDocId)
							.toString()
					}else	{
						super.getParentFile(relativePath)?.let	{
							if(isSafUri(path))	{
								combinePath(relativeUri.rootUri,it)
							}else	{
								it
							}
						}
					}
				}
			}else	{
				null
			}
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
		if(isSafContext(path))	{
			val relativeUri = relativePathFromUri(path)
			val resolvedUri = resolveRelativeUri(Uri.parse(relativeUri.rootUri),relativeUri.relativePath)
				?: return false

			return try	{
				DocumentsContract.deleteDocument(contentResolver,Uri.parse(resolvedUri))
			}catch(_: Exception)	{
				false
			}
		}
		return super.delete(path)
	}

	/**
	 * Renames a SAF document.
	 *
	 * @param src source file URI
	 * @param target new display name
	 * @return URI string of renamed document, or null if failed
	 */
	override fun rename(src: String, target: String): String?	{
		if(isSafContext(src))	{
			val tmpPath = tempPath(src) ?: return null

			val relativeUri = relativePathFromUri(tmpPath)
			val resolvedUri = resolveRelativeUri(
				Uri.parse(relativeUri.rootUri),
				relativeUri.relativePath
			) ?: return null
			
			return DocumentsContract
				.renameDocument(contentResolver,Uri.parse(resolvedUri),target)
				?.toString()
		}
		return super.rename(src,target)
	}

	/**
	 * Checks whether the given URI points to a file.
	 *
	 * @param path file URI string
	 * @return true if file
	 */
	override fun isFile(path: String): Boolean	{
		if(isSafContext(path))	{
			return exists(path) && !isDirectory(path)
		}
		return File(path).isFile
	}

	/**
	 * Checks whether the given URI points to a directory.
	 *
	 * @param path directory URI string
	 * @return true if directory
	 */
	override fun isDirectory(path: String): Boolean	{
		if(isSafContext(path))	{
			val relativeUri = relativePathFromUri(path)
			val resolvedUri = resolveRelativeUri(Uri.parse(relativeUri.rootUri),relativeUri.relativePath)
				?: return false

			val mime = DocumentsContract.Document.COLUMN_MIME_TYPE
			return getDocumentMetadata(Uri.parse(resolvedUri),listOf(mime))[mime] == 
				DocumentsContract.Document.MIME_TYPE_DIR
		}
		return File(path).isDirectory
	}

	/**
	 * Returns the last-modified timestamp of a file or directory.
	 *
	 * SAF timestamps are obtained from
	 * [DocumentsContract.Document.COLUMN_LAST_MODIFIED].
	 *
	 * @param path file or directory path.
	 * @return last-modified time in milliseconds since the Unix epoch, or `-1`
	 * if the value is unavailable.
	 */
	override fun lastModified(path: String): Long	{
		if(isSafContext(path))	{
			val relativeUri = relativePathFromUri(path)
			val resolvedUri = resolveRelativeUri(Uri.parse(relativeUri.rootUri),relativeUri.relativePath)
				?: return -1L
			
			val column = DocumentsContract.Document.COLUMN_LAST_MODIFIED
			return getDocumentMetadata(Uri.parse(resolvedUri),listOf(column))[column]
				?.toLong() ?: -1L
		}
		return File(path).lastModified()
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
		if(isSafContext(path))	{
			val relativeUri = relativePathFromUri(path)
			if(relativeUri.relativePath.isEmpty()) return path

			var currentUri = relativeUri.rootUri

			for(segment in relativeUri.relativePath.split("/"))	{
				if(segment == "." || segment.isBlank()) continue

				if(segment == "..")	{
					getParentFile(currentUri)?.let	{ parent ->
						currentUri = parent
					} ?: return ""
				}else	{
					currentUri = combinePath(currentUri,segment) ?: return ""
				}
			}

			return if(isRelative(path))	{
				var relativeUri = currentUri.substringBefore("||","")
				val relativePath = currentUri.substringAfter("||","")

				if(relativePath.isBlank())	{
					relativeUri
				}else	{
					relativePath
				}
			}else	{
				currentUri
			}
    }
    return super.resolvePath(path)
	}

	/**
	 * Returns the size of a SAF file in bytes.
	 *
	 * @param path file URI string
	 * @return file size or 0 if unavailable
	 */
	override fun size(path: String): Long	{
		if(isSafContext(path))	{
			val relativeUri = relativePathFromUri(path)
			val resolvedUri = resolveRelativeUri(Uri.parse(relativeUri.rootUri),relativeUri.relativePath)
				?: return 0L
				
			val sizeColumn = DocumentsContract.Document.COLUMN_SIZE
			return getDocumentMetadata(Uri.parse(resolvedUri),listOf(sizeColumn))[sizeColumn]
				?.toLong() ?: 0L
		}
		return super.size(path)
	}

	/**
	 * Returns the display name of a file or directory.
	 *
	 * For SAF resources, the name is obtained from
	 * [DocumentsContract.Document.COLUMN_DISPLAY_NAME]. For paths containing a
	 * relative SAF component, the final path component is used.
	 *
	 * For regular filesystem paths, the implementation returns the filesystem
	 * file name.
	 *
	 * @param path file, directory, SAF URI, or relative path.
	 * @return display name, or an empty string if the SAF resource cannot be
	 * resolved.
	 */
	override fun getName(path: String): String	{
		if(isSafContext(path))	{
			val relativeUri = relativePathFromUri(path)
			val resolvedUri = resolveRelativeUri(Uri.parse(relativeUri.rootUri),relativeUri.relativePath)
				?: return ""

			if(relativeUri.relativePath.isNotEmpty())	{
				return super.getName(relativeUri.relativePath)
			}
			
			val column = DocumentsContract.Document.COLUMN_DISPLAY_NAME
			return getDocumentMetadata(Uri.parse(resolvedUri),listOf(column))[column] ?: ""
		}
		return File(path).name
	}

	/**
	 * Returns the application's private files directory.
	 *
	 * This directory is used as the default destination for materialized SAF
	 * resources and is not directly accessible to other applications under
	 * normal Android storage rules.
	 *
	 * @return application's private files directory.
	 */
	fun getFilesDir(): File?	{
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
	fun materialize(path: String, outDir: String): String {
		if(isSafContext(path))	{
			val tmpPath = tempPath(path) ?: return path
			
	    val baseDir = getFilesDir()
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
    val name = getName(path)
    val ext = getExtension(name)

    var fileName =	"${name}_${System.currentTimeMillis()}"
    if(ext.isNotEmpty()) fileName = "$fileName.$ext"
    
    val outFile = File(outRoot, fileName)

    if (!outFile.exists()) {
    	outFile.createNewFile()
    	
      openSource(path)?.use { input ->
        outFile.outputStream().use { output ->
          input.transferTo(output.asSink())
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
        	
          openSource(child)?.use { input ->
            outFile.outputStream().use { output ->
              input.transferTo(output.asSink())
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
	 * The specified path is resolved relative to the application's private
	 * files directory and deleted recursively.
	 *
	 * @param path path of the materialized resource relative to the application's
	 * private files directory.
	 */
	fun clearMaterialized(path: String) {
    val baseDir = getFilesDir() ?: return
    File(baseDir, path).deleteRecursively()
	}
}

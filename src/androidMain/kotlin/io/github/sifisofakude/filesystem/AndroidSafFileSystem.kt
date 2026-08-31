package io.github.sifisofakude.filesystem

import android.net.Uri
import android.content.Context
import android.provider.DocumentsContract

import androidx.documentfile.provider.DocumentFile


import java.io.File
import java.io.OutputStream
import java.io.InputStream
import java.io.FileNotFoundException

import java.nio.file.Paths

import kotlin.io.normalize

import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered

/**
 * Android Storage Access Framework (SAF) implementation of [FileSystemUtil].
 *
 * This implementation extends [JvmFileSystem] to provide filesystem operations
 * for Android Storage Access Framework resources identified by `content://`
 * URIs.
 *
 * SAF resources are provided by Android [DocumentProvider] implementations and
 * may represent local storage, removable storage, cloud storage, or other
 * document providers available on the device.
 *
 * The implementation allows SAF resources and conventional filesystem paths
 * to be accessed through the same [FileSystemUtil] API. Operations are
 * automatically delegated to the appropriate backend based on the supplied
 * path and the currently selected SAF directory.
 *
 * ### Selected SAF directory
 *
 * A directory can be selected as the active SAF root using
 * [changeSelectedDirectory]. Once selected, relative paths are resolved
 * against that directory.
 *
 * For example:
 *
 * ```kotlin
 * fs.changeSelectedDirectory(treeUri)
 *
 * val file = FileOperation("project/src/Main.kt")
 * ```
 *
 * A path beginning with `content://` is always treated as an SAF resource,
 * while relative paths are resolved against the selected SAF directory when
 * one is configured.
 *
 * ### Cross-filesystem operations
 *
 * The implementation supports operations between conventional filesystem
 * paths and SAF resources where supported, allowing applications to copy or
 * move data between Android's traditional filesystem APIs and document
 * providers.
 *
 * ### Materialization
 *
 * Some libraries and APIs require a real filesystem path and cannot operate
 * directly on a `content://` URI. [materialize] can be used to copy an SAF
 * resource into the application's private files directory and obtain a
 * conventional filesystem path.
 *
 * Materialized resources can later be removed using [clearMaterialized].
 *
 * @param context Android context used to access document providers and SAF
 * resources.
 *
 * @see FileSystemUtil
 * @see JvmFileSystem
 * @see DocumentFile
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
		selectedParentUri = newParentUri?.let	{
			if(isDirectory(it.toString())) it
			else null
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
		return isSafUri(path) || selectedParentUri != null
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
	fun isRelative(path: String): Boolean	{
		if(isSafUri(path))	{
			val relativeUri = relativePathFromUri(path)
			if(relativeUri.relativePath.isEmpty())	{
				return false
			}
			return true
		}
		return !File(path).isAbsolute
	}

	/**
	 * Resolves a relative path against an SAF tree URI.
	 *
	 * Each path segment is traversed through the corresponding
	 * [DocumentFile] hierarchy starting at [rootTreeUri].
	 *
	 * For example, resolving `src/main.kt` against a selected tree causes
	 * `src` to be located first and `main.kt` to be located within that
	 * directory.
	 *
	 * @param rootTreeUri SAF tree URI used as the resolution root.
	 * @param relativePath path relative to [rootTreeUri].
	 * @return URI of the resolved document, or `null` if any path segment
	 * could not be resolved.
	 */
	fun resolveRelativeUri(rootTreeUri: Uri, relativePath: String): String? {
    var parent = DocumentFile.fromTreeUri(context,rootTreeUri) ?: return null

    relativePath
    	.split('/')
    	.filter { it.isNotEmpty() }
    	.forEach	{ file ->
    		parent = parent.findFile(file) ?: return null
    	}

    return parent.uri.toString()
	}

	/**
	 * Resolves an SAF URI into a tree root and a path relative to that root.
	 *
	 * The URI is traversed from the leaf toward its parent until a URI that can
	 * be resolved as an SAF tree is found. The returned [SafRelativePath]
	 * contains that tree URI together with the path from the tree root to the
	 * original document.
	 *
	 * This allows document URIs nested below a tree URI to be resolved using
	 * [DocumentFile] traversal rather than relying on the URI path itself as a
	 * filesystem path.
	 *
	 * @param uri SAF `content://` URI to resolve.
	 * @return the discovered SAF tree root and the relative path to [uri].
	 * If no tree root can be resolved, the original URI is returned as the root
	 * with an empty relative path.
	 */
	fun relativePathFromUri(uri: String): SafRelativePath	{
		val defaultResult = SafRelativePath(
			rootUri = uri,
			relativePath = ""
		)

		val relativeNames = mutableListOf<String>()
		var relativeName = uri.substringAfterLast('/',"")
		var relativeUri = uri

		while(true)	{
			if(relativeUri.isNotEmpty())	{
				DocumentFile.fromTreeUri(context,Uri.parse(relativeUri))?.let	{
					return SafRelativePath(
						rootUri = it.uri.toString(),
						relativePath = relativeNames.asReversed().joinToString("/")
					)
				}
			}

			if(relativeName.isEmpty()) break
			
			relativeNames.add(relativeName)
			
			relativeName = relativeUri.substringAfterLast('/',"")
			relativeUri = relativeUri.substringBeforeLast('/',"")
		}
		return defaultResult
	}

	/**
	 * Resolves a path to a [DocumentFile] when the path belongs to the current
	 * SAF context.
	 *
	 * The path may be an explicit `content://` URI or a relative path resolved
	 * against the directory selected by [changeSelectedDirectory].
	 *
	 * @param path SAF URI or relative path to resolve.
	 * @return resolved [DocumentFile], or `null` if the resource cannot be
	 * resolved.
	 */
	fun getDocumentFile(path: String): DocumentFile?	{
		if(isSafContext(path))	{
			val tmpPath = tempPath(path) ?: return null
			val tmpRelativeUri = relativePathFromUri(tmpPath)
			val resolvedUri = resolveRelativeUri(
				rootTreeUri = Uri.parse(tmpRelativeUri.rootUri),
				relativePath = tmpRelativeUri.relativePath
			) ?: return null

			return DocumentFile.fromTreeUri(context, Uri.parse(resolvedUri))
				?: DocumentFile.fromSingleUri(context, Uri.parse(resolvedUri))
		}
		return null
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
				"${it.toString()}/$path"
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
        	if(isSafContext(input))	{
        		var path: String? = tempPath(input)

        		path?.let	{
        			getDocumentFile(path)
        		}
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
	 * Moves an SAF document to another SAF directory.
	 *
	 * The source must resolve to an existing SAF document and the destination
	 * must identify the target SAF directory.
	 *
	 * @param src URI or path identifying the source document.
	 * @param dst URI identifying the destination directory.
	 * @return URI of the moved document, or `null` if the operation fails.
	 */
	override fun move(src: String, dst: String): String?	{
		val source = getDocumentFile(src) ?: return null
		val sourceParent = source.parentFile ?: return null
		val destination = getDocumentFile(dst) ?: return null

		if(!source.exists() || !destination.isDirectory)	{
			return null
		}

		return try	{
			DocumentsContract
				.moveDocument(
					context.contentResolver,
					source.uri,
					sourceParent.uri,
					destination.uri
				)?.toString()
		}catch(_: FileNotFoundException)	{
			null
		}
	}

	/**
	 * Copies an SAF document into a destination SAF directory.
	 *
	 * The destination must identify an existing SAF directory. When [overwrite]
	 * is `false`, the operation fails if a document with the same name already
	 * exists in the destination.
	 *
	 * The operation is performed using [DocumentsContract.copyDocument].
	 *
	 * @param src source SAF URI or path.
	 * @param dst destination SAF directory URI.
	 * @param overwrite whether an existing destination may be replaced.
	 * @return URI of the copied document, or `null` if the operation fails.
	 */
	override fun copy(src: String, dst: String, overwrite: Boolean): String?	{
		return getDocumentFile(src)?.let	{ source ->
			if(!isDirectory(dst))	{
				null
			}else	{
				val tmpName = getName(src)
				val tmpDst = combinePath(dst,tmpName)

				if(exists(tmpDst) && !overwrite)	{
					null
				}else	{
					try	{
						val tmpName = combinePath(dst,getName(src))
						if(exists(tmpName) && !overwrite)	{
							null
						}else	{
							DocumentsContract
								.copyDocument(
									contentResolver,
									source.uri,
									Uri.parse(dst)
								)
								?.toString()
						}
						
					}catch(_: FileNotFoundException)	{
						null
					}
				}
			}
		}
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
    if(isSafContext(directory))	{
    	val tmpDir = tempPath(directory) ?: return emptyList()
    	
	    val root = getDocumentFile(tmpDir) ?: return emptyList()

   		val results = mutableListOf<FileSource>()

	    walkSaf(root,"",extensions,results)
	    
    	return results.map { it.absolutePath }.toList()
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
		if(isSafContext(path))	{
			if(isSafUri(path))	{
				val relativeUri = relativePathFromUri(path)

				var uri = relativeUri.rootUri
				val segments = relativeUri.relativePath.split('/')
				for(segment in segments)	{
					getDocumentFile(uri)?.let	{ parent ->
						parent
							.findFile(segment)
							?.let	{
								if(it.isFile) return null

								uri = it.uri.toString()
							}

							?:

						parent
							.createDirectory(segment)
							?.let	{
								uri = it.uri.toString()
							}

							?:

						return null
					}
					return uri
				}
				return null
			}

			return createDirectory("${selectedParentUri.toString()}/$path")
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
		if(isSafContext(path))	{
			val fileName = getName(path)
	    val parents = path.substringBeforeLast('/',"")
	    var parentUri: String? = null
	    
			if(isSafUri(path))	{
				parentUri = parents
			}else if(selectedParentUri != null)	{
				parentUri = "${selectedParentUri.toString()}/$parents"
			}else	{
				return null
			}

    	return createDirectory(parentUri)?.let	{ parent ->
    		getDocumentFile(parent)
    			?.findFile(fileName)
    			?.let	{
    				if(it.isFile) it.uri.toString()
    				else null
    			}

    			?:

    		getDocumentFile(parent)
    			?.createFile("application/octet-stream",fileName)
    			?.uri?.toString()
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
			val tmpPath = tempPath(path) ?: return null
			val mode = if(append) "wa" else "w"
			
			contentResolver
				.openOutputStream(Uri.parse(tmpPath),mode)
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
			val tmpPath = tempPath(path) ?: return null
			contentResolver
				.openInputStream(Uri.parse(tmpPath))
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
			val tmpPath = tempPath(path) ?: return emptyList()
			
			val document = getDocumentFile(tmpPath)

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
		if(isSafContext(path))	{
			val tmpPath = tempPath(path) ?: return false
			
			return getDocumentFile(tmpPath)?.exists() ?: return false
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
			val tmpPath = tempPath(path) ?: return null
			val relativeUri = relativePathFromUri(tmpPath)

			if(relativeUri.relativePath.isNotEmpty())	{
				return super.getParentFile(relativeUri.relativePath)
			}
			
			return getDocumentFile(relativeUri.rootUri)?.parentFile?.uri?.toString()
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
			val tmpPath = tempPath(path) ?: return false

			return getDocumentFile(tmpPath)?.delete() ?: false
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
				.renameDocument(contentResolver,Uri.parse(resolvedUri
				),target)
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
			val tmpPath = tempPath(path) ?: return false
			getDocumentFile(tmpPath)?.let	{
				return it.isFile
			} ?: return false
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
			val tmpPath = tempPath(path) ?: return false
			
			getDocumentFile(tmpPath)?.let	{
				return it.isDirectory
			} ?: return false
		}
		return File(path).isDirectory
	}

	/**
	 * Returns last modified timestamp of a SAF file.
	 *
	 * @param path file URI string
	 * @return timestamp in millis, or -1 if unavailable
	 */
	override fun lastModified(path: String): Long	{
		if(isSafContext(path))	{
			val tmpPath = tempPath(path) ?: return -1
			
			getDocumentFile(tmpPath)?.let	{
				return it.lastModified()
			} ?: return -1
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
			if(isSafUri(path)) return path

			var parent = getDocumentFile(selectedParentUri.toString()) ?: return ""
			path.split('/').forEach	{ segment ->
				if(segment == "..")	{
					parent = parent.parentFile ?: return ""
				}else	{
					parent = parent.findFile(segment) ?: return ""
				}
			}
			return parent.uri.toString()
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
			val tmpPath = tempPath(path) ?: return 0L
			
			return getDocumentFile(tmpPath)
				?.length() ?: 0L
		}
		return super.size(path)
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
			val relativeUri = relativePathFromUri(path)

			if(relativeUri.relativePath.isNotEmpty())	{
				return super.getName(relativeUri.relativePath)
			}
			
			return getDocumentFile(relativeUri.rootUri)?.name ?: ""
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
	override fun materialize(path: String, outDir: String): String {
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
	override fun clearMaterialized(path: String) {
    val baseDir = getFilesDir() ?: return
    File(baseDir, path).deleteRecursively()
	}
}

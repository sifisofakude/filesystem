package io.github.sifisofakude.filesystem

import android.net.Uri
import android.util.Log
import android.content.Context
import android.provider.DocumentsContract

import java.io.File
import java.math.BigInteger

import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered

class AndroidSafFileSystem(context: Context) : JvmFileSystem()	{
	private val context = context.applicationContext
	private val contentResolver = context.contentResolver
	private val localStorage = "com.android.externalstorage.documents"
	private val unSupportedAuthorities = listOf(
		"com.android.providers.downloads.documents"
	)

	@Volatile
	private var selectedParentUri: Uri? = null

	companion object Permissions	{
		val CAN_READ = "can_read"
		val CAN_WRITE = "can_write"
	}

	fun changeSelectedDirectory(newParentUri: Uri?)	{
		newParentUri?.let	{ parent ->
			if(isTreeUri("$parent") && isDirectory("$parent"))	{
				selectedParentUri = parent
			}else	{
				selectedParentUri = null
			}
		}
	}

	override fun getCurrentDirectory(): String?	{
		return selectedParentUri?.toString()
	}

	fun isSafUri(path: String): Boolean = path.startsWith("content://")

	fun isSafContext(path: String): Boolean	{
		return if(isSafUri(path))	{
			true
		}else	{
			if(isRelative(path)) selectedParentUri != null
			else false
		}
	}

	override fun isRelative(path: String): Boolean	{
		return if(path.startsWith("content://") || path.startsWith("file://"))	{
			!path.substringAfter("||","").isBlank()
		}else	{
			!File(path).isAbsolute
		}
	}

	fun isTreeUri(uri: String): Boolean	= DocumentsContract.isTreeUri(Uri.parse(uri))

	fun isLocalStorage(authority: String): Boolean = authority == localStorage

	fun isSupported(authority: String): Boolean = authority !in unSupportedAuthorities

	fun resolveRelativeUri(uri: Uri, relativePath: String): String?	{
		val authority = uri.authority ?: return null
		
		if(!isSafUri(uri.toString()) || !isSupported(authority)) return null

		if(!isLocalStorage(authority))	{
			return resolveCloudUri(uri, relativePath)
		}
		
		val isTree = DocumentsContract.isTreeUri(uri)

		val treeUri = if(isTree)	{
			uri
		}else	{
			val docId = DocumentsContract.getDocumentId(uri)
			DocumentsContract.buildTreeDocumentUri(authority,docId)
		}

		val rootDocId = if(DocumentsContract.isDocumentUri(context,uri))	{
			DocumentsContract.getDocumentId(treeUri)
		}else	{
			DocumentsContract.getTreeDocumentId(uri)
		}

		val completeDocId = rootDocId + "/" + relativePath.trim('/')

		return DocumentsContract
			.buildDocumentUriUsingTree(treeUri,completeDocId.trim('/'))
			.toString()
	}

	fun resolveCloudUri(rootUri: Uri, relativePath: String): String?	{
		val authority = rootUri.authority ?: return null
		if(!isSafUri(rootUri.toString()) || isLocalStorage(authority) || !isSupported(authority)) return null


		val treeDocId = if(DocumentsContract.isDocumentUri(context,rootUri))	{
			DocumentsContract.getDocumentId(rootUri)
		}else	{
			DocumentsContract.getTreeDocumentId(rootUri)
		}

		val treeUri = DocumentsContract.buildTreeDocumentUri(authority,treeDocId)

		var currentUri = treeUri
		val metadata = listOf(
			DocumentsContract.Document.COLUMN_DISPLAY_NAME,
			DocumentsContract.Document.COLUMN_DOCUMENT_ID
		)
		
		for(segment in relativePath.split('/'))	{
			var segmentFound = false
			val tmpMetadata = getChildrenMetadata(currentUri,metadata)
			
			tmpMetadata.forEach	{
				val name = it[metadata[0]]
				if(name == segment)	{
					val docId = it[metadata[1]]
					currentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri,docId)
					segmentFound = true
					return@forEach
				}
			}

			if(!segmentFound) return null
		}
		return currentUri.toString()
	}

	fun getDocumentMetadata(rootUri: Uri,metadata: List<String>): Map<String,String>	{
		return getMetadata(rootUri,metadata).getOrNull(0) ?: emptyMap()
	}

	fun getChildrenMetadata(rootUri: Uri, metadata: List<String>): List<Map<String, String>> {
	  val childrenUri = try {
	    if (DocumentsContract.isDocumentUri(context,rootUri)) {
	      val docId = DocumentsContract.getDocumentId(rootUri)
	      DocumentsContract.buildChildDocumentsUri(rootUri.authority, docId)
	    } else {
	      val docId = DocumentsContract.getTreeDocumentId(rootUri)
	      DocumentsContract.buildChildDocumentsUriUsingTree(rootUri, docId)
	    }
	  } catch (e: Exception) {
	    Log.w("MetadataEngine", "Provided URI cannot be resolved to a directory hierarchy: $rootUri")
	    return emptyList()
	  }

		return getMetadata(childrenUri, metadata)
	}

	fun getMetadata(uri: Uri, metadata: List<String>): List<Map<String, String>>	{
    val resultsList = mutableListOf<Map<String, String>>()
	  val requiresFlags = metadata.contains(Permissions.CAN_READ) || metadata.contains(Permissions.CAN_WRITE)
	  val projectionList = metadata.filter { it != Permissions.CAN_WRITE && it != Permissions.CAN_READ }.toMutableList()
	  
	  if (requiresFlags && !projectionList.contains(DocumentsContract.Document.COLUMN_FLAGS)) {
    	projectionList.add(DocumentsContract.Document.COLUMN_FLAGS)
	  }
	  
	  val projection = projectionList.toTypedArray()

	  try {
	    context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
	      while (cursor.moveToNext()) {
	        val childMetadata = mutableMapOf<String, String>()
	        
	        if (metadata.contains(Permissions.CAN_READ)) {
          	childMetadata[Permissions.CAN_READ] = "true"
	        }

	        projection.forEach { columnName ->
	          val index = cursor.getColumnIndex(columnName)
	          if (index > -1 && !cursor.isNull(index)) {
	            if (columnName == DocumentsContract.Document.COLUMN_FLAGS) {
	              val flags = cursor.getInt(index)
	              if (metadata.contains(Permissions.CAN_WRITE)) {
	                val supportsWrite = (flags and DocumentsContract.Document.FLAG_SUPPORTS_WRITE) != 0
	                childMetadata[Permissions.CAN_WRITE] = supportsWrite.toString()
	              }
	              if (metadata.contains(DocumentsContract.Document.COLUMN_FLAGS)) {
                	childMetadata[columnName] = flags.toString()
	              }
	            } else {
	              childMetadata[columnName] = cursor.getString(index) ?: ""
	            }
	          }
	        }
       		resultsList.add(childMetadata)
	      }
	    }
	  } catch (e: Exception) {
    	android.util.Log.w("MetadataEngine", "Failed to query children for authority: ${uri.authority}", e)
    	// throw e
	  }
	  return resultsList
	}

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

	fun getDocumentId(uri: Uri): String?	{
		return if(isTreeUri(uri.toString()))	{
			DocumentsContract.getTreeDocumentId(uri)
		}else	{
			DocumentsContract.getDocumentId(uri)
		}
	}

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
			return if(parent.contains("||"))	{
				"${parent.trim('/')}/${child.trim('/')}"
			}else {
				"$parent||${child.trim('/')}"
			}
		}
		return super.combinePath(parent,child)
	}

	override fun canRead(path: String): Boolean	{
		if(isSafContext(path))	{
			if(!exists(path)) return false
			
			val tmpPath = tempPath(path) ?: return false
			val relativePath = relativePathFromUri(tmpPath)
			val resolvedUri = resolveRelativeUri(Uri.parse(relativePath.rootUri),relativePath.relativePath)
				?: return false
			
			val metadata = Permissions.CAN_READ
			return getDocumentMetadata(
				Uri.parse(resolvedUri),listOf(metadata)
			)[metadata]?.toBoolean() ?: false
		}
		return super.canRead(path)
	}

	override fun canWrite(path: String): Boolean	{
		if(isSafContext(path))	{
			if(!exists(path)) return false
			
			val tmpPath = tempPath(path) ?: return false
			val relativePath = relativePathFromUri(tmpPath)
			val resolvedUri = resolveRelativeUri(Uri.parse(relativePath.rootUri),relativePath.relativePath)
				?: return false
			
			val metadata = Permissions.CAN_WRITE
			return getDocumentMetadata(
				Uri.parse(resolvedUri),listOf(metadata)
			)[metadata]?.toBoolean() ?: false
		}
		return super.canWrite(path)
	}

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

	override fun move(src: String, dst: String): String?	{
		return moveByStream(src,dst)
	}

	override fun copy(src: String, dst: String, overwrite: Boolean): String?	{
		return copyByStream(src,dst,overwrite)
	}
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
				combinePath("$rootUri",relativeParent)
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

	override fun listFiles(path: String): List<String>	{
		if(isSafContext(path))	{
			val relativeUri = relativePathFromUri(path)
			val resolvedUri = resolveRelativeUri(Uri.parse(relativeUri.rootUri),relativeUri.relativePath)
				?: return emptyList()


			val metadata = DocumentsContract.Document.COLUMN_DISPLAY_NAME
			val children = getChildrenMetadata(Uri.parse(resolvedUri),listOf(metadata))

			throw IllegalStateException("$resolvedUri")
			
			return getChildrenMetadata(Uri.parse(resolvedUri),listOf(metadata))
				.map { "${it[metadata]}" }
		}
		return super.listFiles(path)
	}

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

	override fun getParentFile(path: String): String?	{
		if(isSafContext(path))	{
			val relativeUri = relativePathFromUri(path)
			val resolvedUri = resolveRelativeUri(Uri.parse(relativeUri.rootUri),relativeUri.relativePath)
				?: return null

			val authority = Uri.parse(resolvedUri).authority ?: return null
			if(!isLocalStorage(authority)) return null

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

	override fun delete(path: String): Boolean	{
		if(isSafContext(path))	{
			val relativeUri = relativePathFromUri(path)
			val resolvedUri = resolveRelativeUri(Uri.parse(relativeUri.rootUri),relativeUri.relativePath)
				?: return false

			return try	{
				DocumentsContract.deleteDocument(contentResolver,Uri.parse(resolvedUri))
			}catch(_: Exception)	{
				Log.w("AndroidSafFileSystem","Delete operation unsupported $resolvedUri")
				false
			}
		}
		return super.delete(path)
	}
	
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

	override fun isFile(path: String): Boolean	{
		if(isSafContext(path))	{
			return exists(path) && !isDirectory(path)
		}
		return File(path).isFile
	}

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

	fun getFilesDir(): File?	{
		return context.getFilesDir()
	}

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

	fun clearMaterialized(path: String) {
    val baseDir = getFilesDir() ?: return
    File(baseDir, path).deleteRecursively()
	}
}

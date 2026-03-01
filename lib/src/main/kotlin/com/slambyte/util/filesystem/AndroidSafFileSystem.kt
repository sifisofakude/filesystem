package com.slambyte.util.filesystem

import android.net.Uri
import android.content.Context
import android.provider.DocumentsContract

import androidx.documentfile.provider.DocumentFile

// import java.util.Set
import java.util.Stack

import java.io.File
import java.io.OutputStream

class AndroidSafFileSystem(context: Context) : FileSystemUtil	{
	private val context = context
	private var selectedParentUri: Uri? = null
	private val contentResolver = context.contentResolver

	fun changeSelectedDirectory(newParentUri: Uri)	{
		selectedParentUri = newParentUri
	}

	override fun getCurrentDirectory(): String?	{
		return selectedParentUri?.toString()
	}

	override fun resolveFiles(inputFiles: List<Any>,extensions: Set<String>): List<FileSource>	{
		val results = mutableListOf<FileSource>()
		
		inputFiles.forEach	{ file ->
			var doc: DocumentFile? = null

			if(file is DocumentFile)	{
				doc = file
			}

			if(file is Uri)	{
				doc = DocumentFile.fromTreeUri(context,file)
			}

			if(file is String)	{
				doc = DocumentFile.fromTreeUri(context,Uri.parse(file))
			}

			if(doc != null)	{
				if(doc.exists() && doc.isFile)	{
					val inputStream = contentResolver.openInputStream(doc.getUri())

					results.add(
						FileSource(
							stream = inputStream,
							relativePath = doc.getName()
						)
					)
				}else if(doc.exists() && doc.isDirectory)	{
					findFiles(doc.getUri().toString(),extensions).forEach	{
						val fileUri = Uri.parse(it)
						val filePath = fileUri.getPath()
						val rootPath = doc.getUri().getPath()

						val relativePath = filePath?.replace("$rootPath/","")

						val inputStream = contentResolver.openInputStream(fileUri)

						results.add(
							FileSource(
								stream = inputStream,
								relativePath = relativePath
							)
						)
					}
				}
			}
		}
		return results
	}

	override fun findFiles(directory: String, extensions: Set<String>): List<String>	{
		val results = mutableListOf<String>()

		val dirStack = Stack<String>()
		val treeUri = Uri.parse(directory)

		val rootId = DocumentsContract.getTreeDocumentId(treeUri)
		dirStack.push(rootId)

		val projection = arrayOf(
		  DocumentsContract.Document.COLUMN_DOCUMENT_ID,
		  DocumentsContract.Document.COLUMN_MIME_TYPE,
		  DocumentsContract.Document.COLUMN_DISPLAY_NAME
		)

		val contentResolver = context.contentResolver

		while(dirStack.isNotEmpty())	{
			val currentDirId = dirStack.pop()
			val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri,currentDirId)

			contentResolver.query(childrenUri,projection,null,null,null)?.use	{ cursor ->
				val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
				val mimeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
				val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)

				while(cursor.moveToNext())	{
					val docId = cursor.getString(idIndex)
					val mime = cursor.getString(mimeIndex)
					val name = cursor.getString(nameIndex)

					if(mime == DocumentsContract.Document.MIME_TYPE_DIR)	{
						dirStack.push(docId)
					}else	{
						val ext = name.substringAfterLast('.',"").lowercase()
						if(extensions.isEmpty() || extensions.contains(ext))	{
							val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri,docId)
							results.add(fileUri.toString())
						}
					}
				}
			}
		}
		return results
	}

	override fun createDirectory(path: String): String?	{
		var currentParentUri = selectedParentUri ?: return null
		var currentParent: DocumentFile? = DocumentFile.fromTreeUri(context,currentParentUri)

		val segments = path.split('/')
		segments.forEach	{ segment ->
			currentParent = currentParent?.findFile(segment)

			if(currentParent == null)	{
				val mimeType = DocumentsContract.Document.MIME_TYPE_DIR

				val newUri = DocumentsContract.createDocument(
					context.contentResolver,
					currentParentUri,
					mimeType,segment
				) ?: return null

				currentParentUri = newUri
				currentParent = DocumentFile.fromTreeUri(context,newUri)
			}else if(currentParent.isDirectory)	{
				currentParentUri = currentParent.getUri()
			}
		}
		
		return currentParentUri.toString()
	}

	override fun openOutputStream(path: String): OutputStream?	{
		val uri = Uri.parse(path)

		var file = DocumentFile.fromSingleUri(context,uri) ?: return null

		return context.getContentResolver().openOutputStream(uri)
	}
}

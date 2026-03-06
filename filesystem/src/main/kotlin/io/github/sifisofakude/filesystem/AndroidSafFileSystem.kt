package io.github.sifisofakude.filesystem

import android.net.Uri
import android.content.Context
import android.provider.DocumentsContract

import androidx.documentfile.provider.DocumentFile

import java.util.Stack

import java.io.File
import java.io.OutputStream

/**
 * Android Storage Access Framework (SAF) implementation of [FileSystemUtil].
 *
 * <p>
 * **Important:** This class is **Android-only**. It depends on:
 * <ul>
 *     <li>The Android SDK (`android.content.Context`, `android.net.Uri`)</li>
 *     <li>`androidx.documentfile:documentfile` library</li>
 * </ul>
 * It will **not work on JVM/desktop environments**.
 * </p>
 *
 * <p>
 * To use this class, your app must include the DocumentFile library dependency:
 * ```gradle
 * dependencies {
 *     implementation("androidx.documentfile:documentfile:1.0.1")
 * }
 * ```
 * </p>
 *
 * <p>
 * Provides access to files and directories using Android SAF. Requires the user
 * to grant access to the desired directories via SAF.
 * </p>
 *
 * Features:
 * <ul>
 *     <li>Maintain a selected root directory via [changeSelectedDirectory]</li>
 *     <li>Resolve files into readable [FileSource] streams</li>
 *     <li>Recursive file discovery with extension filtering</li>
 *     <li>Directory creation inside SAF-accessible locations</li>
 *     <li>Opening output streams for writing files</li>
 * </ul>
 *
 * Permissions:
 * <ul>
 *     <li>The app must have access to the chosen SAF directory</li>
 *     <li>Files that cannot be opened due to revoked permissions will be skipped</li>
 * </ul>
 *
 * Example usage:
 * ```kotlin
 * val safFs = AndroidSafFileSystem(context)
 * safFs.changeSelectedDirectory(userSelectedUri)
 * val files = safFs.resolveFiles(listOf(userSelectedUri), setOf("txt"))
 * ```
 *
 * @param context Android context used for content resolution
 * @since 0.1.0
 */
class AndroidSafFileSystem(context: Context) : FileSystemUtil	{
	private val context = context
	private var selectedParentUri: Uri? = null
	private val contentResolver = context.contentResolver

	/**
   * Changes the currently selected parent directory.
   *
   * All directory operations (creation, relative resolution) will be
   * based on this selected directory.
   *
   * @param newParentUri the URI of the new parent directory
   */
	fun changeSelectedDirectory(newParentUri: Uri)	{
		selectedParentUri = newParentUri
	}

	/**
   * Returns the currently selected directory URI as a string.
   *
   * @return the URI string of the selected directory, or null if none selected
   */
	override fun getCurrentDirectory(): String?	{
		return selectedParentUri?.toString()
	}

	/**
   * Resolves a collection of inputs into readable [FileSource] objects.
   *
   * Supported input types include:
   * - [DocumentFile]
   * - [Uri]
   * - String representations of URIs
   *
   * If a directory is provided, all files matching the given extensions
   * are recursively discovered. Files that cannot be opened due to revoked
   * permissions or I/O errors are skipped.
   *
   * @param inputFiles files, URIs, or paths to resolve
   * @param extensions allowed file extensions (empty set means all)
   * @return a list of readable [FileSource] objects
   */
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

					if(inputStream != null)	{
						results.add(
							FileSource(
								stream = inputStream!!,
								relativePath = doc.getName()!!
							)
						)
					}
				}else if(doc.exists() && doc.isDirectory)	{
					findFiles(doc.getUri().toString(),extensions).forEach	{
						val fileUri = Uri.parse(it)
						val filePath = fileUri.getPath()
						val rootPath = doc.getUri().getPath()

						val relativePath = filePath!!.replace("$rootPath/","")

						val inputStream = contentResolver.openInputStream(fileUri)
						if(inputStream != null)	{
							results.add(
								FileSource(
									stream = inputStream!!,
									relativePath = relativePath
								)
							)
						}
					}
				}
			}
		}
		return results
	}

	/**
   * Recursively finds files inside a SAF directory.
   *
   * Only files matching the specified extensions are returned.
   *
   * @param directory URI string of the directory to search
   * @param extensions allowed file extensions (empty set means all)
   * @return list of URI strings for discovered files
   */
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

	/**
   * Creates a directory in the currently selected SAF parent.
   *
   * @param path relative path segments separated by '/'
   * @return URI string of the created directory, or null if creation failed
   */
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

	/**
   * Opens an [OutputStream] for writing to a file at the given URI.
   *
   * @param path URI string of the target file
   * @return output stream, or null if the file cannot be opened
   */
	override fun openOutputStream(path: String): OutputStream?	{
		val uri = Uri.parse(path)

		var file = DocumentFile.fromSingleUri(context,uri) ?: return null

		return context.getContentResolver().openOutputStream(uri)
	}
}

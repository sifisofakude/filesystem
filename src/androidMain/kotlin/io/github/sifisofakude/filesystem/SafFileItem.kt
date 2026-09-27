package io.github.sifisofakude.filesystem

import android.net.Uri

data class SafFileItem(
	val name: String,
	val mimeType: String,
	val size: Long,
	val uri: Uri
)

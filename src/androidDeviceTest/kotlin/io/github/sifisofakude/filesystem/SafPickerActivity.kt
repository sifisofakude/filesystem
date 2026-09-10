package io.github.sifisofakude.filesystem

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.net.Uri

class SafPickerActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        }

        startActivityForResult(intent, REQUEST_CODE)
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE) {
            resultUri = data?.data
            SafPickerActivity.resultCode = resultCode
            finish()
        }
    }

    companion object {
        var resultUri: Uri? = null
        var resultCode: Int = RESULT_CANCELED

        private const val REQUEST_CODE = 100
    }
}

package com.the412banner.bfe

import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import com.the412banner.bfe.unpack.UnpackService

/**
 * "Extract to ./<Archive name>/" in other apps' Open with sheet (the `QuickExtract` alias). Shows no UI:
 * the file is handed to [UnpackService], which queues it, extracts it with default settings next to the
 * archive, and reports progress (Pause / Cancel) and the result in notifications. With BFE's
 * notifications turned off there would be nothing to see, so it falls back to the Unpack screen,
 * auto-starting there instead.
 */
class QuickExtractActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri: Uri? = intent.data ?: (intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)
        if (uri != null) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if (nm.areNotificationsEnabled()) {
                val queued = UnpackService.isBusy()
                UnpackService.enqueue(this, uri)
                Toast.makeText(
                    applicationContext,
                    if (queued) "Added to BFE's extraction queue" else "Extracting in the background — see notifications",
                    Toast.LENGTH_SHORT,
                ).show()
            } else {
                startActivity(
                    Intent(this, UnpackArchiveActivity::class.java)
                        .setData(uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        .putExtra(UnpackArchiveActivity.EXTRA_AUTO_START, true),
                )
            }
        }
        finish()
    }
}

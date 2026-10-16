package com.the412banner.bfe.cloud

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Target of the "Return to BFE" link (`bfe-auth://done`) on the page rclone shows after a browser
 * sign-in (see [CloudSetup]). It only brings BFE's existing task back to the front, exactly like
 * tapping the launcher icon, and closes itself; the sign-in itself already reached rclone.
 */
class AuthReturnActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        packageManager.getLaunchIntentForPackage(packageName)?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            runCatching { startActivity(it) }
        }
        finish()
    }
}

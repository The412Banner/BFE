package com.the412banner.bfe.security

import android.content.Context
import android.util.Log
import com.the412banner.bfe.apk.SigningKeys
import com.the412banner.bfe.cloud.RcloneDaemon
import com.the412banner.bfe.server.ServerPrefs

/**
 * Moves the secrets BFE 1.1.0 and older kept in plain text into [SecretVault], in the background at
 * every launch. Each step does nothing once done and finishes what an interrupted launch left (the
 * same moves also happen on first use, so the order of events doesn't matter):
 *  - opens the vault key, so a lost key is noticed — and the user told — right away;
 *  - the "Access from network" password;
 *  - imported / generated signing keys' passwords;
 *  - rclone.conf, encrypted in place by rclone ([RcloneDaemon.secureAtRest]).
 */
object CredentialMigration {
    private const val TAG = "BFE_VAULT"

    fun start(ctx: Context) {
        val app = ctx.applicationContext
        Thread {
            step("vault key") { SecretVault.warmUp(app) }
            step("server password") { ServerPrefs.migratePassword(app) }
            step("signing keys") { SigningKeys.migrateToVault(app) }
            RcloneDaemon.secureAtRest(app)
        }.apply { name = "bfe-vault"; isDaemon = true; start() }
    }

    private inline fun step(what: String, block: () -> Unit) {
        runCatching(block).onFailure { Log.w(TAG, "Not done yet ($what): ${it.message}") }
    }
}

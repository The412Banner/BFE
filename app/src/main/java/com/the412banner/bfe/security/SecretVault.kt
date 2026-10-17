package com.the412banner.bfe.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.InvalidKeyException
import java.security.KeyStore
import java.security.ProviderException
import java.security.UnrecoverableKeyException
import javax.crypto.AEADBadTagException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory

/** Where the vault key lives, as the keystore reports it. */
enum class VaultProtection {
    /** A separate tamper-resistant security chip. */
    STRONGBOX,
    /** The processor's Trusted Execution Environment. */
    TEE,
    /** Secure hardware the keystore doesn't name. */
    SECURE_HARDWARE,
    /** No secure hardware on this device: Android's keystore protects the key in software only. */
    SOFTWARE,
    /** The keystore didn't say. */
    UNKNOWN,
}

/** The keystore couldn't be used just now. Nothing was cleared, so trying again later is safe. */
class VaultUnavailableException(cause: Throwable) :
    IOException("The credential vault isn't available right now (${cause.javaClass.simpleName})", cause)

/**
 * BFE's credential vault. Every secret BFE keeps — the rclone config password (which in turn encrypts
 * every cloud sign-in), the Wi-Fi server password, imported signing keys' passwords — is sealed with
 * one AES-256-GCM key held by the Android Keystore: in StrongBox (a separate security chip) where the
 * device has one, otherwise in the TEE. The key never leaves that hardware; BFE can only ask it to
 * encrypt or decrypt. It needs no unlock prompt, because the file server and rclone also start in
 * the background.
 *
 * Entries live in their own prefs file ([PREFS]) in [VaultCodec]'s format (the entry name is bound in
 * as associated data). Opened values are kept in memory for the life of the process.
 *
 * When the key is gone or can't be used any more (keystore wiped, app data restored onto another
 * device, key invalidated), what it sealed can never be opened again: those entries are cleared,
 * their names collected in [lost] so the user is told once, and a new key is made. A keystore that
 * just fails right now throws [VaultUnavailableException] and clears nothing. Thread-safe; blocking.
 */
object SecretVault {
    private const val TAG = "BFE_VAULT"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "bfe.vault.v1"
    private const val PREFS = "bfe_vault"
    private const val ENTRY = "entry/"
    private const val PREF_CREATED_AS = "key_created_as"
    private const val PREF_LOST = "lost"

    private val lock = Any()
    // The lost list has its own lock: the UI reads it while the vault may be busy making a key.
    private val lostLock = Any()
    private var key: SecretKey? = null
    private var protection: VaultProtection? = null
    private val opened = HashMap<String, String>()
    private val _lost = MutableStateFlow<Set<String>>(emptySet())
    @Volatile private var lostLoaded = false

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The secret stored as [name], or null when there is none (or it could no longer be opened and was cleared). */
    fun get(ctx: Context, name: String): String? {
        synchronized(lock) {
            opened[name]?.let { return it }
            if (prefs(ctx).getString(ENTRY + name, null) == null) return null
            val k = keyLocked(ctx)
            // Read again: opening the key may have found it gone and cleared every entry.
            val blob = prefs(ctx).getString(ENTRY + name, null) ?: return null
            return try {
                VaultCodec.open(k, name, blob).also { opened[name] = it }
            } catch (e: AEADBadTagException) {
                Log.w(TAG, "A vault entry no longer opens with the vault key; clearing it")
                discardLocked(ctx, listOf(name))
                null
            } catch (e: InvalidKeyException) {
                resetLocked(ctx, e)
                null
            } catch (e: GeneralSecurityException) {
                throw VaultUnavailableException(e)
            } catch (e: ProviderException) {
                throw VaultUnavailableException(e)
            }
        }
    }

    /** Seals [value] as [name], replacing what was there. On disk (committed) when this returns. */
    fun put(ctx: Context, name: String, value: String) {
        synchronized(lock) {
            if (opened[name] == value && prefs(ctx).contains(ENTRY + name)) return
            val blob = try {
                sealLocked(ctx, name, value)
            } catch (e: InvalidKeyException) {
                // The key can't be used any more (what it sealed is unreadable anyway): start over.
                resetLocked(ctx, e)
                try { sealLocked(ctx, name, value) } catch (e2: GeneralSecurityException) { throw VaultUnavailableException(e2) }
            }
            // commit, not apply: a password has to be on disk before anything is encrypted with it.
            if (!prefs(ctx).edit().putString(ENTRY + name, blob).commit()) throw IOException("Couldn't save to the credential vault")
            opened[name] = value
        }
    }

    fun remove(ctx: Context, name: String) {
        synchronized(lock) {
            opened.remove(name)
            prefs(ctx).edit().remove(ENTRY + name).commit()
        }
    }

    /** Opens the vault key (making one if there's none), so a lost key is noticed at launch. */
    fun warmUp(ctx: Context) {
        synchronized(lock) { keyLocked(ctx) }
    }

    /** Where the vault key lives (making the key first if there isn't one yet). */
    fun protection(ctx: Context): VaultProtection = synchronized(lock) {
        protection ?: protectionOf(keyLocked(ctx), prefs(ctx).getString(PREF_CREATED_AS, null) == VaultProtection.STRONGBOX.name)
            .also { protection = it }
    }

    /** Names of entries that were cleared because they could no longer be opened, until [acknowledgeLost]. */
    fun lost(ctx: Context): StateFlow<Set<String>> {
        if (!lostLoaded) synchronized(lostLock) { loadLostLocked(ctx) }
        return _lost
    }

    /** Records [name] as lost: for secrets kept outside the vault but locked with one of its entries. */
    fun markLost(ctx: Context, name: String) {
        addLost(ctx, listOf(name))
    }

    fun acknowledgeLost(ctx: Context) {
        synchronized(lostLock) {
            prefs(ctx).edit().remove(PREF_LOST).commit()
            _lost.value = emptySet()
            lostLoaded = true
        }
    }

    // ── internals ──

    private fun sealLocked(ctx: Context, name: String, value: String): String = try {
        VaultCodec.seal(keyLocked(ctx), name, value)
    } catch (e: InvalidKeyException) {
        throw e
    } catch (e: GeneralSecurityException) {
        throw VaultUnavailableException(e)
    } catch (e: ProviderException) {
        throw VaultUnavailableException(e)
    }

    private fun keyLocked(ctx: Context): SecretKey {
        key?.let { return it }
        val existing = try {
            val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            try {
                ks.getKey(ALIAS, null) as? SecretKey
            } catch (e: UnrecoverableKeyException) {
                Log.w(TAG, "The vault key can't be used any more (${e.javaClass.simpleName}); making a new one")
                runCatching { ks.deleteEntry(ALIAS) }
                null
            }
        } catch (e: GeneralSecurityException) {
            throw VaultUnavailableException(e)
        } catch (e: ProviderException) {
            throw VaultUnavailableException(e)
        } catch (e: IOException) {
            throw VaultUnavailableException(e)
        }
        if (existing != null) {
            key = existing
            return existing
        }
        // No key: anything still sealed was sealed by a key that no longer exists (keystore wiped, or
        // this data was restored onto another device), so it can never be opened again.
        val stale = entryNames(ctx)
        if (stale.isNotEmpty()) Log.w(TAG, "The vault key is missing; clearing ${stale.size} entries it sealed")
        discardLocked(ctx, stale)
        return createKeyLocked(ctx).also { key = it }
    }

    private fun createKeyLocked(ctx: Context): SecretKey {
        val strong = if (Build.VERSION.SDK_INT >= 28) generateStrongBox() else null
        val k = strong ?: try {
            generate(spec(strongBox = false))
        } catch (e: GeneralSecurityException) {
            throw VaultUnavailableException(e)
        } catch (e: ProviderException) {
            throw VaultUnavailableException(e)
        }
        val level = protectionOf(k, strong != null)
        // Recorded either way, so a key that ended up software-only is never a silent fallback.
        prefs(ctx).edit().putString(PREF_CREATED_AS, level.name).commit()
        protection = level
        if (level == VaultProtection.SOFTWARE || level == VaultProtection.UNKNOWN) {
            Log.w(TAG, "The vault key is NOT in secure hardware on this device ($level)")
        } else {
            Log.i(TAG, "Vault key created ($level)")
        }
        return k
    }

    @RequiresApi(28)
    private fun generateStrongBox(): SecretKey? = try {
        generate(spec(strongBox = true)).also { k ->
            // A chip that makes the key but then refuses AES-GCM with associated data must not leave
            // the vault unusable: prove one round trip before keeping it.
            check(VaultCodec.open(k, ALIAS, VaultCodec.seal(k, ALIAS, "ok")) == "ok")
        }
    } catch (e: StrongBoxUnavailableException) {
        Log.i(TAG, "No StrongBox on this device; the vault key goes in the TEE")
        null
    } catch (e: Exception) {
        // Some StrongBox chips refuse a 256-bit AES key or fail outright: the TEE is next best.
        Log.w(TAG, "StrongBox key generation failed (${e.javaClass.simpleName}); the vault key goes in the TEE")
        runCatching { KeyStore.getInstance(KEYSTORE).apply { load(null) }.deleteEntry(ALIAS) }
        null
    }

    private fun spec(strongBox: Boolean): KeyGenParameterSpec =
        KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            // The keystore picks every IV, so none is ever reused under this key.
            .setRandomizedEncryptionRequired(true)
            .setUserAuthenticationRequired(false)
            .apply { if (strongBox && Build.VERSION.SDK_INT >= 28) setIsStrongBoxBacked(true) }
            .build()

    private fun generate(spec: KeyGenParameterSpec): SecretKey =
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(spec)
            generateKey()
        }

    /** API 31+ reports the security level itself; before that only "secure hardware or not", plus what was asked for. */
    private fun protectionOf(k: SecretKey, strongBox: Boolean): VaultProtection {
        val info = try {
            SecretKeyFactory.getInstance(k.algorithm, KEYSTORE).getKeySpec(k, KeyInfo::class.java) as KeyInfo
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't read the vault key's info (${e.javaClass.simpleName})")
            return VaultProtection.UNKNOWN
        }
        if (Build.VERSION.SDK_INT >= 31) {
            return when (info.securityLevel) {
                KeyProperties.SECURITY_LEVEL_STRONGBOX -> VaultProtection.STRONGBOX
                KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> VaultProtection.TEE
                KeyProperties.SECURITY_LEVEL_UNKNOWN_SECURE -> VaultProtection.SECURE_HARDWARE
                KeyProperties.SECURITY_LEVEL_SOFTWARE -> VaultProtection.SOFTWARE
                else -> VaultProtection.UNKNOWN
            }
        }
        @Suppress("DEPRECATION")
        val secure = info.isInsideSecureHardware
        return when {
            !secure -> VaultProtection.SOFTWARE
            strongBox -> VaultProtection.STRONGBOX
            else -> VaultProtection.TEE
        }
    }

    private fun resetLocked(ctx: Context, cause: Exception) {
        Log.w(TAG, "The vault key was invalidated (${cause.javaClass.simpleName}); clearing what it sealed and making a new one")
        key = null
        protection = null
        runCatching { KeyStore.getInstance(KEYSTORE).apply { load(null) }.deleteEntry(ALIAS) }
        discardLocked(ctx, entryNames(ctx))
    }

    private fun entryNames(ctx: Context): List<String> =
        prefs(ctx).all.keys.filter { it.startsWith(ENTRY) }.map { it.removePrefix(ENTRY) }

    private fun discardLocked(ctx: Context, names: Collection<String>) {
        names.forEach { opened.remove(it) }
        if (names.isEmpty()) return
        val e = prefs(ctx).edit()
        names.forEach { e.remove(ENTRY + it) }
        e.commit()
        addLost(ctx, names)
    }

    private fun loadLostLocked(ctx: Context) {
        if (lostLoaded) return
        _lost.value = prefs(ctx).getStringSet(PREF_LOST, null)?.toSet().orEmpty()
        lostLoaded = true
    }

    private fun addLost(ctx: Context, names: Collection<String>) {
        synchronized(lostLock) {
            loadLostLocked(ctx)
            val all = _lost.value + names
            prefs(ctx).edit().putStringSet(PREF_LOST, all).commit()
            _lost.value = all
        }
    }
}

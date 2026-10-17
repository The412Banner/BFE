package com.the412banner.bfe.apk

import android.content.Context
import android.util.Log
import com.the412banner.bfe.security.SecretVault
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Date

/**
 * Signing-key management for the APK cloner/signer.
 *
 * - The built-in **BFE Test Key**: an RSA-2048 self-signed certificate minted ONCE on first use
 *   (BouncyCastle bcpkix builds the X.509; the JCA does the RSA — no provider registration) and
 *   persisted as PKCS12 in app-private storage, so every clone/edit/sign shares one signature and
 *   can be updated over the top later. The store/key password is fixed ("bfetest"); the file never
 *   leaves `filesDir/keys/`.
 * - Imported keystores: PKCS12 (.p12/.pfx — the format to convert to), BKS, and JKS where the
 *   platform's JCA happens to offer it (Android's does not — the import then says so). The file is
 *   copied into `filesDir/keys/` and remembered with its alias in a private prefs file; its store and
 *   key passwords are sealed in the credential vault ([SecretVault], one entry per keystore file).
 *   BFE 1.1.0 and older kept them in that prefs file in plain text — [migrateToVault] moves them.
 * - Generated keys: same as the built-in path, with the user's CN / organisation / validity.
 */
object SigningKeys {
    private const val TAG = "SigningKeys"
    const val BUILT_IN_NAME = "BFE Test Key"
    private const val BUILT_IN_ALIAS = "bfetest"
    private const val BUILT_IN_PASS = "bfetest"
    private const val PREFS = "signing_keys"
    private const val VAULT_PREFIX = "apk.key/"
    private val lock = Any()

    private fun keysDir(ctx: Context): File = File(ctx.filesDir, "keys").apply { mkdirs() }
    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Every key BFE knows about: the built-in first (created on demand), then imports/generated. */
    fun list(ctx: Context): List<KeyRef> {
        migrateToVault(ctx)
        val out = ArrayList<KeyRef>()
        out += builtIn(ctx)
        stored(ctx).forEach { if (File(it.storePath).isFile) out += withPasswords(ctx, it, strict = false) }
        return out
    }

    /**
     * [key] as BFE keeps it, passwords included (a job only says which key to use). Throws when the
     * key is gone or the vault can't be read just now.
     */
    fun resolve(ctx: Context, key: KeyRef): KeyRef {
        if (key.isBuiltIn) return builtIn(ctx)
        migrateToVault(ctx)
        val k = stored(ctx).firstOrNull { it.storePath == key.storePath && File(it.storePath).isFile }
            ?: throw IOException("The signing key \"${key.displayName}\" is no longer in BFE")
        return withPasswords(ctx, k, strict = true)
    }

    /** Plain passwords left by BFE 1.1.0 and older go into the vault, then out of prefs. Safe to repeat. */
    fun migrateToVault(ctx: Context) {
        synchronized(lock) {
            val objs = storedJson(ctx)
            val legacy = objs.filter { it.has("storePassword") || it.has("keyPassword") }
            if (legacy.isEmpty()) return
            try {
                legacy.forEach { o ->
                    SecretVault.put(ctx, vaultName(o.optString("storePath")), sealedPasswords(o.optString("storePassword", ""), o.optString("keyPassword", "")))
                }
            } catch (e: IOException) {
                Log.w(TAG, "Signing-key passwords not moved to the vault yet: ${e.message}")
                return
            }
            // Only once every password is sealed: rewrite the list without them.
            objs.forEach { it.remove("storePassword"); it.remove("keyPassword") }
            saveJson(ctx, objs)
            Log.i(TAG, "Moved ${legacy.size} signing-key passwords into the vault")
        }
    }

    /** The key the user last signed with (falls back to the built-in). */
    fun lastUsed(ctx: Context): KeyRef {
        val name = prefs(ctx).getString("lastUsed", null)
        return list(ctx).firstOrNull { it.displayName == name } ?: builtIn(ctx)
    }

    fun setLastUsed(ctx: Context, key: KeyRef) {
        prefs(ctx).edit().putString("lastUsed", key.displayName).apply()
    }

    fun lastSchemes(ctx: Context): SignSchemes {
        val p = prefs(ctx)
        return SignSchemes(p.getBoolean("v1", true), p.getBoolean("v2", true), p.getBoolean("v3", true))
    }

    fun setLastSchemes(ctx: Context, s: SignSchemes) {
        prefs(ctx).edit().putBoolean("v1", s.v1).putBoolean("v2", s.v2).putBoolean("v3", s.v3).apply()
    }

    /** The built-in test key, generating + persisting it on first use. Blocking (RSA keygen). */
    fun builtIn(ctx: Context): KeyRef {
        val file = File(keysDir(ctx), "bfe-test.p12")
        val ref = KeyRef(file.absolutePath, "PKCS12", BUILT_IN_ALIAS, BUILT_IN_PASS, BUILT_IN_PASS, BUILT_IN_NAME)
        if (!file.isFile) {
            Log.i(TAG, "Minting the built-in BFE Test Key")
            generateInto(file, BUILT_IN_ALIAS, BUILT_IN_PASS, "CN=BFE Test Key, O=BFE, C=US", 30)
        }
        return ref
    }

    /**
     * Generates a fresh RSA-2048 self-signed key under [displayName] and remembers it.
     * [dn] e.g. "CN=Me, O=Org, C=US"; [validityYears] as in keytool's -validity.
     */
    fun generate(ctx: Context, displayName: String, dn: String, validityYears: Int, password: String): KeyRef {
        val safe = displayName.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "key" }
        var file = File(keysDir(ctx), "$safe.p12")
        var n = 1
        while (file.exists()) { file = File(keysDir(ctx), "$safe-${n++}.p12") }
        generateInto(file, "key", password, dn, validityYears)
        val ref = KeyRef(file.absolutePath, "PKCS12", "key", password, password, displayName)
        try {
            remember(ctx, ref)
        } catch (e: IOException) {
            file.delete()
            throw e
        }
        return ref
    }

    /** The importable keystore types, tried in order when the user doesn't know. */
    val STORE_TYPES = listOf("PKCS12", "BKS", "JKS")

    /**
     * Copies [source] into app-private storage and returns the aliases it holds under [storeType] /
     * [storePassword]. Throws with a readable message when the type isn't available on this
     * platform (JKS on Android) or the password is wrong.
     */
    fun openStore(storeType: String, source: File, storePassword: String): KeyStore {
        val ks = try {
            KeyStore.getInstance(storeType)
        } catch (e: java.security.KeyStoreException) {
            throw IOException("$storeType keystores aren't supported on this device — convert it to PKCS12 (keytool -importkeystore -deststoretype PKCS12) and import the .p12")
        }
        try {
            FileInputStream(source).use { ks.load(it, storePassword.toCharArray()) }
        } catch (e: IOException) {
            throw IOException("Couldn't open ${source.name} as $storeType: ${e.message ?: "wrong password or format"}")
        }
        return ks
    }

    fun aliases(ks: KeyStore): List<String> = ks.aliases().toList().filter { runCatching { ks.isKeyEntry(it) }.getOrDefault(false) }

    /** Imports [source] (already opened successfully as [storeType]) and remembers [alias]. */
    fun import(ctx: Context, source: File, storeType: String, alias: String, storePassword: String, keyPassword: String, displayName: String): KeyRef {
        val safe = source.name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        var file = File(keysDir(ctx), safe)
        var n = 1
        while (file.exists()) { file = File(keysDir(ctx), "${source.nameWithoutExtension}-${n++}.${source.extension}") }
        source.copyTo(file, overwrite = true)
        val ref = KeyRef(file.absolutePath, storeType, alias, storePassword, keyPassword, displayName)
        try {
            // Prove the key opens before keeping it.
            load(ref)
            remember(ctx, ref)
        } catch (e: Exception) {
            file.delete()
            throw e
        }
        return ref
    }

    fun remove(ctx: Context, key: KeyRef) {
        if (key.isBuiltIn) return
        // The other entries stay exactly as stored (one the migration hasn't reached keeps its passwords).
        synchronized(lock) {
            saveJson(ctx, storedJson(ctx).filter { o -> o.optString("storePath").let { File(it).isFile && it != key.storePath } })
        }
        runCatching { SecretVault.remove(ctx, vaultName(key.storePath)) }
        File(key.storePath).delete()
    }

    class Loaded(val privateKey: PrivateKey, val certificates: List<X509Certificate>) {
        val subject: String get() = certificates.firstOrNull()?.subjectX500Principal?.name ?: "?"
    }

    /** Opens [key]'s store and pulls the private key + chain. */
    fun load(key: KeyRef): Loaded {
        val ks = openStore(key.storeType, File(key.storePath), key.storePassword)
        val pk = try {
            ks.getKey(key.alias, key.keyPassword.toCharArray()) as? PrivateKey
        } catch (e: Exception) {
            throw IOException("Couldn't unlock key \"${key.alias}\": ${e.message ?: "wrong key password"}")
        } ?: throw IOException("\"${key.alias}\" isn't a private-key entry")
        val chain = ks.getCertificateChain(key.alias)?.mapNotNull { it as? X509Certificate }.orEmpty()
        if (chain.isEmpty()) throw IOException("\"${key.alias}\" has no certificate")
        return Loaded(pk, chain)
    }

    // ── internals ──

    private fun generateInto(file: File, alias: String, password: String, dn: String, validityYears: Int) {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048, SecureRandom())
        val kp = kpg.generateKeyPair()
        val now = System.currentTimeMillis()
        val notBefore = Date(now - 24L * 3600 * 1000)
        val notAfter = Date(now + validityYears.coerceIn(1, 100) * 365L * 24 * 3600 * 1000)
        val name = X500Name(dn)
        val serial = BigInteger(64, SecureRandom()).abs().add(BigInteger.ONE)
        val builder = JcaX509v3CertificateBuilder(name, serial, notBefore, notAfter, name, kp.public)
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(kp.private)
        val cert = JcaX509CertificateConverter().getCertificate(builder.build(signer))
        val ks = KeyStore.getInstance("PKCS12")
        ks.load(null, null)
        ks.setKeyEntry(alias, kp.private, password.toCharArray(), arrayOf(cert))
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { ks.store(it, password.toCharArray()) }
    }

    private fun remember(ctx: Context, ref: KeyRef) {
        // Passwords into the vault first (throws when it's unavailable), then the key into the list.
        SecretVault.put(ctx, vaultName(ref.storePath), sealedPasswords(ref.storePassword, ref.keyPassword))
        synchronized(lock) {
            val cur = storedJson(ctx).filter { o -> o.optString("storePath").let { File(it).isFile && it != ref.storePath } }
            saveJson(ctx, cur + toJson(ref))
        }
    }

    /** The keys in prefs, without the built-in one. Passwords are blank unless an unmigrated entry still has them. */
    private fun stored(ctx: Context): List<KeyRef> = storedJson(ctx).mapNotNull { fromJson(it) }

    private fun storedJson(ctx: Context): List<JSONObject> = runCatching {
        val arr = JSONArray(prefs(ctx).getString("keys", "[]") ?: "[]")
        (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
    }.getOrDefault(emptyList())

    /** [key] with its passwords from the vault; [strict] throws when the vault can't be read, else they stay as they are. */
    private fun withPasswords(ctx: Context, key: KeyRef, strict: Boolean): KeyRef {
        val sealed = try {
            SecretVault.get(ctx, vaultName(key.storePath))
        } catch (e: IOException) {
            if (strict) throw e
            Log.w(TAG, "Signing-key passwords unavailable: ${e.message}")
            null
        } ?: return key
        return runCatching {
            val o = JSONObject(sealed)
            key.copy(storePassword = o.getString("store"), keyPassword = o.getString("key"))
        }.getOrDefault(key)
    }

    private fun vaultName(storePath: String) = VAULT_PREFIX + File(storePath).name

    private fun sealedPasswords(store: String, key: String) = JSONObject().put("store", store).put("key", key).toString()

    private fun saveJson(ctx: Context, keys: List<JSONObject>) {
        val arr = JSONArray()
        keys.forEach { arr.put(it) }
        // commit: the migration drops the plain passwords only after the vault has them.
        prefs(ctx).edit().putString("keys", arr.toString()).commit()
    }

    /** No passwords: those live in the vault. */
    private fun toJson(k: KeyRef): JSONObject = JSONObject()
        .put("storePath", k.storePath).put("storeType", k.storeType).put("alias", k.alias).put("displayName", k.displayName)

    private fun fromJson(o: JSONObject): KeyRef? = runCatching {
        KeyRef(
            o.getString("storePath"), o.getString("storeType"), o.getString("alias"),
            o.optString("storePassword", ""), o.optString("keyPassword", ""), o.getString("displayName"),
        )
    }.getOrNull()
}

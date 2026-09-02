package com.the412banner.bfe.apk

import android.content.Context
import android.util.Log
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
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
 *   copied into `filesDir/keys/` and remembered with its alias + passwords in a private prefs file.
 * - Generated keys: same as the built-in path, with the user's CN / organisation / validity.
 */
object SigningKeys {
    private const val TAG = "SigningKeys"
    const val BUILT_IN_NAME = "BFE Test Key"
    private const val BUILT_IN_ALIAS = "bfetest"
    private const val BUILT_IN_PASS = "bfetest"
    private const val PREFS = "signing_keys"

    private fun keysDir(ctx: Context): File = File(ctx.filesDir, "keys").apply { mkdirs() }
    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Every key BFE knows about: the built-in first (created on demand), then imports/generated. */
    fun list(ctx: Context): List<KeyRef> {
        val out = ArrayList<KeyRef>()
        out += builtIn(ctx)
        val raw = prefs(ctx).getString("keys", "[]") ?: "[]"
        runCatching {
            val arr = org.json.JSONArray(raw)
            for (i in 0 until arr.length()) fromJson(arr.getJSONObject(i))?.let { if (File(it.storePath).isFile) out += it }
        }
        return out
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
        remember(ctx, ref)
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
        // Prove the key opens before keeping it.
        load(ref)
        remember(ctx, ref)
        return ref
    }

    fun remove(ctx: Context, key: KeyRef) {
        if (key.isBuiltIn) return
        val remaining = list(ctx).filter { !it.isBuiltIn && it.storePath != key.storePath }
        save(ctx, remaining)
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
        val cur = list(ctx).filter { !it.isBuiltIn && it.storePath != ref.storePath }
        save(ctx, cur + ref)
    }

    private fun save(ctx: Context, keys: List<KeyRef>) {
        val arr = org.json.JSONArray()
        keys.forEach { arr.put(toJson(it)) }
        prefs(ctx).edit().putString("keys", arr.toString()).apply()
    }

    private fun toJson(k: KeyRef): JSONObject = JSONObject()
        .put("storePath", k.storePath).put("storeType", k.storeType).put("alias", k.alias)
        .put("storePassword", k.storePassword).put("keyPassword", k.keyPassword).put("displayName", k.displayName)

    private fun fromJson(o: JSONObject): KeyRef? = runCatching {
        KeyRef(
            o.getString("storePath"), o.getString("storeType"), o.getString("alias"),
            o.optString("storePassword", ""), o.optString("keyPassword", ""), o.getString("displayName"),
        )
    }.getOrNull()
}

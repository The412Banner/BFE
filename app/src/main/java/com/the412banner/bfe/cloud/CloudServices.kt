package com.the412banner.bfe.cloud

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddToDrive
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/** Which BFE screen a remote belongs to: cloud storage accounts, or connections to servers. */
enum class CloudSection { CLOUD, REMOTE }

enum class FieldKind { TEXT, PASSWORD, NUMBER, URL, EMAIL, BOOL }

/** One input on a service's sign-in form; [key] is the rclone option it sets. */
data class ServiceField(
    val key: String,
    val label: String,
    val kind: FieldKind = FieldKind.TEXT,
    val required: Boolean = false,
    val default: String = "",
    val hint: String = "",
    val help: String? = null,
    /** Suggested values (value to label); the field stays editable. */
    val choices: List<Pair<String, String>> = emptyList(),
    /** Sent instead when the field is left empty (e.g. FTP's "anonymous"). */
    val blankValue: String? = null,
)

/**
 * A service BFE offers by name in the Add picker. [type] is the rclone backend; [fixed] are settings
 * that make it this service (an S3 provider, a WebDAV vendor). OAuth services sign in through the
 * browser; the rest use [fields].
 */
data class CloudService(
    val id: String,
    val label: String,
    val type: String,
    val section: CloudSection,
    val oauth: Boolean,
    val icon: ImageVector,
    val color: Color,
    val blurb: String,
    val fixed: Map<String, String> = emptyMap(),
    val fields: List<ServiceField> = emptyList(),
    /** Setup questions BFE answers for the user (rclone option name to value). */
    val autoAnswers: Map<String, String> = emptyMap(),
    /** A heads-up shown on the form, with an optional link. */
    val note: String? = null,
    val noteLink: String? = null,
    /** Show the "use my own client ID" fields expanded (when the shared one is being retired). */
    val ownClientIdRecommended: Boolean = false,
    /** Object storage: folders only exist while they hold files; offers a starting bucket. */
    val bucketBased: Boolean = false,
)

object CloudServices {
    private val S3_KEYS = listOf(
        ServiceField("access_key_id", "Access key ID", required = true),
        ServiceField("secret_access_key", "Secret access key", FieldKind.PASSWORD, required = true),
    )

    /** BFE-only form value: the folder/bucket the browser opens at (not an rclone option). */
    const val START_PATH_KEY = "bfe_start_path"
    private val BUCKET = ServiceField(
        START_PATH_KEY, "Bucket (optional)",
        hint = "my-bucket",
        help = "Open this bucket directly. Needed when your key can only see one bucket.",
    )

    val all: List<CloudService> = listOf(
        // ── Cloud: browser sign-in ──
        CloudService(
            "drive", "Google Drive", "drive", CloudSection.CLOUD, oauth = true,
            icon = Icons.Filled.AddToDrive, color = Color(0xFF1E8E3E),
            blurb = "Sign in with Google",
            autoAnswers = mapOf("config_shared_client_id" to "true"),
            note = "rclone's shared Google client ID is being retired during 2026. It still works for now, but " +
                "your own free client ID keeps Drive working after that and isn't rate-limited with everyone else.",
            noteLink = "https://rclone.org/drive/#making-your-own-client-id",
            ownClientIdRecommended = true,
        ),
        CloudService(
            "onedrive", "OneDrive", "onedrive", CloudSection.CLOUD, oauth = true,
            icon = Icons.Filled.Cloud, color = Color(0xFF0F6CBD),
            blurb = "Personal or work account",
            // Personal / Business drive (SharePoint libraries: use More services). The drive picker
            // that follows is answered automatically when the account has a single drive.
            autoAnswers = mapOf("config_type" to "onedrive", "config_drive_ok" to "true"),
        ),
        CloudService(
            "dropbox", "Dropbox", "dropbox", CloudSection.CLOUD, oauth = true,
            icon = Icons.Filled.Inventory2, color = Color(0xFF0061FE), blurb = "Sign in with Dropbox",
        ),
        CloudService(
            "box", "Box", "box", CloudSection.CLOUD, oauth = true,
            icon = Icons.Filled.Inventory, color = Color(0xFF0061D5), blurb = "Sign in with Box",
        ),
        CloudService(
            "pcloud", "pCloud", "pcloud", CloudSection.CLOUD, oauth = true,
            icon = Icons.Filled.CloudQueue, color = Color(0xFF17BED0), blurb = "Sign in with pCloud",
        ),
        CloudService(
            "yandex", "Yandex Disk", "yandex", CloudSection.CLOUD, oauth = true,
            icon = Icons.Filled.CloudSync, color = Color(0xFFE53935), blurb = "Sign in with Yandex",
        ),
        // ── Cloud: forms ──
        CloudService(
            "mega", "MEGA", "mega", CloudSection.CLOUD, oauth = false,
            icon = Icons.Filled.CloudCircle, color = Color(0xFFD9272E), blurb = "Email and password",
            fields = listOf(
                ServiceField("user", "Email", FieldKind.EMAIL, required = true),
                ServiceField("pass", "Password", FieldKind.PASSWORD, required = true),
                ServiceField("2fa", "Two-factor code", FieldKind.NUMBER, hint = "Only if 2FA is on", help = "The current 6-digit code from your authenticator app."),
            ),
        ),
        CloudService(
            "protondrive", "Proton Drive", "protondrive", CloudSection.CLOUD, oauth = false,
            icon = Icons.Filled.Shield, color = Color(0xFF6D4AFF), blurb = "Proton account",
            fields = listOf(
                ServiceField("username", "Proton email or username", FieldKind.EMAIL, required = true),
                ServiceField("password", "Password", FieldKind.PASSWORD, required = true),
                ServiceField("2fa", "Two-factor code", FieldKind.NUMBER, hint = "Only if 2FA is on", help = "The current 6-digit code; needed only for this first sign-in."),
                ServiceField("mailbox_password", "Mailbox password", FieldKind.PASSWORD, hint = "Only for two-password accounts"),
            ),
            note = "Proton Drive support in rclone is a community integration, not an official Proton app.",
        ),
        CloudService(
            "nextcloud", "Nextcloud", "webdav", CloudSection.CLOUD, oauth = false,
            icon = Icons.Filled.CloudDone, color = Color(0xFF0082C9), blurb = "Your Nextcloud server",
            fixed = mapOf("vendor" to "nextcloud"),
            fields = listOf(
                ServiceField("url", "WebDAV address", FieldKind.URL, required = true, hint = "https://cloud.example.com/remote.php/dav/files/USER/",
                    help = "Nextcloud shows it under Files → Settings → WebDAV."),
                ServiceField("user", "Username", required = true),
                ServiceField("pass", "App password", FieldKind.PASSWORD, required = true, help = "Create one under Settings → Security → Devices & sessions."),
            ),
        ),
        CloudService(
            "owncloud", "ownCloud", "webdav", CloudSection.CLOUD, oauth = false,
            icon = Icons.Filled.CloudDone, color = Color(0xFF041E42), blurb = "Your ownCloud server",
            fixed = mapOf("vendor" to "owncloud"),
            fields = listOf(
                ServiceField("url", "WebDAV address", FieldKind.URL, required = true, hint = "https://owncloud.example.com/remote.php/webdav/"),
                ServiceField("user", "Username", required = true),
                ServiceField("pass", "Password", FieldKind.PASSWORD, required = true),
            ),
        ),
        CloudService(
            "s3", "Amazon S3", "s3", CloudSection.CLOUD, oauth = false,
            icon = Icons.Filled.Storage, color = Color(0xFFFF9900), blurb = "Access key",
            fixed = mapOf("provider" to "AWS"), bucketBased = true,
            fields = S3_KEYS + listOf(
                ServiceField("region", "Region", default = "us-east-1", choices = listOf(
                    "us-east-1" to "US East (N. Virginia)", "us-east-2" to "US East (Ohio)", "us-west-1" to "US West (N. California)",
                    "us-west-2" to "US West (Oregon)", "eu-west-1" to "EU (Ireland)", "eu-west-2" to "EU (London)",
                    "eu-central-1" to "EU (Frankfurt)", "ap-southeast-1" to "Asia Pacific (Singapore)", "ap-northeast-1" to "Asia Pacific (Tokyo)",
                    "ap-south-1" to "Asia Pacific (Mumbai)", "sa-east-1" to "South America (São Paulo)",
                )),
                BUCKET,
            ),
        ),
        CloudService(
            "b2", "Backblaze B2", "b2", CloudSection.CLOUD, oauth = false,
            icon = Icons.Filled.Storage, color = Color(0xFFE21E29), blurb = "Application key", bucketBased = true,
            fields = listOf(
                ServiceField("account", "Key ID", required = true, help = "The keyID of an application key (or your account ID with the master key)."),
                ServiceField("key", "Application key", FieldKind.PASSWORD, required = true),
                BUCKET,
            ),
        ),
        CloudService(
            "wasabi", "Wasabi", "s3", CloudSection.CLOUD, oauth = false,
            icon = Icons.Filled.Storage, color = Color(0xFF00C853), blurb = "Access key",
            fixed = mapOf("provider" to "Wasabi"), bucketBased = true,
            fields = S3_KEYS + listOf(
                ServiceField("endpoint", "Endpoint", default = "s3.wasabisys.com", choices = listOf(
                    "s3.wasabisys.com" to "US East 1", "s3.us-east-2.wasabisys.com" to "US East 2",
                    "s3.us-central-1.wasabisys.com" to "US Central 1", "s3.us-west-1.wasabisys.com" to "US West 1",
                    "s3.ca-central-1.wasabisys.com" to "CA Central 1", "s3.eu-central-1.wasabisys.com" to "EU Central 1",
                    "s3.eu-central-2.wasabisys.com" to "EU Central 2", "s3.eu-west-1.wasabisys.com" to "EU West 1",
                    "s3.eu-west-2.wasabisys.com" to "EU West 2", "s3.ap-northeast-1.wasabisys.com" to "AP Northeast 1 (Tokyo)",
                    "s3.ap-southeast-1.wasabisys.com" to "AP Southeast 1 (Singapore)", "s3.ap-southeast-2.wasabisys.com" to "AP Southeast 2 (Sydney)",
                )),
                BUCKET,
            ),
        ),
        CloudService(
            "r2", "Cloudflare R2", "s3", CloudSection.CLOUD, oauth = false,
            icon = Icons.Filled.Storage, color = Color(0xFFF38020), blurb = "API token keys",
            fixed = mapOf("provider" to "Cloudflare", "region" to "auto"), bucketBased = true,
            fields = S3_KEYS + listOf(
                ServiceField("endpoint", "Endpoint", FieldKind.URL, required = true, hint = "https://ACCOUNT_ID.r2.cloudflarestorage.com"),
                BUCKET,
            ),
        ),
        CloudService(
            "minio", "MinIO", "s3", CloudSection.CLOUD, oauth = false,
            icon = Icons.Filled.Storage, color = Color(0xFFC72C48), blurb = "Self-hosted S3",
            fixed = mapOf("provider" to "Minio"), bucketBased = true,
            fields = listOf(ServiceField("endpoint", "Server address", FieldKind.URL, required = true, hint = "http://192.168.1.10:9000")) + S3_KEYS + BUCKET,
        ),
        CloudService(
            "s3other", "S3-compatible", "s3", CloudSection.CLOUD, oauth = false,
            icon = Icons.Filled.Storage, color = Color(0xFF607D8B), blurb = "Any other S3 service",
            fixed = mapOf("provider" to "Other"), bucketBased = true,
            fields = listOf(ServiceField("endpoint", "Endpoint", FieldKind.URL, required = true, hint = "https://s3.example.com")) +
                S3_KEYS + listOf(ServiceField("region", "Region", hint = "Leave empty if unsure")) + BUCKET,
        ),

        // ── Remote: servers ──
        CloudService(
            "sftp", "SFTP", "sftp", CloudSection.REMOTE, oauth = false,
            icon = Icons.Filled.Terminal, color = Color(0xFF455A64), blurb = "SSH file transfer",
            fields = listOf(
                ServiceField("host", "Host", required = true, hint = "192.168.1.20 or server.example.com"),
                ServiceField("port", "Port", FieldKind.NUMBER, default = "22"),
                ServiceField("user", "Username", required = true),
                ServiceField("pass", "Password", FieldKind.PASSWORD, hint = "Leave empty to use a key file"),
                ServiceField("key_file", "Private key file", hint = "/storage/emulated/0/.ssh/id_ed25519", help = "Path to an OpenSSH private key on this device (optional)."),
                ServiceField("key_file_pass", "Key passphrase", FieldKind.PASSWORD, hint = "Only if the key is encrypted"),
            ),
        ),
        CloudService(
            "ftp", "FTP", "ftp", CloudSection.REMOTE, oauth = false,
            icon = Icons.Filled.Dns, color = Color(0xFF5D4037), blurb = "FTP or FTPS server",
            fields = listOf(
                ServiceField("host", "Host", required = true, hint = "192.168.1.20 or ftp.example.com"),
                ServiceField("port", "Port", FieldKind.NUMBER, default = "21"),
                ServiceField("user", "Username", hint = "Empty = anonymous", blankValue = "anonymous"),
                ServiceField("pass", "Password", FieldKind.PASSWORD),
                ServiceField("explicit_tls", "Use TLS (explicit FTPS)", FieldKind.BOOL, default = "false"),
                ServiceField("tls", "Implicit FTPS (port 990)", FieldKind.BOOL, default = "false"),
                ServiceField("no_check_certificate", "Accept self-signed certificates", FieldKind.BOOL, default = "false"),
            ),
        ),
        CloudService(
            "smb", "SMB / Windows share", "smb", CloudSection.REMOTE, oauth = false,
            icon = Icons.Filled.Computer, color = Color(0xFF1565C0), blurb = "PC, NAS or router share",
            fields = listOf(
                ServiceField("host", "Host", required = true, hint = "192.168.1.20 or NAS-NAME"),
                ServiceField("user", "Username", hint = "Empty = guest", blankValue = "guest"),
                ServiceField("pass", "Password", FieldKind.PASSWORD),
                ServiceField("domain", "Domain / workgroup", default = "WORKGROUP"),
                ServiceField("port", "Port", FieldKind.NUMBER, default = "445"),
            ),
        ),
        CloudService(
            "webdav", "WebDAV", "webdav", CloudSection.REMOTE, oauth = false,
            icon = Icons.Filled.Language, color = Color(0xFF00897B), blurb = "Any WebDAV server",
            fixed = mapOf("vendor" to "other"),
            fields = listOf(
                ServiceField("url", "Address", FieldKind.URL, required = true, hint = "https://dav.example.com/files/"),
                ServiceField("user", "Username"),
                ServiceField("pass", "Password", FieldKind.PASSWORD),
            ),
        ),
        CloudService(
            "http", "HTTP folder", "http", CloudSection.REMOTE, oauth = false,
            icon = Icons.Filled.Public, color = Color(0xFF6A1B9A), blurb = "Read-only web directory",
            fields = listOf(ServiceField("url", "Address", FieldKind.URL, required = true, hint = "https://downloads.example.com/")),
        ),
    )

    fun forSection(section: CloudSection) = all.filter { it.section == section }

    /** WebDAV vendors that are cloud products rather than plain servers. */
    private val CLOUD_WEBDAV = setOf("nextcloud", "owncloud", "infinitescale", "sharepoint", "sharepoint-ntlm", "fastmail")

    fun sectionOf(type: String, params: Map<String, String>): CloudSection = when (type) {
        "sftp", "ftp", "smb", "http" -> CloudSection.REMOTE
        "webdav" -> if (params["vendor"] in CLOUD_WEBDAV) CloudSection.CLOUD else CloudSection.REMOTE
        else -> CloudSection.CLOUD
    }

    /** The curated service an existing remote was made from (by type + fixed settings), if any. */
    fun forRemote(remote: RcRemote): CloudService? =
        all.filter { it.type == remote.type }
            .sortedByDescending { it.fixed.size }
            .firstOrNull { s -> s.fixed.all { (k, v) -> k == "region" || remote.params[k].equals(v, ignoreCase = true) } }

    /** Icon + colour for any remote, curated or not. */
    fun look(remote: RcRemote): Pair<ImageVector, Color> =
        forRemote(remote)?.let { it.icon to it.color } ?: lookForType(remote.type)

    fun lookForType(type: String): Pair<ImageVector, Color> = when (type) {
        "crypt" -> Icons.Filled.Shield to Color(0xFF37474F)
        "union", "combine" -> Icons.Filled.Widgets to Color(0xFF546E7A)
        "local" -> Icons.Filled.Storage to Color(0xFF546E7A)
        else -> Icons.Filled.Cloud to Color(0xFF546E7A)
    }

    /** "Google Drive", or the backend's own description for one made through More services. */
    fun labelFor(remote: RcRemote, providers: List<RcProvider>?): String =
        forRemote(remote)?.label ?: providers?.firstOrNull { it.name == remote.type }?.description ?: remote.type

    /** rclone's OAuth backends among BFE's (the ones whose setup ends in a browser sign-in). */
    val OAUTH_TYPES = setOf("drive", "onedrive", "dropbox", "box", "pcloud", "yandex")
}

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.the412banner.bfe.ui.media

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.exifinterface.media.ExifInterface
import com.the412banner.bfe.core.StringUtils
import com.the412banner.bfe.media.MediaRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Name, folder, size, resolution, dates, camera and exposure for one photo, in a glass sheet. */
@Composable
internal fun PhotoDetailsSheet(item: MediaRef, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val rows by produceState<List<Pair<String, String>>?>(null, item.key) {
        value = withContext(Dispatchers.IO) { runCatching { photoDetails(context, item) }.getOrDefault(emptyList()) }
    }
    val shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = shape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.94f),
        modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f), shape),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        ) {
            Text("Details", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            val r = rows
            if (r == null) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally).padding(24.dp))
            } else {
                SelectionContainer {
                    Column {
                        r.forEach { (label, value) ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(112.dp),
                                )
                                Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun open(context: Context, item: MediaRef): InputStream? =
    item.path?.let { File(it).inputStream() } ?: context.contentResolver.openInputStream(Uri.parse(item.uri))

/** Blocking: reads the image header and EXIF. */
private fun photoDetails(context: Context, item: MediaRef): List<Pair<String, String>> {
    val out = ArrayList<Pair<String, String>>()
    val file = item.path?.let { File(it) }
    out += "Name" to item.name
    file?.parentFile?.let { out += "Folder" to it.absolutePath }
    val size = file?.length() ?: item.size
    if (size >= 0) out += "Size" to "${StringUtils.formatBytes(size)} (${"%,d".format(size)} bytes)"

    val exif = runCatching {
        if (file != null) ExifInterface(file.absolutePath) else open(context, item)?.use { ExifInterface(it) }
    }.getOrNull()

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    runCatching { open(context, item)?.use { BitmapFactory.decodeStream(it, null, bounds) } }
    var w = bounds.outWidth
    var h = bounds.outHeight
    if (w <= 0 || h <= 0) {
        w = exif?.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0) ?: 0
        h = exif?.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0) ?: 0
    }
    val turned = exif?.rotationDegrees?.let { it == 90 || it == 270 } == true
    if (turned) { val t = w; w = h; h = t }
    if (w > 0 && h > 0) {
        val mp = w.toLong() * h / 1_000_000.0
        out += "Resolution" to "$w × $h" + if (mp >= 0.1) " (%.1f MP)".format(mp) else ""
    }
    bounds.outMimeType?.let { out += "Type" to it }

    exif?.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)?.let { raw ->
        val parsed = runCatching { SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).parse(raw) }.getOrNull()
        out += "Taken" to (parsed?.let { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(it) } ?: raw)
    }
    val modified = file?.lastModified() ?: item.modified
    if (modified > 0) out += "Modified" to DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(modified))

    if (exif != null) {
        val make = exif.getAttribute(ExifInterface.TAG_MAKE)?.trim().orEmpty()
        val model = exif.getAttribute(ExifInterface.TAG_MODEL)?.trim().orEmpty()
        val camera = if (model.startsWith(make, ignoreCase = true)) model else "$make $model".trim()
        if (camera.isNotEmpty()) out += "Camera" to camera
        exif.getAttribute(ExifInterface.TAG_LENS_MODEL)?.trim()?.takeIf { it.isNotEmpty() }?.let { out += "Lens" to it }
        val parts = ArrayList<String>()
        exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0).takeIf { it > 0 }?.let { t ->
            parts += if (t < 1) "1/${(1 / t).roundToInt()} s" else "%.1f s".format(t)
        }
        exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0).takeIf { it > 0 }?.let { parts += "f/%.1f".format(it) }
        exif.getAttributeInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, 0).takeIf { it > 0 }?.let { parts += "ISO $it" }
        exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0).takeIf { it > 0 }?.let { parts += "%.1f mm".format(it) }
        if (parts.isNotEmpty()) out += "Exposure" to parts.joinToString(" · ")
        exif.latLong?.let { (lat, lon) -> out += "Location" to "%.5f, %.5f".format(lat, lon) }
    }
    return out
}

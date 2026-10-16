package com.the412banner.bfe.ui.screens.server

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.nayuki.qrcodegen.QrCode
import kotlin.math.floor

/**
 * [text] as a QR code (Nayuki's QR Code generator, MIT). Always dark modules on white with a quiet
 * zone, whatever the theme — that's what phone cameras read most reliably.
 */
@Composable
fun QrCodeImage(text: String, size: Dp = 176.dp, modifier: Modifier = Modifier) {
    val qr = remember(text) { runCatching { QrCode.encodeText(text, QrCode.Ecc.MEDIUM) }.getOrNull() } ?: return
    Canvas(
        modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White)
            .padding(10.dp)
            .semantics { contentDescription = "QR code for $text" },
    ) {
        val n = qr.size
        // Whole-pixel modules, centred: fractional cells leave hairline seams some scanners trip on.
        val cell = floor(this.size.minDimension / n)
        val origin = (this.size.minDimension - cell * n) / 2f
        for (y in 0 until n) {
            for (x in 0 until n) {
                if (qr.getModule(x, y)) {
                    drawRect(Color.Black, topLeft = Offset(origin + x * cell, origin + y * cell), size = Size(cell, cell))
                }
            }
        }
    }
}

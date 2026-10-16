package com.the412banner.bfe.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/** The app's full name; its initials spell BFE, the short form used in messages. */
const val BRAND_NAME = "Banners File Explorer"

/**
 * The "Banners File Explorer" wordmark: the B, F and E initials a touch larger and bolder, in the accent
 * colour, each with a soft glow behind it; the rest of each word in the regular text colour.
 */
@Composable
fun BrandWordmark(modifier: Modifier = Modifier, fontSize: TextUnit = 20.sp) {
    val accent = MaterialTheme.colorScheme.primary
    val ink = MaterialTheme.colorScheme.onSurface
    val text = remember(accent, ink, fontSize) {
        val initial = SpanStyle(
            color = accent,
            fontWeight = FontWeight.ExtraBold,
            fontSize = fontSize * 1.15f,
            shadow = Shadow(color = accent.copy(alpha = 0.6f), offset = Offset.Zero, blurRadius = 14f),
        )
        val rest = SpanStyle(color = ink, fontWeight = FontWeight.Medium)
        buildAnnotatedString {
            BRAND_NAME.split(' ').forEachIndexed { i, word ->
                if (i > 0) withStyle(rest) { append(' ') }
                withStyle(initial) { append(word.first()) }
                withStyle(rest) { append(word.drop(1)) }
            }
        }
    }
    Text(text, modifier = modifier, fontSize = fontSize, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

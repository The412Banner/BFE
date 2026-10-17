package com.the412banner.bfe.ui.docs

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.the412banner.bfe.docs.ReadingTheme
import com.the412banner.bfe.ui.components.AccentButton
import com.the412banner.bfe.ui.components.BfeDropdownMenu
import com.the412banner.bfe.ui.components.GlassPillBar
import com.the412banner.bfe.ui.components.bfeBarBackground
import com.the412banner.bfe.ui.components.bfeTopBarColors
import com.the412banner.bfe.ui.theme.Glass
import com.the412banner.bfe.ui.theme.GlassLevel
import com.the412banner.bfe.ui.theme.LocalGlass
import com.the412banner.bfe.ui.theme.WindowBlurBehind
import com.the412banner.bfe.ui.theme.glass
import com.the412banner.bfe.ui.theme.isAurora
import com.the412banner.bfe.ui.theme.rememberWindowBlurAvailable

/** The viewer's remembered choices: text size, wrapping, line numbers, PDF night mode, last chapter. */
internal object DocPrefs {
    private const val PREFS = "bfe_document_viewer"
    private fun p(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Text size in percent (WebView textZoom; the text viewer scales its 13 sp by it). */
    fun textZoom(ctx: Context): Int = p(ctx).getInt("text_zoom", 100).coerceIn(MIN_ZOOM, MAX_ZOOM)
    fun setTextZoom(ctx: Context, v: Int) = p(ctx).edit().putInt("text_zoom", v.coerceIn(MIN_ZOOM, MAX_ZOOM)).apply()

    fun wrap(ctx: Context): Boolean = p(ctx).getBoolean("wrap", true)
    fun setWrap(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("wrap", v).apply()

    fun lineNumbers(ctx: Context): Boolean = p(ctx).getBoolean("line_numbers", true)
    fun setLineNumbers(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("line_numbers", v).apply()

    fun pdfNight(ctx: Context): Boolean = p(ctx).getBoolean("pdf_night", false)
    fun setPdfNight(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("pdf_night", v).apply()

    /** Where a multi-page document (e-book chapter, sheet, PDF page) was left, by file key. */
    fun lastPage(ctx: Context, key: String): Int = p(ctx).getInt("page_$key", 0)
    fun setLastPage(ctx: Context, key: String, page: Int) {
        val prefs = p(ctx)
        // Keep the list small: forget the oldest positions past 200 files.
        val all = prefs.all.keys.filter { it.startsWith("page_") }
        val e = prefs.edit()
        if (all.size > 200) all.take(all.size - 200).forEach { e.remove(it) }
        e.putInt("page_$key", page).apply()
    }

    const val MIN_ZOOM = 50
    const val MAX_ZOOM = 250
}

private fun Color.css(): String = if (alpha >= 0.999f) "#%06x".format(toArgb() and 0xFFFFFF)
else "rgba(%d,%d,%d,%.3f)".format(java.util.Locale.US, (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt(), alpha)

/**
 * The reading style's colours from BFE's theme. Classic: the page is the app background. Aurora: the
 * page is clear (the glass card under the WebView is the ground) and header cells / slides take the
 * raised glass tint.
 */
@Composable
internal fun rememberReadingTheme(): ReadingTheme {
    val cs = MaterialTheme.colorScheme
    val aurora = isAurora
    val g = LocalGlass.current
    val dark = cs.background.luminance() < 0.5f
    return remember(cs, aurora, g) {
        ReadingTheme(
            dark = dark,
            text = cs.onSurface.css(),
            muted = cs.onSurfaceVariant.css(),
            link = cs.primary.css(),
            border = (if (dark) cs.outline else cs.outlineVariant.compositeOver(cs.background)).css(),
            codeBg = (if (aurora) cs.onSurface.copy(alpha = if (dark) 0.08f else 0.06f) else cs.surfaceVariant).css(),
            page = if (aurora) "transparent" else cs.background.css(),
            raised = (if (aurora) cs.onSurface.copy(alpha = if (dark) 0.06f else 0.045f) else cs.surfaceContainerHigh).css(),
        )
    }
}

/** The viewer's app bar: Back, the file name over a detail line, and [actions]. Clear over Aurora's backdrop. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DocTopBar(title: String, subtitle: String?, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit) {
    TopAppBar(
        colors = bfeTopBarColors(TopAppBarDefaults.topAppBarColors()),
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        title = {
            Column {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 17.sp)
                if (!subtitle.isNullOrBlank()) {
                    Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        actions = actions,
    )
}

/** Find in page / in text: query field, "3/12", previous, next, close. */
@Composable
internal fun FindBar(
    visible: Boolean,
    query: String,
    onQuery: (String) -> Unit,
    status: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
) {
    AnimatedVisibility(visible = visible) {
        val focus = remember { FocusRequester() }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isAurora) Modifier.padding(horizontal = 12.dp, vertical = 4.dp).glass(Glass.Card, GlassLevel.Raised) else Modifier)
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                placeholder = { Text("Find") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onNext() }),
                modifier = Modifier.weight(1f).focusRequester(focus),
            )
            Text(status, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp))
            IconButton(onClick = onPrev) { Icon(Icons.Filled.KeyboardArrowUp, "Previous") }
            IconButton(onClick = onNext) { Icon(Icons.Filled.KeyboardArrowDown, "Next") }
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close find") }
        }
        androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    }
}

/** "Aa": smaller / reset / larger text, in a small menu under the button. */
@Composable
internal fun TextSizeButton(percent: Int, onChange: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Filled.FormatSize, "Text size") }
        BfeDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
                IconButton(onClick = { onChange((percent - 10).coerceAtLeast(DocPrefs.MIN_ZOOM)) }, enabled = percent > DocPrefs.MIN_ZOOM) {
                    Icon(Icons.Filled.Remove, "Smaller text")
                }
                TextButton(onClick = { onChange(100) }, modifier = Modifier.widthIn(min = 72.dp)) { Text("$percent%") }
                IconButton(onClick = { onChange((percent + 10).coerceAtMost(DocPrefs.MAX_ZOOM)) }, enabled = percent < DocPrefs.MAX_ZOOM) {
                    Icon(Icons.Filled.Add, "Larger text")
                }
            }
        }
    }
}

/**
 * Where the document is drawn. Classic: full-bleed on the page colour. Aurora: a raised glass card
 * with a margin, so the backdrop frames the text.
 */
@Composable
internal fun DocSurface(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    if (isAurora) {
        Box(modifier.fillMaxSize().padding(start = 10.dp, end = 10.dp, bottom = 10.dp).glass(Glass.Card, GlassLevel.Raised), content = content)
    } else {
        Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), content = content)
    }
}

/**
 * The bottom controls (page / chapter / sheet navigation). Classic: a bar in the toolbar colour.
 * Aurora: a floating glass pill.
 */
@Composable
internal fun DocBottomBar(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    if (isAurora) {
        Box(modifier.fillMaxWidth().padding(bottom = 14.dp, start = 16.dp, end = 16.dp), contentAlignment = Alignment.Center) {
            GlassPillBar(content = content)
        }
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = modifier
                .fillMaxWidth()
                .bfeBarBackground(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            content = content,
        )
    }
}

/** A labelled button for [DocBottomBar]: icon + text, or text alone. */
@Composable
internal fun DocBarButton(label: String?, icon: ImageVector?, onClick: () -> Unit, enabled: Boolean = true, contentDescription: String? = label) {
    if (isAurora && label == null && icon != null) {
        com.the412banner.bfe.ui.components.GlassIconButton(icon, contentDescription, onClick, enabled = enabled)
        return
    }
    if (label == null && icon != null) {
        IconButton(onClick = onClick, enabled = enabled) { Icon(icon, contentDescription) }
        return
    }
    TextButton(onClick = onClick, enabled = enabled) {
        if (icon != null) { Icon(icon, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)) }
        Text(label.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 220.dp))
    }
}

/** A centred message (loading, a damaged file, an older Office format) with optional buttons. */
@Composable
internal fun DocNotice(
    icon: ImageVector,
    title: String,
    body: String?,
    modifier: Modifier = Modifier,
    primary: Pair<String, () -> Unit>? = null,
    secondary: Pair<String, () -> Unit>? = null,
) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .widthIn(max = 420.dp)
                .then(if (isAurora) Modifier.glass(Glass.Card, GlassLevel.Raised) else Modifier.clip(RoundedCornerShape(20.dp)).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp)))
                .padding(24.dp),
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            if (!body.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, fontSize = 14.sp)
            }
            if (primary != null) {
                Spacer(Modifier.height(18.dp))
                if (isAurora) AccentButton(primary.first, primary.second)
                else FilledTonalButton(onClick = primary.second) { Text(primary.first) }
            }
            if (secondary != null) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = secondary.second) { Text(secondary.first) }
            }
        }
    }
}

/** A bottom sheet in either look (Aurora: frosted glass, blur behind on Android 12+). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DocSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    if (isAurora) {
        val g = LocalGlass.current
        val blur = rememberWindowBlurAvailable()
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = state,
            shape = Glass.Sheet,
            containerColor = if (blur) g.dialogOverBlur else g.sheet,
            tonalElevation = 0.dp,
            scrimColor = Color.Black.copy(alpha = if (blur) 0.22f else 0.42f),
        ) {
            WindowBlurBehind(enabled = blur, radius = 24.dp, dimAmount = 0f)
            Column(Modifier.navigationBarsPadding(), content = content)
        }
    } else {
        ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
            Column(Modifier.navigationBarsPadding(), content = content)
        }
    }
}

/** One row of a sheet list (a contents entry), indented by [depth]. */
@Composable
internal fun SheetRow(text: String, depth: Int, selected: Boolean, onClick: () -> Unit) {
    Text(
        text,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        fontSize = if (depth == 0) 15.sp else 14.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = (20 + depth * 18).dp, end = 20.dp, top = 11.dp, bottom = 11.dp),
    )
}

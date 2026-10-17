package com.the412banner.bfe.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import com.the412banner.bfe.ui.theme.LocalGlass
import com.the412banner.bfe.ui.theme.isAurora

// Shared outlined-card look for the app's popup menus, matching the FileManager / CommunityCard
// idiom (surfaceContainer fill, 1dp outline, rounded 10dp). Applied to a DropdownMenu's modifier so
// the popup reads as an outlined card. Keep all three menu call-sites on this so they stay identical.
//
// Aurora Glass: a no-op — BfeDropdownMenu draws the glass panel itself.
@Composable
internal fun Modifier.outlinedMenuCard(): Modifier {
    if (isAurora) return this
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    return this
        .clip(shape)
        .background(cs.surfaceContainer)
        .border(1.dp, cs.outline, shape)
}

// The thin low-alpha separator that sits between items inside an outlined menu card.
//
// Aurora Glass: an inset hairline in the glass border colour.
@Composable
internal fun MenuItemDivider() {
    if (isAurora) {
        HorizontalDivider(color = LocalGlass.current.border, modifier = Modifier.padding(horizontal = 12.dp))
        return
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
}

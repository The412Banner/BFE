package com.the412banner.bfe.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.the412banner.bfe.ui.theme.AuroraBackdrop
import com.the412banner.bfe.ui.theme.LocalGlass
import com.the412banner.bfe.ui.theme.isAurora

/*
 * Look-aware building blocks for the secondary screens (categories, Apps, Cloud / Remote, network
 * server, Settings, Unpack, viewers). Each takes the Classic value and returns it untouched in Classic,
 * so a screen keeps one code path; in Aurora Glass they turn app bars and page grounds clear (the
 * backdrop shows through), cards and bars into glass, and full-screen overlays into backdrop-filled
 * pages that still cover what is underneath.
 */

/** App-bar colours: Classic's as given; Aurora's clear over the backdrop. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun bfeTopBarColors(classic: TopAppBarColors): TopAppBarColors =
    if (isAurora) TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent)
    else classic

/** A page / Scaffold ground: Classic's colour; clear in Aurora (the backdrop is the ground). */
@Composable
fun bfeScreenColor(classic: Color): Color = if (isAurora) Color.Transparent else classic

/** Card colours: Classic's as given; Aurora's resting glass (the accent wash when [selected]). */
@Composable
fun bfeCardColors(classic: CardColors, selected: Boolean = false): CardColors =
    if (isAurora) CardDefaults.cardColors(containerColor = if (selected) LocalGlass.current.selection else LocalGlass.current.panel) else classic

/** Card rim: Classic's as given; Aurora's glass hairline (an accent rim when [selected]). */
@Composable
fun bfeCardBorder(classic: BorderStroke?, selected: Boolean = false): BorderStroke? =
    if (isAurora) BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else LocalGlass.current.border) else classic

/** A bar / strip behind controls: Classic's solid colour; Aurora's glass. */
@Composable
fun Modifier.bfeBarBackground(classic: Color): Modifier =
    if (isAurora) background(LocalGlass.current.panel) else background(classic)

/**
 * A full-screen page that covers whatever is under it (folder pickers over a screen, a separate
 * activity's root). Classic: the plain page colour. Aurora: the living backdrop, so glass content on it
 * reads the same as everywhere else.
 */
@Composable
fun BfePage(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        // One structure for both looks, so the content keeps its state whichever look draws it.
        Box(Modifier.fillMaxSize()) {
            if (isAurora) AuroraBackdrop(Modifier.fillMaxSize())
            content()
        }
    }
}

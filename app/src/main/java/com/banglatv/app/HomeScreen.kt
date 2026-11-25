@file:OptIn(ExperimentalFoundationApi::class)

package com.banglatv.app

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage

@Composable
fun HomeScreen(vm: MainViewModel, gridState: LazyGridState) {
    val ctx = LocalContext.current
    val tv = remember { ctx.isTv() }
    var showSettings by remember { mutableStateOf(false) }

    LaunchedEffect(vm.message) {
        vm.message?.let {
            Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show()
            vm.message = null
        }
    }

    val groups = remember(vm.channels) {
        listOf("All", "Favorites") + vm.channels.map { it.group }.distinct()
    }
    val shown = remember(vm.channels, vm.query, vm.selectedGroup, vm.favorites) {
        vm.channels.filter { c ->
            val g = when (vm.selectedGroup) {
                "All" -> true
                "Favorites" -> c.key in vm.favorites
                else -> c.group == vm.selectedGroup
            }
            g && (vm.query.isBlank() || c.name.contains(vm.query.trim(), ignoreCase = true))
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BgTop, Bg), endY = 900f))
            .systemBarsPadding(),
    ) {
        // Header
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.sa_logo),
                contentDescription = "SA TV",
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp)),
            )
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text("SA TV", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFF5B301))
                Text(
                    "${vm.channels.size} channels  •  Ideas. Stories. Impact.",
                    fontSize = 12.sp,
                    color = TextDim,
                )
            }
            FocusButton(onClick = { vm.refresh() }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = Color.White)
            }
            Spacer(Modifier.size(8.dp))
            FocusButton(onClick = { showSettings = true }) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Color.White)
            }
        }
        if (vm.loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = Cyan, trackColor = Color.Transparent)
        }

        // Search
        OutlinedTextField(
            value = vm.query,
            onValueChange = { vm.query = it },
            singleLine = true,
            placeholder = { Text("Search channels", color = TextDim) },
            leadingIcon = { Icon(Icons.Filled.Search, null, tint = TextDim) },
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Surface1,
                unfocusedContainerColor = Surface1,
                focusedBorderColor = Cyan,
                unfocusedBorderColor = Color.Transparent,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = Cyan,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
        )

        // Category chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(groups, key = { it }) { g ->
                FocusButton(
                    onClick = { vm.selectedGroup = g },
                    selected = vm.selectedGroup == g,
                    shape = RoundedCornerShape(50),
                ) {
                    if (g == "Favorites") Icon(Icons.Filled.Favorite, null, tint = Pink, modifier = Modifier.size(16.dp))
                    Text(g, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (vm.selectedGroup == "Favorites") "No favorites yet — long-press a channel to add one."
                    else "No channels found",
                    color = TextDim,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(if (tv) 180.dp else 150.dp),
                state = gridState,
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(shown, key = { it.key }) { ch ->
                    ChannelCard(
                        ch = ch,
                        fav = ch.key in vm.favorites,
                        onClick = {
                            vm.queue = shown
                            vm.nowPlaying = ch
                        },
                        onLongClick = { vm.toggleFavorite(ch) },
                    )
                }
            }
        }
    }

    if (showSettings) {
        SettingsDialog(vm, onDismiss = { showSettings = false })
    }
}

@Composable
fun ChannelCard(ch: Channel, fav: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.07f else 1f, label = "card")
    val shape = RoundedCornerShape(18.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1.3f)
            .scale(scale)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    if (focused) listOf(AccentDark, SurfaceHi) else listOf(SurfaceHi, Surface1),
                ),
            )
            .then(if (focused) Modifier.border(3.dp, Cyan, shape) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                if (ch.logo != null) {
                    SubcomposeAsyncImage(
                        model = ch.logo,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                        loading = { Initial(ch.name) },
                        error = { Initial(ch.name) },
                    )
                } else {
                    Initial(ch.name)
                }
                Spacer(Modifier.weight(1f))
                if (fav) Icon(Icons.Filled.Favorite, "Favorite", tint = Pink, modifier = Modifier.size(20.dp))
            }
            Column {
                Text(
                    ch.name,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (ch.urls.size > 1) "${ch.group}  •  ${ch.urls.size} sources" else ch.group,
                    color = TextDim,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SettingsDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    var url by remember { mutableStateOf(vm.playlistUrl) }
    var include by remember { mutableStateOf(vm.includeOnline) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface1,
        title = { Text("Settings", color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Add online playlist (iptv-org Bengali)", color = Color.White, modifier = Modifier.weight(1f))
                    Switch(checked = include, onCheckedChange = { include = it })
                }
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Playlist URL (.m3u)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Built-in Indian Bengali channels are always shown. Channels with several links switch automatically when one fails.",
                    color = TextDim,
                    fontSize = 12.sp,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                vm.saveSettings(url, include)
                onDismiss()
            }) { Text("Save", color = Cyan) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { url = DEFAULT_PLAYLIST_URL }) { Text("Reset URL", color = TextDim) }
                TextButton(onClick = onDismiss) { Text("Cancel", color = TextDim) }
            }
        },
    )
}

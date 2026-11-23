package dev.catzeyea.thorbro.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.max
import dev.catzeyea.thorbro.android.ui.components.ButtonKind
import dev.catzeyea.thorbro.android.ui.components.ThorButton
import dev.catzeyea.thorbro.android.ui.components.controllerClickable
import dev.catzeyea.thorbro.android.ui.components.focusRing
import dev.catzeyea.thorbro.android.ui.components.panel
import dev.catzeyea.thorbro.android.ui.theme.ThorColors
import dev.catzeyea.thorbro.android.ui.theme.ThorDimens
import dev.catzeyea.thorbro.android.ui.theme.ThorShapes
import dev.catzeyea.thorbro.android.ui.theme.ThorType
import dev.catzeyea.thorbro.android.ui.theme.du

/**
 * Games tab: the game list on the left, the selected game with "Play on Thor" on the right.
 * Moving focus through the list selects a game; A on a game (or the Play button) starts it.
 * A touch tap on a game only selects it, so a stray tap does not start a stream.
 */
@Composable
fun GamesScreen(state: AppUiState, entry: FocusRequester, actions: AppActions, onOpenSetup: () -> Unit) {
    when (val content = StreamModel.games(state.storedProfile)) {
        GamesContent.NoProfile -> EmptyState(
            title = "No PC profile yet. Open Setup.",
            text = "The game list comes from ThorBro on your PC. Follow the steps on the Setup tab.",
            entry = entry,
            onOpenSetup = onOpenSetup,
        )
        is GamesContent.NoGames -> EmptyState(
            title = "No games from ${content.pcName}.",
            text = "Add the games in Sunshine on the PC, then press Send to Thor app in ThorBro again.",
            entry = entry,
            onOpenSetup = onOpenSetup,
        )
        is GamesContent.Games -> GameList(content, state.moonlight.installed, entry, actions)
    }
}

@Composable
private fun GameList(content: GamesContent.Games, moonlightInstalled: Boolean, entry: FocusRequester, actions: AppActions) {
    // A new game list (new profile) starts at the first game again.
    var selected by remember(content.games) { mutableIntStateOf(0) }
    val selectedIndex = selected.coerceIn(0, content.games.lastIndex)
    val selectedGame = content.games[selectedIndex]

    Row(Modifier.fillMaxSize().padding(bottom = 6.du), horizontalArrangement = Arrangement.spacedBy(28.du)) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .panel(ThorShapes.GamesList, shadow = ThorShapes.PcCardShadow)
                .padding(start = 20.du, end = 20.du, top = 20.du, bottom = 16.du),
        ) {
            Text("games from your pc", style = ThorType.label(), modifier = Modifier.padding(start = 10.du, bottom = 6.du))
            // A plain scrolling Column (not LazyColumn): every row stays composed, so the focus requesters
            // used below always point at a real row. Game lists from Sunshine are short.
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = ThorDimens.ScrollBleed),
                verticalArrangement = Arrangement.spacedBy(8.du),
            ) {
                content.games.forEachIndexed { index, game ->
                    GameRow(
                        name = game,
                        selected = index == selectedIndex,
                        onFocused = { selected = index },
                        onPlay = { actions.onLaunchGame(game) },
                        modifier = if (index == selectedIndex) Modifier.focusRequester(entry) else Modifier,
                    )
                }
            }
        }

        Column(
            Modifier
                .width(440.du)
                .fillMaxHeight()
                .panel(
                    ThorShapes.SelectedGameCard,
                    background = ThorColors.PanelSelected,
                    border = ThorColors.Ink,
                    shadow = ThorShapes.MainCardShadow,
                )
                .verticalScroll(rememberScrollState())
                .padding(28.du),
            verticalArrangement = Arrangement.spacedBy(14.du),
        ) {
            Text("selected", style = ThorType.label())
            Text(selectedGame, style = ThorType.bigTitle())
            Text("Starts on ${content.pcName} through Moonlight.", style = ThorType.body())
            if (!moonlightInstalled) {
                Text(
                    "Moonlight is not installed on this Thor. Download links: About tab.",
                    style = ThorType.style(14, FontWeight.SemiBold, ThorColors.Danger, lineHeight = 1.4f),
                )
            }
            ThorButton(
                "Play on Thor",
                { actions.onLaunchGame(selectedGame) },
                Modifier
                    .padding(top = 14.du)
                    // D-pad Left goes back to the selected game, not to whichever row is next to the button.
                    .focusProperties { left = entry },
                kind = ButtonKind.Main,
                fontSize = 22,
            )
            Spacer(Modifier.weight(1f, fill = false).heightIn(min = 14.du))
            Text("Missing a game? Add it on the PC, then press Send to Thor app in ThorBro.", style = ThorType.small())
        }
    }
}

@Composable
private fun GameRow(
    name: String,
    selected: Boolean,
    onFocused: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = ThorShapes.GameRow.shape()
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = max(58.du, ThorDimens.MinTarget))
            .focusRing(focused, shape)
            .background(if (selected) ThorColors.Sage else Color.Transparent, shape)
            .border(ThorDimens.Outline, if (selected) ThorColors.Ink else Color.Transparent, shape)
            .onFocusChanged { if (it.isFocused) onFocused() }
            // A starts the game; a tap only focuses (and so selects) the row.
            .controllerClickable(interaction, onActivate = onPlay, onTap = {})
            .padding(horizontal = 20.du),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            name,
            style = ThorType.style(
                20,
                if (selected) FontWeight.Bold else FontWeight.Medium,
                if (selected) ThorColors.OnAccent else ThorColors.Ink,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Shown when there is no profile or no games: a short text and a button to the Setup tab. */
@Composable
fun EmptyState(title: String, text: String, entry: FocusRequester, onOpenSetup: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .widthIn(max = 720.du)
                .panel(ThorShapes.PcCard, shadow = ThorShapes.PcCardShadow)
                .padding(horizontal = 32.du, vertical = 28.du),
            verticalArrangement = Arrangement.spacedBy(14.du),
        ) {
            Text(title, style = ThorType.cardTitle())
            Text(text, style = ThorType.body())
            ThorButton("Open Setup", onOpenSetup, Modifier.padding(top = 6.du).focusRequester(entry))
        }
    }
}

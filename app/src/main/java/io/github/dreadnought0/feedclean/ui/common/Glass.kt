package io.github.dreadnought0.feedclean.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.dreadnought0.feedclean.R
import io.github.dreadnought0.feedclean.armed.ArmedLevel
import io.github.dreadnought0.feedclean.ui.icons.FeedCleanIcons
import io.github.dreadnought0.feedclean.ui.theme.Glass

// ── Surfaces ─────────────────────────────────────────────────────────────────────────────────────────────────

/** Card look: normal frosted glass, amber ("Pause everything" while on), or a red outline (Setup when accessibility is missing). */
enum class CardTone { NORMAL, AMBER, DESTRUCTIVE }

val GlassCardShape = RoundedCornerShape(22.dp)
private val NestShape = RoundedCornerShape(16.dp)

/** A frosted glass card: translucent white, white hairline border, 22 dp corners. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    tone: CardTone = CardTone.NORMAL,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    spacing: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val (fill, border) = when (tone) {
        CardTone.NORMAL -> Glass.Card to Glass.Border
        CardTone.AMBER -> Glass.AmberWash to Glass.AmberBorderSoft
        CardTone.DESTRUCTIVE -> Glass.Card to Glass.RedBorder
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = GlassCardShape,
        color = fill,
        contentColor = Glass.Ink,
        border = BorderStroke(1.dp, border),
    ) {
        Column(
            modifier = (if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(spacing),
            content = content,
        )
    }
}

/** A lighter panel nested inside a [GlassCard] (e.g. the skip choices inside Auto-skip). */
@Composable
fun GlassNest(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(NestShape)
            .background(Glass.Nest)
            .border(1.dp, Glass.Border, NestShape)
            .padding(contentPadding),
        content = content,
    )
}

/** Thin white divider between rows inside a card. */
@Composable
fun GlassDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier, thickness = 1.dp, color = Glass.Divider)
}

/** A small rounded square holding a card's icon. */
@Composable
fun IconTile(icon: ImageVector, modifier: Modifier = Modifier, amber: Boolean = false, size: Dp = 32.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(if (amber) Glass.AmberFill else Glass.CardStrong)
            .border(1.dp, if (amber) Glass.AmberBorderSoft else Glass.Border, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (amber) Glass.AmberInk else Glass.Teal, modifier = Modifier.size(size * 0.56f))
    }
}

// ── Screen scaffold ─────────────────────────────────────────────────────────────────────────────────────────

/**
 * A scrolling screen of glass cards over the gradient: transparent top bar (title, optional back button and
 * actions) and a 16 dp padded, 12 dp spaced column.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassScreen(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    spacing: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        topBar = { FeedCleanTopAppBar(title = title, onBack = onBack, actions = actions, scrollBehavior = scrollBehavior) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 4.dp, bottom = 24.dp)
                .then(if (onBack != null) Modifier.navigationBarsPadding() else Modifier),
            verticalArrangement = Arrangement.spacedBy(spacing),
            content = content,
        )
    }
}

/** Muted footnote under a card. */
@Composable
fun SectionNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = Glass.Muted,
        modifier = modifier.padding(horizontal = 4.dp),
    )
}

/** Small uppercase-ish label above a group inside a card ("COVER STYLE"). */
@Composable
fun GroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = Glass.Muted,
        modifier = modifier.padding(top = 8.dp, bottom = 6.dp),
    )
}

/** Card title row: icon tile, title, then an optional pill and trailing control (switch). */
@Composable
fun CardHeader(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    amber: Boolean = false,
    pill: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 36.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (icon != null) IconTile(icon, amber = amber)
        Text(
            title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium, fontSize = MaterialTheme.typography.titleLarge.fontSize * 0.9f),
            color = if (amber) Glass.AmberInk else Glass.Ink,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        pill?.invoke()
        trailing?.invoke()
    }
}

/** Body paragraph inside a card. */
@Composable
fun CardBody(text: String, modifier: Modifier = Modifier, color: Color = Glass.Muted) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = color, modifier = modifier.padding(top = 6.dp))
}

// ── Pills ───────────────────────────────────────────────────────────────────────────────────────────────────

/** Neutral / accent / amber / slate tag look. */
enum class PillStyle { NEUTRAL, ACCENT, AMBER, SLATE }

/** A small rounded tag ("Coming soon", "Built-in", "Done", "Needs attention"). */
@Composable
fun TagPill(text: String, modifier: Modifier = Modifier, style: PillStyle = PillStyle.NEUTRAL, icon: ImageVector? = null) {
    val (fill, border, ink) = when (style) {
        PillStyle.NEUTRAL -> Triple(Glass.CardStrong, Glass.TrackBorder, Glass.Muted)
        PillStyle.ACCENT -> Triple(Glass.Accent, Glass.Accent, Color.White)
        PillStyle.AMBER -> Triple(Glass.AmberFill, Glass.AmberBorder, Glass.AmberText)
        PillStyle.SLATE -> Triple(Glass.CardStrong, Glass.Slate, Glass.SlateInk)
    }
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(fill)
            .border(1.dp, border, CircleShape)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(11.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = ink, maxLines = 1)
    }
}

/** The Armed / Off / Blocked / Paused badge. */
@Composable
fun ArmedPill(level: ArmedLevel, modifier: Modifier = Modifier) {
    when (level) {
        ArmedLevel.ARMED -> TagPill(stringResource(R.string.armed_level_armed), modifier, PillStyle.ACCENT, Icons.Filled.Check)
        ArmedLevel.OFF -> TagPill(stringResource(R.string.armed_level_off), modifier, PillStyle.NEUTRAL)
        ArmedLevel.BLOCKED -> TagPill(stringResource(R.string.armed_level_blocked), modifier, PillStyle.AMBER, Icons.Filled.Warning)
        ArmedLevel.PAUSED -> TagPill(stringResource(R.string.armed_level_paused), modifier, PillStyle.SLATE, FeedCleanIcons.Pause)
    }
}

// ── Controls ────────────────────────────────────────────────────────────────────────────────────────────────

/** The glass switch: teal when on (amber for "Pause everything"), frosted white track when off. */
@Composable
fun GlassSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    amber: Boolean = false,
) {
    val on = if (amber) Glass.AmberStrong else Glass.Accent
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = on,
            checkedBorderColor = on,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = Glass.Track,
            uncheckedBorderColor = Glass.TrackBorder,
            disabledUncheckedTrackColor = Glass.Track.copy(alpha = 0.4f),
            disabledUncheckedThumbColor = Color.White.copy(alpha = 0.7f),
            disabledUncheckedBorderColor = Glass.TrackBorder.copy(alpha = 0.4f),
            disabledCheckedTrackColor = on.copy(alpha = 0.4f),
            disabledCheckedThumbColor = Color.White,
        ),
    )
}

/** ⓘ button that opens a short explanation. */
@Composable
fun InfoButton(title: String, text: String, modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(22.dp)
            .clip(CircleShape)
            .clickable(onClickLabel = stringResource(R.string.home_info_cd, title)) { open = true },
        contentAlignment = Alignment.Center,
    ) {
        Icon(FeedCleanIcons.InfoOutline, contentDescription = stringResource(R.string.home_info_cd, title), tint = Glass.Faint, modifier = Modifier.size(15.dp))
    }
    if (open) {
        GlassDialog(
            title = title,
            text = text,
            confirm = stringResource(R.string.info_ok),
            onConfirm = { open = false },
            onDismiss = { open = false },
        )
    }
}

/**
 * One option row: title (+ pill + ⓘ), a one-line description, and a trailing switch or chevron. Tapping the row
 * toggles the switch (or runs [onClick]).
 */
@Composable
fun OptionRow(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    level: ArmedLevel? = null,
    info: String? = null,
    checked: Boolean? = null,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
    titleColor: Color = Glass.Ink,
    descriptionColor: Color = Glass.Muted,
    onLevelClick: (() -> Unit)? = null,
) {
    val rowModifier = when {
        checked != null && onCheckedChange != null ->
            Modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
        onClick != null -> Modifier.clickable(enabled = enabled, onClick = onClick)
        else -> Modifier
    }
    Row(
        modifier = modifier.fillMaxWidth().then(rowModifier).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (enabled) titleColor else Glass.Faint,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (level != null) {
                    ArmedPill(level, if (onLevelClick != null) Modifier.clip(CircleShape).clickable(onClick = onLevelClick) else Modifier)
                }
                if (info != null) InfoButton(title, info)
            }
            if (description != null) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) descriptionColor else Glass.Faint,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        when {
            trailing != null -> trailing()
            checked != null -> GlassSwitch(checked = checked, onCheckedChange = null, enabled = enabled)
            onClick != null -> Chevron()
        }
    }
}

@Composable
fun Chevron(modifier: Modifier = Modifier, tint: Color = Glass.Faint) {
    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = tint, modifier = modifier.size(20.dp))
}

/** Link row: accent title on the left, a muted value and a chevron on the right. */
@Composable
fun LinkRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    value: String? = null,
    subtitle: String? = null,
    icon: ImageVector? = null,
    accent: Boolean = true,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) IconTile(icon, size = 36.dp)
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = if (accent) FontWeight.SemiBold else FontWeight.Medium),
                color = when {
                    !enabled -> Glass.Faint
                    accent -> Glass.Accent
                    else -> Glass.Ink
                },
            )
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Glass.Muted, modifier = Modifier.padding(top = 1.dp))
            }
        }
        if (value != null) {
            Text(value, style = MaterialTheme.typography.bodySmall, color = Glass.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Chevron()
    }
}

/** The amber (Blocked) or slate (Paused) bar under a row: reason on the left, a fix link on the right. */
@Composable
fun WarnBar(
    text: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    paused: Boolean = false,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (paused) Glass.CardStrong else Glass.AmberWash)
            .border(1.dp, if (paused) Glass.Slate.copy(alpha = 0.5f) else Glass.AmberBorderSoft, shape)
            .then(if (onAction != null) Modifier.clickable(onClick = onAction) else Modifier)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            if (paused) FeedCleanIcons.Pause else Icons.Filled.Warning,
            contentDescription = null,
            tint = if (paused) Glass.SlateInk else Glass.AmberStrong,
            modifier = Modifier.size(15.dp),
        )
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = if (paused) Glass.SlateInk else Glass.AmberText,
            modifier = Modifier.weight(1f),
        )
        if (action != null) {
            Text("$action ›", style = MaterialTheme.typography.labelMedium, color = Glass.Accent)
        }
    }
}

/** Teal filled primary button (full width by default). */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 50.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = Glass.Accent, contentColor = Color.White),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Frosted secondary button; [destructive] makes it red. */
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    destructive: Boolean = false,
    enabled: Boolean = true,
) {
    val ink = if (destructive) Glass.Red else Glass.Ink
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 46.dp),
        shape = CircleShape,
        border = BorderStroke(1.dp, if (destructive) Glass.RedBorder else Glass.TrackBorder),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Glass.CardStrong, contentColor = ink),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** A selectable card-like radio option (reason picker, block mode). */
@Composable
fun RadioOption(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) Glass.AccentWash else Glass.Nest)
            .border(1.dp, if (selected) Glass.Accent.copy(alpha = 0.35f) else Glass.Border, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            modifier = Modifier.padding(horizontal = 6.dp),
            colors = RadioButtonDefaults.colors(selectedColor = Glass.Accent, unselectedColor = Glass.Faint),
        )
        Column(Modifier.weight(1f).padding(vertical = 4.dp, horizontal = 4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Glass.Ink)
            if (description != null) Text(description, style = MaterialTheme.typography.bodySmall, color = Glass.Muted)
        }
    }
}

/** Segmented picker ("Blank | Blur | Labelled"). */
@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(Glass.Nest)
            .border(1.dp, Glass.Border, CircleShape)
            .padding(3.dp),
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(if (on) Glass.Accent else Color.Transparent)
                    .selectable(selected = on, role = Role.Tab, onClick = { onSelect(value) })
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = if (on) Color.White else Glass.Muted)
            }
        }
    }
}

/** − value + stepper. */
@Composable
fun Stepper(
    value: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier,
    canDecrease: Boolean = true,
    canIncrease: Boolean = true,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StepButton(FeedCleanIcons.Remove, stringResource(R.string.tim_decrease), onDecrease, canDecrease)
        Text(value, style = MaterialTheme.typography.titleSmall, color = Glass.Ink, modifier = Modifier.width(44.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        StepButton(FeedCleanIcons.Add, stringResource(R.string.tim_increase), onIncrease, canIncrease)
    }
}

@Composable
private fun StepButton(icon: ImageVector, label: String, onClick: () -> Unit, enabled: Boolean) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = Glass.CardStrong,
        contentColor = if (enabled) Glass.Ink else Glass.Faint,
        border = BorderStroke(1.dp, Glass.TrackBorder),
        modifier = Modifier.size(32.dp),
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = label, modifier = Modifier.size(16.dp)) }
    }
}

/** A value chip ("0 ms", "60", "Built-in"). */
@Composable
fun ValueChip(text: String, modifier: Modifier = Modifier) = TagPill(text, modifier, PillStyle.NEUTRAL)

// ── Dialogs ─────────────────────────────────────────────────────────────────────────────────────────────────

/** Frosted dialog: title, body, a teal primary button and an optional frosted secondary button. */
@Composable
fun GlassDialog(
    title: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    text: String? = null,
    dismiss: String? = null,
    destructive: Boolean = false,
    body: (@Composable ColumnScope.() -> Unit)? = null,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = Glass.Sheet.copy(alpha = 0.97f),
            contentColor = Glass.Ink,
            border = BorderStroke(1.dp, Color.White),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                if (text != null) Text(text, style = MaterialTheme.typography.bodyMedium, color = Glass.Muted)
                body?.invoke(this)
                Spacer(Modifier.height(4.dp))
                if (destructive) {
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = Glass.Red, contentColor = Color.White),
                    ) { Text(confirm, style = MaterialTheme.typography.labelLarge) }
                } else {
                    PrimaryButton(confirm, onConfirm)
                }
                if (dismiss != null) GlassButton(dismiss, onDismiss)
            }
        }
    }
}

/** Plain text button in accent color. */
@Composable
fun AccentTextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier) { Text(text, color = Glass.Accent, style = MaterialTheme.typography.labelLarge) }
}

/** A ✓ / ✗ / neutral check line used by the fix sheet and the guide. */
@Composable
fun CheckLine(text: String, met: Boolean?, modifier: Modifier = Modifier, emphasize: Boolean = false) {
    Row(modifier = modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val (bg, glyph) = when (met) {
            true -> Glass.Accent to Icons.Filled.Check
            false -> Glass.AmberStrong to FeedCleanIcons.Close
            null -> Glass.Slate to FeedCleanIcons.Pause
        }
        Box(Modifier.size(22.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
            Icon(glyph, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = if (emphasize) FontWeight.SemiBold else FontWeight.Normal),
            color = when (met) {
                false -> Glass.AmberText
                else -> Glass.Ink
            },
        )
    }
}

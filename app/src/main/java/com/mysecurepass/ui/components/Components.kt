package com.mysecurepass.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mysecurepass.core.strength.Rating
import com.mysecurepass.ui.theme.ExtraColors
import com.mysecurepass.ui.theme.MonoFamily
import com.mysecurepass.ui.theme.Msp

// ---- App-wide helpers ---------------------------------------------------------------------

/** Shows a short message. Provided by AppRoot. */
val LocalMessenger = staticCompositionLocalOf<(String) -> Unit> { {} }

/** Haptic tick respecting the user's setting. Provided by AppRoot. */
val LocalHapticsEnabled = staticCompositionLocalOf { true }

@Composable
fun rememberHaptic(): (HapticFeedbackType) -> Unit {
    val haptic = LocalHapticFeedback.current
    val enabled = LocalHapticsEnabled.current
    return remember(haptic, enabled) { { type -> if (enabled) haptic.performHapticFeedback(type) } }
}

// ---- Layout --------------------------------------------------------------------------------

/** Centered, max-width content column so tablets/foldables don't stretch lines across the screen. */
@Composable
fun ContentWidth(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), content = content)
    }
}

@Composable
fun ScreenTitle(title: String, subtitle: String? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 20.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        actions()
    }
}

/** Flat card with a hairline border: the main surface of the design. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(20.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    val base = modifier
        .fillMaxWidth()
        .clip(shape)
        .background(MaterialTheme.colorScheme.surfaceContainer)
        .border(1.dp, Msp.extra.hairline, shape)
    Column(
        (if (onClick != null) base.clickable(onClick = onClick) else base).padding(padding),
        content = content,
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier.padding(start = 28.dp, end = 24.dp, top = 24.dp, bottom = 10.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Msp.extra.hairline))
}

// ---- Password rendering ----------------------------------------------------------------------

fun colorize(value: String, extra: ExtraColors, base: Color): AnnotatedString = buildAnnotatedString {
    for (c in value) {
        val color = when {
            c.isDigit() -> extra.digit
            c.isLetter() -> base
            c == ' ' -> base
            else -> extra.symbol
        }
        withStyle(SpanStyle(color = color)) { append(c) }
    }
}

@Composable
fun SecretText(
    value: String,
    visible: Boolean,
    style: TextStyle,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
    maxLines: Int = Int.MAX_VALUE,
) {
    val extra = Msp.extra
    val base = MaterialTheme.colorScheme.onSurface
    val text = remember(value, visible, extra, base) {
        if (visible) colorize(value, extra, base) else AnnotatedString("•".repeat(value.length.coerceAtMost(24)))
    }
    Text(
        text, modifier = modifier.semantics { contentDescription = if (visible) value else "Hidden secret" },
        style = style, textAlign = textAlign, maxLines = maxLines,
    )
}

// ---- Strength -----------------------------------------------------------------------------

@Composable
fun ratingColor(rating: Rating): Color {
    val e = Msp.extra
    return when (rating) {
        Rating.VERY_WEAK -> e.veryWeak
        Rating.WEAK -> e.weak
        Rating.FAIR -> e.fair
        Rating.STRONG -> e.strong
        Rating.EXCELLENT -> e.excellent
    }
}

/** Five-segment meter with a spring animation. */
@Composable
fun StrengthMeter(rating: Rating, modifier: Modifier = Modifier, fraction: Float = (rating.ordinal + 1) / 5f) {
    val color by animateColorAsState(ratingColor(rating), label = "strengthColor")
    val animated by animateFloatAsState(fraction.coerceIn(0.04f, 1f), spring(stiffness = Spring.StiffnessMediumLow), label = "strength")
    Row(modifier.fillMaxWidth().height(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(5) { i ->
            val segStart = i / 5f
            val fill = ((animated - segStart) * 5f).coerceIn(0f, 1f)
            Box(Modifier.weight(1f).fillMaxHeight().clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(fill).clip(CircleShape).background(color))
            }
        }
    }
}

// ---- Controls -----------------------------------------------------------------------------

/** Pill segmented control with a sliding indicator. */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = rememberHaptic()
    val index = options.indexOf(selected).coerceAtLeast(0)
    BoxWithConstraints(
        modifier.fillMaxWidth().height(44.dp).clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(4.dp),
    ) {
        val segment: Dp = maxWidth / options.size
        val offset by animateDpAsState(segment * index, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow), label = "seg")
        Box(
            Modifier.offset(x = offset).width(segment).fillMaxHeight().clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .border(1.dp, Msp.extra.hairline, CircleShape),
        )
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, option ->
                val isSelected = i == index
                val textColor by animateColorAsState(
                    if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, label = "segText",
                )
                Box(
                    Modifier.weight(1f).fillMaxHeight().clip(CircleShape)
                        .clickable(role = Role.Tab) {
                            if (!isSelected) { haptic(HapticFeedbackType.TextHandleMove); onSelect(option) }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label(option), style = MaterialTheme.typography.labelLarge, color = textColor, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun ToggleRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    subtitle: String? = null,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val haptic = rememberHaptic()
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
            .clickable(enabled = enabled, role = Role.Switch) { haptic(HapticFeedbackType.TextHandleMove); onChange(!checked) }
            .padding(vertical = if (icon != null) 12.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { IconTile(icon); Spacer(Modifier.width(14.dp)) }
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked, onCheckedChange = null, enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                uncheckedBorderColor = Color.Transparent,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
    }
}

@Composable
fun SliderRow(
    label: String,
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
    valueLabel: String = value.toString(),
) {
    val haptic = rememberHaptic()
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(valueLabel, style = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFamily), color = MaterialTheme.colorScheme.onSurface)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = {
                val v = it.toInt()
                if (v != value) { haptic(HapticFeedbackType.TextHandleMove); onChange(v) }
            },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.onSurface,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
        )
    }
}

/** Large pill button with a press-scale micro-interaction. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    filled: Boolean = true,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(stiffness = Spring.StiffnessHigh), label = "press")
    val bg = when {
        !enabled -> MaterialTheme.colorScheme.surfaceContainerHighest
        destructive && filled -> MaterialTheme.colorScheme.error
        filled -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val fg = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
        destructive && filled -> MaterialTheme.colorScheme.onError
        destructive -> MaterialTheme.colorScheme.error
        filled -> MaterialTheme.colorScheme.surface
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier.scale(scale).height(54.dp).clip(CircleShape).background(bg)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
    }
}

@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 54.dp,
    rotation: Float = 0f,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, spring(stiffness = Spring.StiffnessHigh), label = "press")
    Box(
        modifier.scale(scale).size(size).clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, Msp.extra.hairline, CircleShape)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon, contentDescription, tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp).rotate(rotation),
        )
    }
}


@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val bg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceContainerHigh, label = "chipBg",
    )
    val fg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface, label = "chipFg",
    )
    Box(
        modifier.height(36.dp).clip(CircleShape).background(bg).clickable(role = Role.RadioButton, onClick = onClick).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
    }
}

/** Password input that never offers autocorrect/suggestions and is masked by default. */
@Composable
fun SecretField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: () -> Unit = {},
    isError: Boolean = false,
    supporting: String? = null,
    initiallyVisible: Boolean = false,
    monospace: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    var visible by rememberSaveable { mutableStateOf(initiallyVisible) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        supportingText = supporting?.let { { Text(it) } },
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        textStyle = if (monospace) MaterialTheme.typography.bodyLarge.copy(fontFamily = MonoFamily) else MaterialTheme.typography.bodyLarge,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false, imeAction = imeAction),
        keyboardActions = KeyboardActions(onAny = { onImeAction() }),
        colors = fieldColors(),
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                trailing?.invoke()
                IconButton(onClick = { visible = !visible }) {
                    Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (visible) "Hide" else "Show")
                }
            }
        },
    )
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    unfocusedBorderColor = Msp.extra.hairline,
    focusedBorderColor = MaterialTheme.colorScheme.onSurface,
    focusedLabelColor = MaterialTheme.colorScheme.onSurface,
    cursorColor = MaterialTheme.colorScheme.primary,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
)

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(72.dp).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null) { Spacer(Modifier.height(24.dp)); action() }
    }
}

@Composable
fun Badge(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.14f), shape = CircleShape) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, color = color)
    }
}

// ---- Stage 2 components ---------------------------------------------------------------------

/** Shows a message with an action (e.g. Undo). Provided by AppRoot. */
val LocalActionMessenger = staticCompositionLocalOf<(message: String, action: String, onAction: () -> Unit) -> Unit> { { _, _, _ -> } }

/** Animated circular score (0–100) with a label in the middle. */
@Composable
fun ScoreRing(score: Int, color: Color, modifier: Modifier = Modifier, size: Dp = 120.dp, stroke: Dp = 10.dp, label: String? = null) {
    val animated by animateFloatAsState(score.coerceIn(0, 100) / 100f, spring(stiffness = Spring.StiffnessVeryLow), label = "ring")
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val s = stroke.toPx()
            val arc = androidx.compose.ui.geometry.Size(this.size.width - s, this.size.height - s)
            val topLeft = androidx.compose.ui.geometry.Offset(s / 2, s / 2)
            val style = androidx.compose.ui.graphics.drawscope.Stroke(width = s, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            drawArc(track, -90f, 360f, false, topLeft, arc, style = style)
            drawArc(color, -90f, 360f * animated, false, topLeft, arc, style = style)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${(animated * 100).toInt()}", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onSurface)
            if (label != null) Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

enum class Status { GOOD, WARN, BAD, INFO }

@Composable
fun statusColor(status: Status): Color = when (status) {
    Status.GOOD -> Msp.extra.strong
    Status.WARN -> Msp.extra.fair
    Status.BAD -> Msp.extra.veryWeak
    Status.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** A row with a coloured status dot/icon, title, detail and optional action. */
@Composable
fun StatusRow(
    icon: ImageVector,
    title: String,
    detail: String,
    status: Status,
    onClick: (() -> Unit)? = null,
    trailing: String? = null,
) {
    val color = statusColor(status)
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            Text(trailing, style = MaterialTheme.typography.labelLarge, color = color)
        }
        if (onClick != null) {
            Icon(androidx.compose.material.icons.Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

/** Compact top bar used by secondary screens. */
@Composable
fun SubScreenBar(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(androidx.compose.material.icons.Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(start = 4.dp), maxLines = 1)
        actions()
    }
}

/** Labelled row of single-choice chips (scrolls horizontally on narrow screens). */
@Composable
fun <T> OptionRow(title: String, subtitle: String?, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    val haptic = rememberHaptic()
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (value, label) ->
                Chip(label, value == selected, { if (value != selected) { haptic(HapticFeedbackType.TextHandleMove); onSelect(value) } })
            }
        }
    }
}

/** Icon tile used as a leading element in settings-style rows. */
@Composable
fun IconTile(icon: ImageVector, tint: Color = MaterialTheme.colorScheme.onSurface, size: Dp = 36.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.33f)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.5f)) }
}

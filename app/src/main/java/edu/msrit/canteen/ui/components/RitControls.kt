package edu.msrit.canteen.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import edu.msrit.canteen.ui.theme.RitRadii
import edu.msrit.canteen.ui.theme.RitThemeTokens
import edu.msrit.canteen.ui.theme.RitType

@Composable
fun RitText(
    text: String,
    style: TextStyle = RitType.body,
    color: Color = RitThemeTokens.colors.foreground,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(color = color),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun RitPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trailingIcon: RitIconKind? = null,
) {
    val colors = RitThemeTokens.colors
    val shape = RoundedCornerShape(RitRadii.pill)
    Row(
        modifier = modifier
            .heightIn(min = 54.dp)
            .clip(shape)
            .background(if (enabled) colors.accent else colors.outline)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        RitText(text, RitType.label, if (enabled) colors.onAccent else colors.muted)
        if (trailingIcon != null) {
            Spacer(Modifier.width(10.dp))
            RitIcon(trailingIcon, if (enabled) colors.onAccent else colors.muted, size = 18.dp)
        }
    }
}

@Composable
fun RitSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: RitIconKind? = null,
    enabled: Boolean = true,
) {
    val colors = RitThemeTokens.colors
    val shape = RoundedCornerShape(RitRadii.pill)
    Row(
        modifier = modifier
            .heightIn(min = 54.dp)
            .clip(shape)
            .border(1.dp, colors.outline, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (leadingIcon != null) {
            RitIcon(leadingIcon, colors.foreground, size = 19.dp)
            Spacer(Modifier.width(10.dp))
        }
        RitText(text, RitType.label)
    }
}

@Composable
fun RitTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    inputTag: String? = null,
    singleLine: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingContent: @Composable (() -> Unit)? = null,
) {
    val colors = RitThemeTokens.colors
    val shape = RoundedCornerShape(RitRadii.card)
    Column(
        modifier = modifier
            .border(1.dp, colors.outline, shape)
            .clip(shape)
            .background(colors.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        RitText(label, RitType.small, colors.muted)
        Spacer(Modifier.height(5.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f).then(if (singleLine) Modifier.heightIn(min = 28.dp) else Modifier.heightIn(min = 84.dp))
                .then(if (inputTag == null) Modifier else Modifier.testTag(inputTag)),
            textStyle = RitType.body.copy(color = colors.foreground),
            singleLine = singleLine,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            visualTransformation = visualTransformation,
            cursorBrush = SolidColor(colors.accent),
            decorationBox = { inner ->
                Box(contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart) {
                    if (value.isEmpty()) RitText(placeholder, RitType.body, colors.muted)
                    inner()
                }
            },
        )
        trailingContent?.invoke()
        }
    }
}

@Composable
fun RitSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    hint: String = "Search for food, canteens or cuisines…",
    onFilterClick: (() -> Unit)? = null,
    onClearClick: (() -> Unit)? = null,
    inputModifier: Modifier = Modifier,
    onSubmit: (() -> Unit)? = null,
) {
    val colors = RitThemeTokens.colors
    val shape = RoundedCornerShape(RitRadii.pill)
    Row(
        modifier = modifier
            .height(56.dp)
            .clip(shape)
            .border(1.dp, colors.outline, shape)
            .background(colors.surface)
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RitIcon(RitIconKind.Search, colors.foreground)
        Spacer(Modifier.width(12.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f).then(inputModifier)
                .semantics { contentDescription = "Search preview catalog" }.testTag("search_input"),
            textStyle = RitType.body.copy(color = colors.foreground),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmit?.invoke() }),
            cursorBrush = SolidColor(colors.accent),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) RitText(hint, RitType.body, colors.muted, maxLines = 1)
                    inner()
                }
            },
        )
        if (value.isNotEmpty() && onClearClick != null) {
            Box(Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClearClick)
                .semantics { contentDescription = "Clear search" }.testTag("search_clear"), contentAlignment = Alignment.Center) {
                RitIcon(RitIconKind.Close, colors.muted, size = 18.dp)
            }
        }
        if (onFilterClick != null) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onFilterClick)
                    .semantics { contentDescription = "Filters" }.testTag("search_filters_toggle"),
                contentAlignment = Alignment.Center,
            ) { RitIcon(RitIconKind.Filter, colors.accent) }
        }
    }
}

@Composable
fun RitSearchEntry(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = RitThemeTokens.colors
    val shape = RoundedCornerShape(RitRadii.pill)
    Row(modifier.height(56.dp).clip(shape).border(1.dp, colors.outline, shape).background(colors.surface)
        .clickable(role = Role.Button, onClick = onClick).testTag("home_search_entry")
        .padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        RitIcon(RitIconKind.Search, colors.foreground)
        Spacer(Modifier.width(12.dp))
        RitText("Search food, categories or sellers…", RitType.body, colors.muted, Modifier.weight(1f), maxLines = 1)
        RitIcon(RitIconKind.Filter, colors.accent, size = 20.dp)
    }
}

@Composable
fun RitChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = RitThemeTokens.colors
    val shape = RoundedCornerShape(RitRadii.pill)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (selected) colors.accent else colors.warmCard)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 48.dp)
            .semantics { stateDescription = if (selected) "Selected" else "Not selected" }
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) { RitText(label, RitType.small, if (selected) colors.onAccent else colors.foreground) }
}

/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.vxs.frostsoulx.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarColors
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarDefaults.TonalElevation
import androidx.compose.material3.Surface
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp
import dev.vxs.frostsoulx.R
import dev.vxs.frostsoulx.constants.AppBarHeight
import dev.vxs.frostsoulx.ui.frostsoul.SearchTheme
import kotlin.math.max

@ExperimentalMaterial3Api
@Composable
fun TopSearch(
    query: TextFieldValue,
    onQueryChange: (TextFieldValue) -> Unit,
    onSearch: (String) -> Unit,
    active: Boolean,
    onActiveChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    shape: Shape = SearchBarDefaults.inputFieldShape,
    colors: SearchBarColors =
        SearchBarDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    tonalElevation: Dp = SearchBarDefaults.TonalElevation,
    windowInsets: WindowInsets = WindowInsets.systemBars,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    focusRequester: FocusRequester = remember { FocusRequester() },
    leftFocusRequester: FocusRequester? = null,
    onCloseFinished: () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val animationProgress: Float by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec =
            if (active) {
                spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                )
            } else {
                spring(
                    dampingRatio = 0.9f,
                    stiffness = Spring.StiffnessMedium,
                )
            },
        label = "SearchBarAnimation",
    )

    LaunchedEffect(animationProgress, active) {
        if (!active && animationProgress <= 0.001f) {
            onCloseFinished()
        }
    }

    val topInset = windowInsets.asPaddingValues().calculateTopPadding().coerceAtLeast(0.dp)
    val headerHeight = 56.dp
    val minBarHeight = topInset + headerHeight

    BoxWithConstraints(
        modifier = modifier.offset { IntOffset(x = 0, y = 0) },
        propagateMinConstraints = true,
    ) {
        val fullHeight = constraints.maxHeight.toFloat()
        val collapsedHeight = with(LocalDensity.current) { minBarHeight.roundToPx().toFloat() }
        val currentHeightDp = with(LocalDensity.current) {
            lerp(collapsedHeight, fullHeight, animationProgress.coerceIn(0f, 1f)).toDp()
        }

        Surface(
            color = colors.containerColor,
            contentColor = contentColorFor(colors.containerColor),
            tonalElevation = tonalElevation,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(currentHeightDp),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = topInset)
                            .height(headerHeight)
                            .padding(horizontal = 8.dp),
                ) {
                    if (leadingIcon != null) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(44.dp),
                        ) {
                            leadingIcon()
                        }
                        Spacer(Modifier.width(4.dp))
                    }

                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF212121),
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(46.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.search),
                                contentDescription = "Search",
                                tint = Color.White.copy(alpha = 0.65f),
                                modifier = Modifier.size(18.dp),
                            )

                            Spacer(Modifier.width(10.dp))

                            val coercedQuery = remember(query) { query.coerceTextRanges() }

                            BasicTextField(
                                value = coercedQuery,
                                onValueChange = { onQueryChange(it.coerceTextRanges()) },
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .focusRequester(focusRequester)
                                        .then(
                                            if (leftFocusRequester != null) {
                                                Modifier.focusProperties {
                                                    left = leftFocusRequester
                                                }
                                            } else {
                                                Modifier
                                            },
                                        ).pointerInput(Unit) {
                                            awaitEachGesture {
                                                awaitFirstDown(pass = PointerEventPass.Initial)
                                                val upEvent = waitForUpOrCancellation(pass = PointerEventPass.Initial)
                                                if (upEvent != null) {
                                                    onActiveChange(true)
                                                }
                                            }
                                        }.semantics {
                                            contentDescription = "Search"
                                            if (active) {
                                                stateDescription = "Suggestions available"
                                            }
                                        }.onKeyEvent {
                                            if (it.key == Key.Enter) {
                                                onSearch(coercedQuery.text)
                                                return@onKeyEvent true
                                            }
                                            false
                                        },
                                enabled = enabled,
                                singleLine = true,
                                textStyle =
                                    SearchTheme.InputTextStyle.copy(
                                        color = Color.White,
                                        fontSize = 15.sp,
                                    ),
                                cursorBrush = SolidColor(Color.White),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { onSearch(coercedQuery.text) }),
                                interactionSource = interactionSource,
                                decorationBox = { innerTextField ->
                                    Box(
                                        contentAlignment = Alignment.CenterStart,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        if (coercedQuery.text.isEmpty() && placeholder != null) {
                                            CompositionLocalProvider(
                                                LocalContentColor provides Color.White.copy(alpha = 0.5f),
                                            ) {
                                                placeholder()
                                            }
                                        }
                                        innerTextField()
                                    }
                                },
                            )

                            if (coercedQuery.text.isNotEmpty()) {
                                Spacer(Modifier.width(6.dp))
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier =
                                        Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.22f))
                                            .clickable {
                                                onQueryChange(TextFieldValue(""))
                                            },
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.close),
                                        contentDescription = "Clear",
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp),
                                    )
                                }
                            }
                        }
                    }

                    if (trailingIcon != null) {
                        Spacer(Modifier.width(4.dp))
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(44.dp),
                        ) {
                            trailingIcon()
                        }
                    }
                }

                val contentAlpha =
                    if (active) {
                        ((animationProgress - 0.15f) / 0.85f).coerceIn(0f, 1f)
                    } else {
                        ((animationProgress - 0.25f) / 0.75f).coerceIn(0f, 1f)
                    }

                if (animationProgress > 0.001f && contentAlpha > 0f) {
                    Column(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .alpha(contentAlpha)
                                .graphicsLayer {
                                    translationY = (1f - contentAlpha) * -20.dp.toPx()
                                },
                    ) {
                        HorizontalDivider(
                            color = colors.dividerColor.copy(alpha = contentAlpha * 0.15f),
                            thickness = 0.5.dp,
                        )
                        content()
                    }
                }
            }
        }
    }

    BackHandler(enabled = active) {
        onActiveChange(false)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBarInputField(
    query: TextFieldValue,
    onQueryChange: (TextFieldValue) -> Unit,
    onSearch: (String) -> Unit,
    active: Boolean,
    onActiveChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    colors: TextFieldColors,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    focusRequester: FocusRequester = remember { FocusRequester() },
    leftFocusRequester: FocusRequester? = null,
) {
    val coercedQuery = remember(query) { query.coerceTextRanges() }
    val focused = interactionSource.collectIsFocusedAsState().value
    val textColor =
        LocalTextStyle.current.color.takeOrElse {
            if (focused) colors.focusedTextColor else colors.unfocusedTextColor
        }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .height(InputFieldHeight),
    ) {
        if (leadingIcon != null) {
            Spacer(Modifier.width(SearchBarIconOffsetX))
            leadingIcon()
        }

        BasicTextField(
            value = coercedQuery,
            onValueChange = { onQueryChange(it.coerceTextRanges()) },
            modifier =
                Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .then(
                        if (leftFocusRequester != null) {
                            Modifier.focusProperties {
                                left = leftFocusRequester
                            }
                        } else {
                            Modifier
                        },
                    ).pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(pass = PointerEventPass.Initial)
                            val upEvent = waitForUpOrCancellation(pass = PointerEventPass.Initial)
                            if (upEvent != null) {
                                onActiveChange(true)
                            }
                        }
                    }.semantics {
                        contentDescription = "Search"
                        if (active) {
                            stateDescription = "Suggestions available"
                        }
                    }.onKeyEvent {
                        if (it.key == Key.Enter) {
                            onSearch(coercedQuery.text)
                            return@onKeyEvent true
                        }
                        false
                    },
            enabled = enabled,
            singleLine = true,
                    textStyle = SearchTheme.InputTextStyle.copy(color = textColor),
            cursorBrush = SolidColor(colors.cursorColor),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch(coercedQuery.text) }),
            interactionSource = interactionSource,
            decorationBox = @Composable { innerTextField ->
                TextFieldDefaults.DecorationBox(
                    value = coercedQuery.text,
                    innerTextField = innerTextField,
                    enabled = enabled,
                    singleLine = true,
                    visualTransformation = VisualTransformation.None,
                    interactionSource = interactionSource,
                    placeholder = placeholder,
                    shape = SearchBarDefaults.inputFieldShape,
                    colors = colors,
                    contentPadding = PaddingValues(),
                    container = {},
                )
            },
        )

        if (trailingIcon != null) {
            trailingIcon()
            Spacer(Modifier.width(SearchBarIconOffsetX))
        }
    }
}

private fun TextFieldValue.coerceTextRanges(): TextFieldValue {
    val textLength = text.length
    val coercedSelection = selection.coerceInText(textLength)
    val coercedComposition = composition?.coerceInText(textLength)
    return if (coercedSelection == selection && coercedComposition == composition) {
        this
    } else {
        copy(selection = coercedSelection, composition = coercedComposition)
    }
}

private fun TextRange.coerceInText(textLength: Int): TextRange =
    TextRange(
        start = minOf(start, end).coerceIn(0, textLength),
        end = maxOf(start, end).coerceIn(0, textLength),
    )

// Measurement specs
val InputFieldHeight = 48.dp
private val SearchBarCornerRadius: Dp = 12.dp
internal val SearchBarVerticalPadding: Dp = 8.dp
internal val SearchBarHorizontalPadding: Dp = 16.dp
val SearchBarIconOffsetX: Dp = 4.dp
private const val AnimationDurationMillis: Int = 300

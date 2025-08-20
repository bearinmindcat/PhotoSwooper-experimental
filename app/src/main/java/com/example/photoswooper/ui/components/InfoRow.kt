package com.example.photoswooper.ui.components

import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.text.format.Formatter.formatShortFileSize
import android.view.HapticFeedbackConstants
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.photoswooper.R
import com.example.photoswooper.data.models.Media
import com.example.photoswooper.data.uistates.BooleanPreference
import com.example.photoswooper.dataStore
import com.example.photoswooper.ui.components.tiny.AnimatedExpandCollapseIcon
import com.example.photoswooper.ui.viewmodels.MainViewModel
import com.example.photoswooper.utils.DataStoreInterface

data class InfoData(
    val title: String,
    @param:DrawableRes val iconPainterId: Int,
    val value: String?,
    val action: (() -> Unit)? = null,
)

@Composable
fun InfoRow(
    viewModel: MainViewModel,
    currentMedia: Media?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val reduceAnimations = DataStoreInterface(context.dataStore)
        .getBooleanSettingValue(BooleanPreference.REDUCE_ANIMATIONS.setting).collectAsState(false)

    val expanded = DataStoreInterface(context.dataStore)
        .getBooleanSettingValue(BooleanPreference.INFO_ROW_EXPANDED.setting).collectAsState(false)

    val arrayOfInfo = arrayOf(
        InfoData(
            "Date",
            R.drawable.calendar,
            currentMedia?.getFormattedDate()
        ),
        InfoData(
            "Size",
            R.drawable.hard_drives,
            formatShortFileSize(context, currentMedia?.size ?: 0)
        ),
        InfoData(
            "Location",
            R.drawable.map,
            currentMedia?.getFormattedLocation(),
            { viewModel.openLocationInMapsApp(currentMedia) }
        ),
        InfoData(
            "Album",
            iconPainterId = R.drawable.books,
            value = currentMedia?.album,
        ),
        InfoData(
            "Resolution",
            R.drawable.frame_corners,
            currentMedia?.resolution
        )
    )

    Box(modifier = modifier.pointerInput(null) {}) { // empty pointerInput prevents image receiving taps accidentally
        IconButton(
            onClick = {
                if (SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) view.performHapticFeedback(
                    if (expanded.value) HapticFeedbackConstants.TOGGLE_OFF
                    else HapticFeedbackConstants.TOGGLE_ON
                )
                viewModel.toggleInfoRowExpanded()
            },
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            AnimatedExpandCollapseIcon(
                expanded = !expanded.value, // ! to invert the direction of caret icon
                contentDescription = null,
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(horizontal = dimensionResource(R.dimen.padding_medium))
                .fillMaxWidth()
        ) {
            Text(
                text = currentMedia?.title ?: "Title",
                maxLines = if (expanded.value) 2 else 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .padding(
                        start = dimensionResource(R.dimen.padding_medium),
                        end = dimensionResource(R.dimen.padding_medium),
                        top = dimensionResource(R.dimen.padding_medium),
                        bottom = dimensionResource(R.dimen.padding_small)
                    )
            )
            if (currentMedia?.description != null)
                Text(
                    text = currentMedia.description,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .padding(horizontal = dimensionResource(R.dimen.padding_medium))
                )
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .fillMaxWidth()
            ) {
                /* Show first 3 items as these are always on first row */
                arrayOfInfo.sliceArray(0..2).forEach { currentInfo ->
                    Info(
                        title = currentInfo.title,
                        icon = painterResource(currentInfo.iconPainterId),
                        value = currentInfo.value,
                        action = currentInfo.action

                    )
                }
                /* Then conditionally show the rest */
                AnimatedVisibility(
                    visible = !expanded.value,
                    enter = expandHorizontally(
                        animationSpec = spring(
                            viewModel.defaultEntryAnimationSpec.dampingRatio,
                            if (reduceAnimations.value) 0f else viewModel.defaultEntryAnimationSpec.stiffness
                        ),
                        expandFrom = Alignment.Start
                    ) + fadeIn(),
                    exit = shrinkHorizontally(
                        animationSpec = spring(
                            viewModel.defaultEntryAnimationSpec.dampingRatio,
                            if (reduceAnimations.value) 0f else viewModel.defaultEntryAnimationSpec.stiffness
                        ),
                        shrinkTowards = Alignment.End
                    ) + fadeOut(animationSpec = spring(stiffness = Spring.StiffnessHigh)),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        arrayOfInfo.sliceArray(3..arrayOfInfo.lastIndex).forEach { currentInfo ->
                            Info(
                                title = currentInfo.title,
                                icon = painterResource(currentInfo.iconPainterId),
                                value = currentInfo.value,
                                action = currentInfo.action,
                            )
                        }
                    }
                }
            }
            AnimatedVisibility(
                visible = expanded.value,
                enter = expandVertically(
                    animationSpec = spring(
                        viewModel.defaultEntryAnimationSpec.dampingRatio,
                        if (reduceAnimations.value) 0f else viewModel.defaultEntryAnimationSpec.stiffness
                    )
                ) + fadeIn(),
                exit = shrinkVertically(
                    animationSpec = spring(
                        viewModel.defaultEntryAnimationSpec.dampingRatio,
                        if (reduceAnimations.value) 0f else Spring.StiffnessLow
                    )
                ) + fadeOut(),
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    arrayOfInfo.sliceArray(3..arrayOfInfo.lastIndex).forEach { currentInfo ->
                        Info(
                            title = currentInfo.title,
                            icon = painterResource(currentInfo.iconPainterId),
                            value = currentInfo.value,
                            action = currentInfo.action,

                            )
                    }
                }
            }
        }
    }
}

@Composable
fun Info(
    title: String,
    icon: Painter,
    value: String?,
    action: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val nullValue = "-"
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .padding(dimensionResource(R.dimen.padding_small))

    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                null,
                Modifier
                    .padding(end = dimensionResource(R.dimen.padding_xsmall))
                    .size(16.dp)

            )
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
            )
        }
        Text(
            value ?: nullValue,
            style = MaterialTheme.typography.bodyMedium,
            textDecoration =
                if (action != null && value != null)
                    TextDecoration.Underline
                else
                    TextDecoration.None,
            maxLines = 1,
            overflow = TextOverflow.MiddleEllipsis,
            modifier =
                if (action != null && value != null) Modifier.clickable { action() }
                else Modifier
        )
    }
}
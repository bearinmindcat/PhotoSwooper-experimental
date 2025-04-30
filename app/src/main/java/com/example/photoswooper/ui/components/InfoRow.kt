package com.example.photoswooper.ui.components

import android.os.Build
import android.text.format.Formatter.formatShortFileSize
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.photoswooper.R
import com.example.photoswooper.data.models.Photo
import com.example.photoswooper.ui.view.MainViewModel

@Composable
fun InfoRow(
    viewModel: MainViewModel,
    currentPhoto: Photo?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
    ) {
        Text(
            text = currentPhoto?.title?: "Title",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .padding(
                    start = dimensionResource(R.dimen.padding_medium),
                    end =  dimensionResource(R.dimen.padding_medium),
                    top = dimensionResource(R.dimen.padding_medium),
                    bottom = dimensionResource(R.dimen.padding_small))
        )
        val bodyMediumWithShadow = MaterialTheme.typography.bodyMedium.copy(
            shadow = Shadow(blurRadius = 2f)
        )
        if (currentPhoto?.description != null)
            Text(
                text = currentPhoto.description,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                style = bodyMediumWithShadow,
                modifier = Modifier
                    .padding(horizontal = dimensionResource(R.dimen.padding_medium))
            )
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Info(
                title = "Date",
                icon = painterResource(R.drawable.calendar),
                value = {
                    Text(
                        currentPhoto?.getFormattedDate() ?: "",
                        style = bodyMediumWithShadow
                    )
                }
            )
            Info(
                title = "Size",
                icon = painterResource(R.drawable.hard_drives),
                value = {
                    Text(
                        formatShortFileSize(context, currentPhoto?.size ?: 0),
                        style = bodyMediumWithShadow
                    )
                }
            )
            Info(
                title = "Location",
                icon = painterResource(R.drawable.map),
                value = {
                    Text(
                        currentPhoto?.location?.toString() ?: "",
                        style = bodyMediumWithShadow,
                        textDecoration = TextDecoration.Underline,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            viewModel.openLocationInMapsApp(currentPhoto)
                        }
                    )
                }
            )
            Info(
                title = "Album",
                icon = painterResource(R.drawable.books),
                value = {
                    Text(
                        currentPhoto?.album ?: "",
                        style = bodyMediumWithShadow
                    )
                }
            )
        }
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.fillMaxWidth()
        ) {
            Info(
                title = "Resolution",
                icon = painterResource(R.drawable.frame_corners),
                value = {
                    Text(
                        currentPhoto?.resolution ?: "",
                        style = bodyMediumWithShadow
                    )
                }
            )
            OutlinedIconButton(
                onClick = {
                    viewModel.sharePhoto()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                }
            ) { Icon(
                painterResource(R.drawable.share_network),
                contentDescription = "Share photo",
                modifier = Modifier.padding(dimensionResource(R.dimen.padding_small))
            ) }
        }
    }
}

@Composable
fun Info(
    title: String,
    icon: Painter,
    value: @Composable () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(dimensionResource(R.dimen.padding_small))
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
                style = MaterialTheme.typography.titleMedium.copy(
                    shadow = Shadow(blurRadius = 2f)
                )
            )
        }
        value()
    }
}
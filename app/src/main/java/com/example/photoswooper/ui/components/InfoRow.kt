package com.example.photoswooper.ui.components

import android.os.Build
import android.text.format.Formatter.formatShortFileSize
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
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
    val nullValue = "-"

    Box(
        modifier = modifier
    ) {
        Column (
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(horizontal = dimensionResource(R.dimen.padding_medium))
                .fillMaxWidth()
        ) {
            Text(
                text = currentPhoto?.title ?: "Title",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .padding(
                        start = dimensionResource(R.dimen.padding_medium),
                        end = dimensionResource(R.dimen.padding_medium),
                        top = dimensionResource(R.dimen.padding_medium),
                        bottom = dimensionResource(R.dimen.padding_small)
                    )
            )
            if (currentPhoto?.description != null)
                Text(
                    text = currentPhoto.description,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .padding(horizontal = dimensionResource(R.dimen.padding_medium))
                )
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {
                Info(
                    title = "Date",
                    icon = painterResource(R.drawable.calendar),
                    value = {
                        Text(
                            currentPhoto?.getFormattedDate() ?: nullValue,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
                Info(
                    title = "Size",
                    icon = painterResource(R.drawable.hard_drives),
                    value = {
                        Text(
                            formatShortFileSize(context, currentPhoto?.size ?: 0),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
                Info(
                    title = "Location",
                    icon = painterResource(R.drawable.map),
                    value = {
                        Text(
                            currentPhoto?.getFormattedLocation()?: nullValue,
                            style = MaterialTheme.typography.bodyMedium,
                            textDecoration =
                                if (currentPhoto?.getFormattedLocation() != null) TextDecoration.Underline
                                else TextDecoration.None,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable {
                                viewModel.openLocationInMapsApp(currentPhoto)
                            }
                        )
                    },
                    modifier = Modifier.weight(1.5f)
                )
            }
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {
                Info(
                    title = "Album",
                    icon = painterResource(R.drawable.books),
                    value = {
                        Text(
                            currentPhoto?.album ?: nullValue,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.MiddleEllipsis,
                        )
                    },
                    modifier = Modifier.weight(1.5f)
                )
                Info(
                    title = "Resolution",
                    icon = painterResource(R.drawable.frame_corners),
                    value = {
                        Text(
                            currentPhoto?.resolution ?: nullValue,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    modifier = Modifier.weight(1.5f)
                )
                OutlinedIconButton(
                    onClick = {
                        viewModel.sharePhoto()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    }
                ) {
                    Icon(
                        painterResource(R.drawable.share_network),
                        contentDescription = "Share photo",
                        modifier = Modifier.padding(dimensionResource(R.dimen.padding_small))
                    )
                }
            }
        }
    }
}

@Composable
fun Info(
    title: String,
    icon: Painter,
    value: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(dimensionResource(R.dimen.padding_small))
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
                fontWeight = FontWeight.Bold
            )
        }
        value()
    }
}
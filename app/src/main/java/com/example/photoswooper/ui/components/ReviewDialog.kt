package com.example.photoswooper.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandIn
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import com.example.photoswooper.R
import com.example.photoswooper.data.models.Photo

@Composable
fun ReviewDialog(
    photosToDelete: List<Photo>,
    onDismissRequest: () -> Unit,
    onCancellation: () -> Unit,
    onUnsetPhoto: (Photo) -> Unit,
    onConfirmation: () -> Unit,
    onDisableReviewDialog: () -> Unit,
) {
    var disableReviewDialog by remember { mutableStateOf(false) } // Whether to show this review dialog next time
    // TODO("Make disable review dialog persistent")

    LaunchedEffect(photosToDelete) {
        if (photosToDelete.isEmpty()) {
            onDismissRequest()
        }
    }

    Dialog(onDismissRequest = { onDismissRequest() }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimensionResource(R.dimen.padding_medium)),
            shape = MaterialTheme.shapes.large,
        ) {
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(dimensionResource(R.dimen.padding_small)),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(dimensionResource(R.dimen.padding_medium))
                ) {
                    photosToDelete.forEach { photo ->
                        AnimatedVisibility(
                            visible = photo in photosToDelete,
                            enter = expandIn(
                                animationSpec = spring(
                                    stiffness = Spring.StiffnessMediumLow,
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                )
                            ),
                            exit = shrinkOut(
                                animationSpec = spring(
                                    stiffness = Spring.StiffnessMediumLow,
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    )
                            )
                        ) {
                            Box(
                                Modifier
                                    .size(96.dp)
                                    .padding(horizontal = 4.dp)
                            ) {
                                AsyncImage(
                                    model = photo.uri,
                                    contentDescription = null,
                                    contentScale = ContentScale.FillHeight,
                                    alignment = Alignment.Center,
                                )
                                IconButton(
                                    onClick = { onUnsetPhoto(photo) },
                                    modifier = Modifier
                                        .background(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                                        )
                                        .clip(CircleShape)
                                        .size(dimensionResource(R.dimen.medium_icon))
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.x),
                                        contentDescription = "Cancel deletion of this photo",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier
                                            .padding(dimensionResource(R.dimen.padding_xsmall))

                                    )
                                }
                            }
                        }
                    }

                }
                Text(
                    text = stringResource(R.string.confirm_delete),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(dimensionResource(R.dimen.padding_small)),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(dimensionResource(R.dimen.padding_small))
                        .clickable { disableReviewDialog = !disableReviewDialog }
                ) {
                    Checkbox(
                        checked = disableReviewDialog,
                        onCheckedChange = { disableReviewDialog = !disableReviewDialog }
                    )
                    Text(
                        text = stringResource(R.string.show_review_dialog_again),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    TextButton(
                        onClick = { onDismissRequest(); onCancellation();  },
                        modifier = Modifier.padding(8.dp),
                    ) {
                        Text(
                            stringResource(R.string.cancel_delete),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    TextButton(
                        onClick = {
                            onConfirmation()
                            if(disableReviewDialog) onDisableReviewDialog()
                        },
                        modifier = Modifier.padding(8.dp),
                    ) {
                        Text(
                            stringResource(R.string.confirm),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
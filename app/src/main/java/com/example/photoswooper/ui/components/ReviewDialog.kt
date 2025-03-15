package com.example.photoswooper.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.photoswooper.R
import com.example.photoswooper.data.models.Photo

@Composable
fun ReviewDialog(
    photosToDelete: List<Photo>,
    onDismissRequest: () -> Unit,
    onCancellation: () -> Unit,
    onUnsetPhoto: (Photo) -> Unit,
    onConfirmation: () -> Unit
) {

    Dialog(onDismissRequest = { onDismissRequest() }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(375.dp)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    photosToDelete.forEach { photo ->
                        var visible by remember { mutableStateOf(true) }
                        AnimatedVisibility(
                            visible = visible,
                            enter = expandVertically(),
                            exit = shrinkVertically()
                        ) {
                            Box(
                                Modifier
                                    .size(96.dp)
                                    .padding(horizontal = 4.dp)
                            ) {
                                AsyncImage(
                                    model = photo.uri,
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit
                                )
                                Icon(
                                    painter = painterResource(R.drawable.x),
                                    contentDescription = "Cancel deletion of this photo",
                                    tint = Color.LightGray,
                                    modifier = Modifier
                                        .background(
                                            shape = CircleShape,
                                            color = Color.Transparent.copy(alpha = 0.5f)
                                        )
                                        .clip(CircleShape)
                                        .clickable {
                                            onUnsetPhoto(photo)
                                            visible = false
                                            if (photosToDelete.isEmpty()) { onDismissRequest() }
                                        }
                                )
                            }
                        }
                    }

                }
                Text(
                    text = "Are you sure you want to delete these photos?",
                    modifier = Modifier.padding(16.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    TextButton(
                        onClick = { onDismissRequest(); onCancellation();  },
                        modifier = Modifier.padding(8.dp),
                    ) {
                        Text("Cancel deletion", color = MaterialTheme.colorScheme.secondary)
                    }
                    TextButton(
                        onClick = { onConfirmation() },
                        modifier = Modifier.padding(8.dp),
                    ) {
                        Text("Confirm", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
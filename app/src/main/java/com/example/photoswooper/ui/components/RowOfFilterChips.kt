package com.example.photoswooper.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import com.example.photoswooper.R


@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun RowOfFilterChips(
    chipsText: List<String>, /* A list of strings which are displayed in each chip to inform what they do and act as an
                                identifier for the current filter option */
    current: String, // The string in chipsText that is the current filter option
    updateCurrent: (String) -> Unit, // Function to call when the user clicks on one of the filter options
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        chipsText.forEach {text ->
            FilterChip(
                selected = text == current,
                onClick = { updateCurrent(text) },
                label = {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onBackground)
                    )
                },
                modifier = Modifier
                    .padding(horizontal = dimensionResource(R.dimen.padding_small))
            )
        }
    }
}
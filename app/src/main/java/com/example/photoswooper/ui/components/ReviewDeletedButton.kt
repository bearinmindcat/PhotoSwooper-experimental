package com.example.photoswooper.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import com.example.photoswooper.R
import com.example.photoswooper.ui.viewmodels.MainViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun ReviewDeletedButton(
    view: View,
    viewModel: MainViewModel,
    numToDelete: Int,
    reviewDialogEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    ElevatedButton(
        onClick = {
            if(numToDelete > 0) {
                if (reviewDialogEnabled)
                    viewModel.showReviewDialog()
                else
                    CoroutineScope(Dispatchers.Main).launch {
                        viewModel.confirmDeletion()
                    }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            }
            else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                view.performHapticFeedback(HapticFeedbackConstants.REJECT)
        },
        modifier = modifier
//                                .height(92.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.check),
                contentDescription = null,
                modifier = Modifier.padding(dimensionResource(R.dimen.padding_small))
            )
            Text(
                text = "Review",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(dimensionResource(R.dimen.padding_small))
            )
        }
    }
}
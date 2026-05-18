package com.miseservice.smsovh.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.miseservice.smsovh.R
import com.miseservice.smsovh.viewmodel.MainUiState

@Composable
fun ServiceStatusRow(
    uiState: MainUiState,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = if (uiState.serviceActive) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            } else {
                colorResource(id = R.color.smsovh_primary).copy(alpha = 0.08f)
            },
            tonalElevation = 0.dp
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = colorResource(id = R.color.smsovh_primary)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Verified,
                        contentDescription = null,
                        tint = if (uiState.serviceActive) {
                            colorResource(id = R.color.smsovh_primary)
                        } else {
                            colorResource(id = R.color.smsovh_primary).copy(alpha = 0.75f)
                        },
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = when {
                        uiState.isLoading && uiState.serviceToggleTargetActive == true -> stringResource(R.string.service_starting)
                        uiState.isLoading && uiState.serviceToggleTargetActive == false -> stringResource(R.string.service_stopping)
                        uiState.isLoading -> stringResource(R.string.service_status_updating)
                        uiState.serviceActive -> stringResource(R.string.service_status_active)
                        else -> stringResource(R.string.service_status_inactive)
                    },
                    color = when {
                        uiState.isLoading -> colorResource(id = R.color.smsovh_primary).copy(alpha = 0.55f)
                        uiState.serviceActive -> colorResource(id = R.color.smsovh_primary)
                        else -> colorResource(id = R.color.smsovh_primary).copy(alpha = 0.75f)
                    },
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = uiState.serviceActive,
            onCheckedChange = onCheckedChange,
            enabled = !uiState.isLoading,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colorResource(id = R.color.smsovh_primary),
                checkedTrackColor = colorResource(id = R.color.smsovh_primary).copy(alpha = 0.45f),
                uncheckedThumbColor = colorResource(id = R.color.smsovh_primary).copy(alpha = 0.85f),
                uncheckedTrackColor = colorResource(id = R.color.smsovh_primary).copy(alpha = 0.25f),
                disabledCheckedThumbColor = colorResource(id = R.color.smsovh_primary).copy(alpha = 0.45f),
                disabledCheckedTrackColor = colorResource(id = R.color.smsovh_primary).copy(alpha = 0.20f),
                disabledUncheckedThumbColor = colorResource(id = R.color.smsovh_primary).copy(alpha = 0.40f),
                disabledUncheckedTrackColor = colorResource(id = R.color.smsovh_primary).copy(alpha = 0.15f)
            )
        )
    }
}


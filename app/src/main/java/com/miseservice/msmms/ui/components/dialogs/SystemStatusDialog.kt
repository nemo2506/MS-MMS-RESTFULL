package com.miseservice.msmms.ui.components.dialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.miseservice.msmms.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SystemStatusDialog(
    serviceActive: Boolean,
    networkConnected: Boolean,
    powerManagerConnected: Boolean,
    simNetworkAvailable: Boolean,
    onDismiss: () -> Unit
) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    var contentVisible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Déclenche l'entrée dès l'affichage
    LaunchedEffect(Unit) { contentVisible = true }

    Dialog(
        onDismissRequest = { /* géré par le bouton VU */ },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(screenHeight),
            contentAlignment = Alignment.BottomCenter
        ) {
            AnimatedVisibility(
                visible = contentVisible,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(320)
                ),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(280)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.system_status_dialog_title),
                            color = colorResource(id = R.color.white),
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.system_status_dialog_subtitle),
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 12.sp
                        )
                        Spacer(Modifier.height(20.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                        Spacer(Modifier.height(16.dp))

                        StatusRow(
                            label = stringResource(R.string.system_status_service),
                            ok = serviceActive
                        )
                        Spacer(Modifier.height(14.dp))
                        StatusRow(
                            label = stringResource(R.string.system_status_network),
                            ok = networkConnected
                        )
                        Spacer(Modifier.height(14.dp))
                        StatusRow(
                            label = stringResource(R.string.system_status_chargeur),
                            ok = powerManagerConnected
                        )
                        Spacer(Modifier.height(14.dp))
                        StatusRow(
                            label = stringResource(R.string.system_status_sim),
                            ok = simNetworkAvailable
                        )

                        Spacer(Modifier.height(24.dp))

                        Button(
                            onClick = {
                                scope.launch {
                                    contentVisible = false
                                    delay(300)
                                    onDismiss()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colorResource(id = R.color.smsovh_primary)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.system_status_vu),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, ok: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(
                    color = if (ok) Color(0xFF4CAF50) else Color(0xFFF44336),
                    shape = CircleShape
                )
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = "${if (ok) "✅" else "❌"} $label",
            color = Color.White.copy(alpha = if (ok) 0.9f else 1f),
            fontSize = 15.sp,
            fontWeight = if (ok) FontWeight.Normal else FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
    }
}

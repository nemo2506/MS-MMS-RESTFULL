package com.miseservice.msmms.ui.components

import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import com.miseservice.msmms.R

@Composable
fun smsOvhTextFieldColors(): TextFieldColors {
    return TextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedLabelColor = Color.White,
        unfocusedLabelColor = Color.White,
        focusedSupportingTextColor = Color.White,
        unfocusedSupportingTextColor = Color.White,
        focusedIndicatorColor = colorResource(id = R.color.smsovh_primary),
        unfocusedIndicatorColor = Color.White.copy(alpha = 0.75f),
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        cursorColor = colorResource(id = R.color.smsovh_primary)
    )
}

@Composable
fun smsOvhButtonColors(): ButtonColors {
    return ButtonDefaults.buttonColors(
        containerColor = colorResource(id = R.color.smsovh_primary),
        contentColor = colorResource(id = R.color.white),
        disabledContainerColor = colorResource(id = R.color.smsovh_primary).copy(alpha = 0.45f),
        disabledContentColor = colorResource(id = R.color.white).copy(alpha = 0.75f)
    )
}


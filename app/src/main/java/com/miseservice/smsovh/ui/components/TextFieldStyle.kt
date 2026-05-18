package com.miseservice.smsovh.ui.components

import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import com.miseservice.smsovh.R

@Composable
fun smsOvhTextFieldColors(): TextFieldColors {
    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedLabelColor = Color.White,
        unfocusedLabelColor = Color.White,
        focusedSupportingTextColor = Color.White,
        unfocusedSupportingTextColor = Color.White,
        focusedBorderColor = colorResource(id = R.color.smsovh_primary),
        unfocusedBorderColor = Color.White.copy(alpha = 0.75f),
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


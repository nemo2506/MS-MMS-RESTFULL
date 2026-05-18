package com.miseservice.smsovh.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miseservice.smsovh.R

@Composable
fun MainHeader() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.header_sms),
            fontSize = 42.sp,
            color = colorResource(id = R.color.smsovh_primary),
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(R.string.header_ovh),
            fontSize = 15.sp,
            color = colorResource(id = R.color.smsovh_primary),
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
    }
}


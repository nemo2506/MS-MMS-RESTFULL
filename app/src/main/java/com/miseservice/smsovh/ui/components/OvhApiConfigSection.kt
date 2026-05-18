package com.miseservice.smsovh.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miseservice.smsovh.R

@Composable
fun OvhApiConfigSection(
    ovhAppKey: String,
    ovhAppSecret: String,
    ovhConsumerKey: String,
    ovhServiceName: String,
    ovhEndpoint: String,
    ovhCountryPrefix: String,
    onOvhAppKeyChange: (String) -> Unit,
    onOvhAppSecretChange: (String) -> Unit,
    onOvhConsumerKeyChange: (String) -> Unit,
    onOvhServiceNameChange: (String) -> Unit,
    onOvhEndpointChange: (String) -> Unit,
    onOvhCountryPrefixChange: (String) -> Unit
) {
    Text(
        text = stringResource(R.string.ovh_credentials_title),
        fontSize = 16.5.sp,
        color = colorResource(id = R.color.smsovh_primary),
        fontWeight = FontWeight.Bold
    )
    Spacer(Modifier.height(12.dp))

    OutlinedTextField(
        value = ovhAppKey,
        onValueChange = onOvhAppKeyChange,
        label = { Text(stringResource(R.string.ovh_app_key_label)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        colors = smsOvhTextFieldColors()
    )
    Spacer(Modifier.height(10.dp))

    OutlinedTextField(
        value = ovhAppSecret,
        onValueChange = onOvhAppSecretChange,
        label = { Text(stringResource(R.string.ovh_app_secret_label)) },
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        colors = smsOvhTextFieldColors()
    )
    Spacer(Modifier.height(10.dp))

    OutlinedTextField(
        value = ovhConsumerKey,
        onValueChange = onOvhConsumerKeyChange,
        label = { Text(stringResource(R.string.ovh_consumer_key_label)) },
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        colors = smsOvhTextFieldColors()
    )
    Spacer(Modifier.height(10.dp))

    OutlinedTextField(
        value = ovhServiceName,
        onValueChange = onOvhServiceNameChange,
        label = { Text(stringResource(R.string.ovh_service_name_label)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        colors = smsOvhTextFieldColors()
    )
    Spacer(Modifier.height(10.dp))

    OutlinedTextField(
        value = ovhEndpoint,
        onValueChange = onOvhEndpointChange,
        label = { Text(stringResource(R.string.ovh_endpoint_label)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        colors = smsOvhTextFieldColors(),
        supportingText = { Text(stringResource(R.string.ovh_endpoint_supporting)) }
    )
    Spacer(Modifier.height(10.dp))

    OutlinedTextField(
        value = ovhCountryPrefix,
        onValueChange = onOvhCountryPrefixChange,
        label = { Text(stringResource(R.string.ovh_country_prefix_label)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        colors = smsOvhTextFieldColors()
    )
    Spacer(Modifier.height(8.dp))

    Text(
        text = stringResource(R.string.ovh_credentials_help_text),
        style = MaterialTheme.typography.bodySmall,
        color = colorResource(id = R.color.white)
    )
}


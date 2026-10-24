package com.coursework.unifiedmail.ui.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation

/**
 * [FlatTextField] preconfigured for password entry, with a trailing eye icon to reveal/hide the
 * typed value — masked by default, like every other password field.
 */
@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
) {
    var isRevealed by remember { mutableStateOf(false) }

    FlatTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        placeholder = placeholder,
        visualTransformation = if (isRevealed) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = { isRevealed = !isRevealed }) {
                Icon(
                    if (isRevealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (isRevealed) "Hide password" else "Show password",
                )
            }
        },
        modifier = modifier,
    )
}

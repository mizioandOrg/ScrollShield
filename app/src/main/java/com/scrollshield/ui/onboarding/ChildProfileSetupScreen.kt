package com.scrollshield.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.scrollshield.profile.ChildProfileConfig

@Composable
fun ChildProfileSetupScreen(
    wantsChildProfile: Boolean?,
    childPinDigits: String,
    childPinConfirm: String,
    onWantsChildProfile: (Boolean) -> Unit,
    onPinChange: (String) -> Unit,
    onPinConfirmChange: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Child Profile",
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = "Will children use this phone?",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = { onWantsChildProfile(true) },
                modifier = Modifier.weight(1f),
                enabled = wantsChildProfile != true
            ) {
                Text("Yes")
            }
            OutlinedButton(
                onClick = { onWantsChildProfile(false) },
                modifier = Modifier.weight(1f),
                enabled = wantsChildProfile != false
            ) {
                Text("No")
            }
        }

        if (wantsChildProfile == true) {
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Child Profile Defaults",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "- Time budget: ${ChildProfileConfig.DEFAULT_TIME_BUDGET_MINUTES} min/day",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "- Scroll mask: always on, not dismissable",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "- Ad counter: enabled",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "- PIN protected: yes",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "- Blocked categories: ${ChildProfileConfig.BLOCKED_CATEGORY_LABELS.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Set a 4-digit PIN to protect profile switching",
                style = MaterialTheme.typography.bodyMedium
            )

            OutlinedTextField(
                value = childPinDigits,
                onValueChange = { value ->
                    if (value.length <= 4 && value.all { it.isDigit() }) {
                        onPinChange(value)
                    }
                },
                label = { Text("4-Digit PIN") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = childPinConfirm,
                onValueChange = { value ->
                    if (value.length <= 4 && value.all { it.isDigit() }) {
                        onPinConfirmChange(value)
                    }
                },
                label = { Text("Confirm PIN") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                isError = childPinConfirm.isNotEmpty() && childPinDigits != childPinConfirm,
                supportingText = if (childPinConfirm.isNotEmpty() && childPinDigits != childPinConfirm) {
                    { Text("PINs do not match") }
                } else null,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

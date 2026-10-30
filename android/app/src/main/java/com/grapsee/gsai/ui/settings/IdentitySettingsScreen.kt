package com.grapsee.gsai.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.data.AccountStore
import com.grapsee.gsai.ui.components.GsGlassRole
import com.grapsee.gsai.ui.components.RefFootnote
import com.grapsee.gsai.ui.components.RefPrimaryButton
import com.grapsee.gsai.ui.components.RefSection
import com.grapsee.gsai.ui.components.RefSettingsPage
import com.grapsee.gsai.ui.components.RefTextField
import com.grapsee.gsai.ui.icons.GsIcons

/**
 * The editor behind Profile's Name/Email rows and Account & Security's identity
 * card. Before this page the two screens pointed at each other for "editing"
 * and neither had a field: a loop with no destination.
 *
 * It edits exactly what the app shows (AccountStore). This build provisions no
 * Grapsee Mail mailbox, so the footnote says the address changes this install's
 * identity, not a mailbox.
 */
@Composable
fun IdentitySettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(AccountStore.displayName(context)) }
    var email by remember { mutableStateOf(AccountStore.email(context)) }
    val cleanEmail = email.trim()
    val emailError = if (cleanEmail.isNotEmpty() && !cleanEmail.contains("@")) {
        "Enter an address like you@grapsee.com"
    } else {
        null
    }

    RefSettingsPage(
        title = "Your details",
        subtitle = "The name and address this app shows.",
        onBack = onBack,
    ) {
        RefSection(label = "Profile", role = GsGlassRole.CardLight) {
            Column(
                Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RefTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "Name",
                    placeholder = "Your name",
                    leadingIcon = GsIcons.UserRound,
                    fieldTestTag = "identity-name",
                )
                RefTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = "Email",
                    placeholder = "you@grapsee.com",
                    keyboardType = KeyboardType.Email,
                    error = emailError,
                    leadingIcon = GsIcons.Mail,
                    fieldTestTag = "identity-email",
                )
            }
        }
        RefFootnote(
            "Saved on this device. This changes what the app shows; it does " +
                "not create or move a Grapsee Mail account."
        )
        Spacer(Modifier.height(10.dp))
        RefPrimaryButton(
            label = "Save",
            onClick = {
                if (emailError == null) {
                    AccountStore.setDisplayName(context, name.trim())
                    AccountStore.setEmail(context, cleanEmail)
                    onBack()
                }
            },
            icon = GsIcons.Check,
        )
    }
}

package com.grapsee.gsai.ui.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material.icons.outlined.PhoneIphone
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.grapsee.gsai.data.SettingsStore
import com.grapsee.gsai.ui.theme.GsMotion
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import kotlinx.coroutines.launch

/** Internal auth flow state machine — self-contained, no nav dependency. */
private enum class AuthStep { Welcome, SignIn, SignUp, VerifyEmail, TwoFactor, ResetPassword, ResetSent }

/** Kinetic horizontal slide used for every step change (forward = enter from
 *  the right). Under reduce-motion / reduce-animations the step snaps with no
 *  transition at all — a 220 ms slide is still motion. */
private fun stepTransition(forward: Boolean): ContentTransform {
    if (SettingsStore.reduceAnimations || SettingsStore.reduceMotion) {
        return EnterTransition.None togetherWith ExitTransition.None
    }
    val slideSpec = tween<IntOffset>(durationMillis = 220)
    return (
        slideInHorizontally(animationSpec = slideSpec) { full ->
            if (forward) full / 4 else -full / 4
        } + fadeIn(animationSpec = GsMotion.standard())
        ) togetherWith (
        slideOutHorizontally(animationSpec = slideSpec) { full ->
            if (forward) -full / 4 else full / 4
        } + fadeOut(animationSpec = GsMotion.standard())
        )
}

private fun resetCode(codes: SnapshotStateList<String>) {
    for (index in codes.indices) codes[index] = ""
}

@Composable
fun AuthScreen(onFinished: () -> Unit) {
    var step by remember { mutableStateOf(AuthStep.Welcome) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showMessage: (String) -> Unit = { message ->
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    // Shared credential state across steps (simulated flow — no backend yet).
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showSignInErrors by remember { mutableStateOf(false) }
    var showSignUpErrors by remember { mutableStateOf(false) }
    var showResetErrors by remember { mutableStateOf(false) }
    var termsAccepted by remember { mutableStateOf(false) }
    var rememberMe by remember { mutableStateOf(false) }
    val verifyCode = remember { mutableStateListOf("", "", "", "", "", "") }
    val twoFactorCode = remember { mutableStateListOf("", "", "", "", "", "") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                stepTransition(forward = targetState.ordinal >= initialState.ordinal)
            },
            label = "authStep"
        ) { current ->
            when (current) {
                AuthStep.Welcome -> WelcomeStep(
                    onCreateAccount = { step = AuthStep.SignUp },
                    onSignIn = { step = AuthStep.SignIn }
                )
                AuthStep.SignIn -> SignInStep(
                    email = email,
                    password = password,
                    rememberMe = rememberMe,
                    showErrors = showSignInErrors,
                    onEmailChange = { email = it },
                    onPasswordChange = { password = it },
                    onRememberChange = { rememberMe = it },
                    onBack = { step = AuthStep.Welcome },
                    onForgotPassword = { step = AuthStep.ResetPassword },
                    onSignIn = {
                        if (email.isBlank() || password.isBlank()) {
                            showSignInErrors = true
                        } else {
                            showSignInErrors = false
                            resetCode(verifyCode)
                            step = AuthStep.VerifyEmail
                        }
                    },
                    onSocialSignIn = {
                        resetCode(verifyCode)
                        step = AuthStep.VerifyEmail
                    },
                    onPasskey = onFinished
                )
                AuthStep.SignUp -> SignUpStep(
                    name = name,
                    email = email,
                    password = password,
                    termsAccepted = termsAccepted,
                    showErrors = showSignUpErrors,
                    onNameChange = { name = it },
                    onEmailChange = { email = it },
                    onPasswordChange = { password = it },
                    onTermsChange = { termsAccepted = it },
                    onBack = { step = AuthStep.Welcome },
                    onCreateAccount = {
                        if (name.isBlank() || email.isBlank() || password.isBlank()) {
                            showSignUpErrors = true
                        } else {
                            showSignUpErrors = false
                            resetCode(verifyCode)
                            step = AuthStep.VerifyEmail
                        }
                    }
                )
                AuthStep.VerifyEmail -> VerifyEmailStep(
                    email = email,
                    code = verifyCode,
                    onVerify = {
                        resetCode(twoFactorCode)
                        step = AuthStep.TwoFactor
                    },
                    onResend = { showMessage("Code sent") }
                )
                AuthStep.TwoFactor -> TwoFactorStep(
                    code = twoFactorCode,
                    onVerify = onFinished,
                    onBackupCode = { showMessage("Backup code sign-in is coming in a future build") }
                )
                AuthStep.ResetPassword -> ResetPasswordStep(
                    email = email,
                    showErrors = showResetErrors,
                    onEmailChange = { email = it },
                    onBack = { step = AuthStep.SignIn },
                    onSend = {
                        if (email.isBlank()) {
                            showResetErrors = true
                        } else {
                            showResetErrors = false
                            step = AuthStep.ResetSent
                        }
                    }
                )
                AuthStep.ResetSent -> ResetSentStep(
                    email = email,
                    onBackToSignIn = { step = AuthStep.SignIn }
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

/** Scrollable step chrome with optional back chevron; centered steps get centered content. */
@Composable
private fun AuthStepScaffold(
    onBack: (() -> Unit)? = null,
    centered: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = GsMotion.spaceL),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start
    ) {
        Spacer(Modifier.height(GsMotion.spaceM))
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Spacer(Modifier.height(GsMotion.spaceS))
        }
        content()
        Spacer(Modifier.height(GsMotion.spaceXL))
    }
}

@Composable
private fun WelcomeStep(onCreateAccount: () -> Unit, onSignIn: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = GsMotion.spaceL)
    ) {
        Spacer(Modifier.weight(1.1f))
        Text(
            text = "GS AI",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(GsMotion.spaceS))
        Text(
            text = "Your intelligent command centre",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(GsMotion.spaceXL))
        WelcomeValueRow(
            icon = Icons.Outlined.AutoAwesome,
            title = "One AI for everything",
            caption = "Chat, write, research and build in one place"
        )
        Spacer(Modifier.height(GsMotion.spaceM))
        WelcomeValueRow(
            icon = Icons.Outlined.Lock,
            title = "Your data stays yours",
            caption = "On-device history. No ads, no tracking."
        )
        Spacer(Modifier.height(GsMotion.spaceM))
        WelcomeValueRow(
            icon = Icons.Outlined.Speed,
            title = "Native speed, editorial craft",
            caption = "Built native. Designed with care."
        )
        Spacer(Modifier.weight(1f))
        Button(onClick = onCreateAccount, modifier = Modifier.fillMaxWidth()) {
            Text("Create account")
        }
        Spacer(Modifier.height(GsMotion.spaceS))
        OutlinedButton(onClick = onSignIn, modifier = Modifier.fillMaxWidth()) {
            Text("Sign in")
        }
        Spacer(Modifier.height(GsMotion.spaceM))
        Text(
            text = "By continuing you agree to the Terms and Privacy Policy",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(Modifier.height(GsMotion.spaceL))
    }
}

@Composable
private fun WelcomeValueRow(icon: ImageVector, title: String, caption: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SignInStep(
    email: String,
    password: String,
    rememberMe: Boolean,
    showErrors: Boolean,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onRememberChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onForgotPassword: () -> Unit,
    onSignIn: () -> Unit,
    onSocialSignIn: () -> Unit,
    onPasskey: () -> Unit
) {
    AuthStepScaffold(onBack = onBack) {
        Text(
            text = "Welcome back",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(GsMotion.spaceM))
        AuthTextField(
            value = email,
            onValueChange = onEmailChange,
            label = "Email",
            keyboardType = KeyboardType.Email,
            supporting = if (showErrors && email.isBlank()) "Enter your email" else null
        )
        Spacer(Modifier.height(GsMotion.spaceS))
        AuthTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = "Password",
            isPassword = true,
            supporting = if (showErrors && password.isBlank()) "Enter your password" else null
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onForgotPassword) {
                Text("Forgot password?")
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "Remember me",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Checkbox(checked = rememberMe, onCheckedChange = onRememberChange)
        }
        Button(onClick = onSignIn, modifier = Modifier.fillMaxWidth()) {
            Text("Sign in")
        }
        Spacer(Modifier.height(GsMotion.spaceL))
        AuthDividerRow()
        Spacer(Modifier.height(GsMotion.spaceL))
        OutlinedButton(onClick = onSocialSignIn, modifier = Modifier.fillMaxWidth()) {
            Icon(
                imageVector = Icons.Outlined.Language,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(GsMotion.spaceS))
            Text("Continue with Google")
        }
        Spacer(Modifier.height(GsMotion.spaceS))
        OutlinedButton(onClick = onSocialSignIn, modifier = Modifier.fillMaxWidth()) {
            Icon(
                imageVector = Icons.Outlined.PhoneIphone,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(GsMotion.spaceS))
            Text("Continue with Apple")
        }
        Spacer(Modifier.height(GsMotion.spaceS))
        FilledTonalButton(onClick = onPasskey, modifier = Modifier.fillMaxWidth()) {
            Icon(
                imageVector = Icons.Outlined.Fingerprint,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(GsMotion.spaceS))
            Text("Use a passkey")
        }
    }
}

@Composable
private fun SignUpStep(
    name: String,
    email: String,
    password: String,
    termsAccepted: Boolean,
    showErrors: Boolean,
    onNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTermsChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onCreateAccount: () -> Unit
) {
    val strength = when {
        password.length >= 12 -> 3
        password.length >= 8 -> 2
        password.length >= 4 -> 1
        else -> 0
    }
    AuthStepScaffold(onBack = onBack) {
        Text(
            text = "Create your account",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(GsMotion.spaceM))
        AuthTextField(
            value = name,
            onValueChange = onNameChange,
            label = "Full name",
            supporting = if (showErrors && name.isBlank()) "Enter your name" else null
        )
        Spacer(Modifier.height(GsMotion.spaceS))
        AuthTextField(
            value = email,
            onValueChange = onEmailChange,
            label = "Email",
            keyboardType = KeyboardType.Email,
            supporting = if (showErrors && email.isBlank()) "Enter your email" else null
        )
        Spacer(Modifier.height(GsMotion.spaceS))
        AuthTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = "Password",
            isPassword = true,
            supporting = if (showErrors && password.isBlank()) "Enter a password" else null
        )
        Spacer(Modifier.height(GsMotion.spaceS))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            repeat(3) { segment ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (segment < strength) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            }
                        )
                )
            }
        }
        Spacer(Modifier.height(GsMotion.spaceXS))
        Text(
            text = when (strength) {
                3 -> "Strong password"
                2 -> "Good password"
                1 -> "Weak password"
                else -> "Use at least 4 characters"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(GsMotion.spaceS))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = termsAccepted, onCheckedChange = onTermsChange)
            Text(
                text = "I agree to the Terms and Privacy Policy",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(GsMotion.spaceS))
        Button(onClick = onCreateAccount, modifier = Modifier.fillMaxWidth()) {
            Text("Create account")
        }
    }
}

@Composable
private fun VerifyEmailStep(
    email: String,
    code: SnapshotStateList<String>,
    onVerify: () -> Unit,
    onResend: () -> Unit
) {
    AuthStepScaffold(centered = true) {
        IconCircle(icon = Icons.Outlined.Mail)
        Spacer(Modifier.height(GsMotion.spaceM))
        Text(
            text = "Check your inbox",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(GsMotion.spaceS))
        Text(
            text = "We sent a 6-digit code to ${email.ifBlank { "your email" }}.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(GsMotion.spaceL))
        SixCodeRow(codes = code)
        Spacer(Modifier.height(GsMotion.spaceL))
        Button(
            onClick = onVerify,
            enabled = code.all { it.length == 1 },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Verify")
        }
        Spacer(Modifier.height(GsMotion.spaceS))
        TextButton(onClick = onResend) {
            Text("Resend code")
        }
    }
}

@Composable
private fun TwoFactorStep(
    code: SnapshotStateList<String>,
    onVerify: () -> Unit,
    onBackupCode: () -> Unit
) {
    AuthStepScaffold(centered = true) {
        IconCircle(icon = Icons.Outlined.Shield)
        Spacer(Modifier.height(GsMotion.spaceM))
        Text(
            text = "Two-factor authentication",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(GsMotion.spaceS))
        Text(
            text = "Enter the code from your authenticator app",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(GsMotion.spaceL))
        SixCodeRow(codes = code)
        Spacer(Modifier.height(GsMotion.spaceL))
        Button(
            onClick = onVerify,
            enabled = code.all { it.length == 1 },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Verify")
        }
        Spacer(Modifier.height(GsMotion.spaceS))
        TextButton(onClick = onBackupCode) {
            Text("Use a backup code")
        }
    }
}

@Composable
private fun ResetPasswordStep(
    email: String,
    showErrors: Boolean,
    onEmailChange: (String) -> Unit,
    onBack: () -> Unit,
    onSend: () -> Unit
) {
    AuthStepScaffold(onBack = onBack) {
        Text(
            text = "Reset password",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(GsMotion.spaceS))
        Text(
            text = "Enter your account email and we'll send a reset link.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(GsMotion.spaceL))
        AuthTextField(
            value = email,
            onValueChange = onEmailChange,
            label = "Email",
            keyboardType = KeyboardType.Email,
            supporting = if (showErrors && email.isBlank()) "Enter your email" else null
        )
        Spacer(Modifier.height(GsMotion.spaceM))
        Button(onClick = onSend, modifier = Modifier.fillMaxWidth()) {
            Text("Send reset link")
        }
    }
}

@Composable
private fun ResetSentStep(email: String, onBackToSignIn: () -> Unit) {
    AuthStepScaffold(centered = true) {
        IconCircle(icon = Icons.Outlined.MarkEmailRead)
        Spacer(Modifier.height(GsMotion.spaceM))
        Text(
            text = "Reset link sent",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(GsMotion.spaceS))
        Text(
            text = "Check ${email.ifBlank { "your inbox" }} for a link to reset your password.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(GsMotion.spaceL))
        Button(onClick = onBackToSignIn, modifier = Modifier.fillMaxWidth()) {
            Text("Back to sign in")
        }
    }
}

/** Outlined text field with optional password eye toggle and subtle error text. */
@Composable
private fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    supporting: String? = null
) {
    var showPassword by remember { mutableStateOf(false) }
    val supportingText: (@Composable () -> Unit)? = if (supporting != null) {
        {
            Text(
                text = supporting,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    } else {
        null
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (isPassword && !showPassword) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        trailingIcon = if (isPassword) {
            {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        imageVector = if (showPassword) {
                            Icons.Outlined.VisibilityOff
                        } else {
                            Icons.Outlined.Visibility
                        },
                        contentDescription = if (showPassword) "Hide password" else "Show password",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            null
        },
        supportingText = supportingText
    )
}

/** Six single-digit boxes with paste-distribution and auto-advance focus. */
@Composable
private fun SixCodeRow(codes: SnapshotStateList<String>) {
    val focusRequesters = remember { List(6) { FocusRequester() } }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceS)
    ) {
        repeat(6) { index ->
            OutlinedTextField(
                value = codes[index],
                onValueChange = { raw ->
                    val digits = raw.filter { it.isDigit() }
                    if (digits.length > 1) {
                        var cursor = index
                        for (digit in digits) {
                            if (cursor > 5) break
                            codes[cursor] = digit.toString()
                            cursor++
                        }
                        focusRequesters[(cursor - 1).coerceIn(0, 5)].requestFocus()
                    } else {
                        codes[index] = digits
                        if (digits.isNotEmpty() && index < 5) {
                            focusRequesters[index + 1].requestFocus()
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .focusRequester(focusRequesters[index]),
                textStyle = MaterialTheme.typography.titleLarge.copy(textAlign = TextAlign.Center),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
        }
    }
}

@Composable
private fun IconCircle(icon: ImageVector) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(72.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(30.dp)
            )
        }
    }
}

@Composable
private fun AuthDividerRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GsMotion.spaceM)
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline)
        Text(
            text = "or continue with",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline)
    }
}

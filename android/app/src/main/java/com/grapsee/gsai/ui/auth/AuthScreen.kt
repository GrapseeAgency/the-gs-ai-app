package com.grapsee.gsai.ui.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grapsee.gsai.data.AccountStore
import com.grapsee.gsai.data.SettingsStore
import com.grapsee.gsai.ui.components.RefArt
import com.grapsee.gsai.ui.components.RefArtwork
import com.grapsee.gsai.ui.components.RefCard
import com.grapsee.gsai.ui.components.RefDivider
import com.grapsee.gsai.ui.components.RefIconButton
import com.grapsee.gsai.ui.components.RefPrimaryButton
import com.grapsee.gsai.ui.components.RefRow
import com.grapsee.gsai.ui.components.RefTextField
import com.grapsee.gsai.ui.icons.GsIcons
import com.grapsee.gsai.ui.theme.GsTheme
import kotlinx.coroutines.launch

/**
 * AUTH — the door.
 *
 * The flow is unchanged (sign-in, sign-up, verify, 2FA, reset); the surface is
 * rebuilt in the reference language because this is the first screen a person
 * ever sees and it previously looked like a Material form generator.
 *
 * Conventions this file holds:
 *  · No opaque background. The bloom lives behind the whole app; auth is not
 *    an exception. The only paint here is the halo behind the companion.
 *  · Labels above fields, values in primary ink. There is no state in which a
 *    field can render its own label on top of its own value.
 *  · One full-width apricot button per page. Secondary paths are quiet glass
 *    rows — never a second orange button.
 */

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
        } + fadeIn(animationSpec = tween(220))
        ) togetherWith (
        slideOutHorizontally(animationSpec = slideSpec) { full ->
            if (forward) -full / 4 else full / 4
        } + fadeOut(animationSpec = tween(180))
        )
}

private fun resetCode(codes: SnapshotStateList<String>) {
    for (index in codes.indices) codes[index] = ""
}

@Composable
fun AuthScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
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
                    onVerify = {
                        // IDENTITY PERSISTENCE — the honest latest-known-success
                        // point. Every credential path (sign-up AND sign-in)
                        // funnels through VerifyEmail → TwoFactor, and this is
                        // the moment the flow actually completes: both code
                        // steps verified, [onFinished] hands off to Onboarding.
                        // The sign-up path knows the name; sign-in only captured
                        // the email — a blank value is skipped rather than
                        // written, so a stored name survives a later sign-in.
                        if (name.isNotBlank()) AccountStore.setDisplayName(context, name)
                        if (email.isNotBlank()) AccountStore.setEmail(context, email)
                        onFinished()
                    },
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

// ---------------------------------------------------------------------------
// Step chrome
// ---------------------------------------------------------------------------

/**
 * Scrollable step page: optional back chevron, eyebrow, title, supporting
 * line, then the body. Content scrolls under the IME; the primary action sits
 * at the end of the content so it is reachable one-handed after the last field.
 */
@Composable
private fun AuthStepPage(
    title: String,
    subtitle: String? = null,
    eyebrow: String? = null,
    onBack: (() -> Unit)? = null,
    centered: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = GsTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = if (onBack != null) 4.dp else 22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                RefIconButton(GsIcons.ChevronLeft, "Back", onBack)
            }
            Spacer(Modifier.weight(1f))
            // The wordmark is the one constant across every auth step: the
            // reader always knows they are still in GS, not in a web view.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(colors.accent, colors.accentStrong)
                            )
                        )
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = "GS",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textPrimary
                )
            }
        }

        if (!eyebrow.isNullOrBlank()) {
            Text(
                text = eyebrow.uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.1.sp
                ),
                color = colors.accentStrong,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        Text(
            text = title,
            style = MaterialTheme.typography.displaySmall,
            color = colors.textPrimary,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start
        )

        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                textAlign = if (centered) TextAlign.Center else TextAlign.Start
            )
        }

        Spacer(Modifier.height(26.dp))
        content()
        Spacer(Modifier.height(24.dp))
    }
}

// ---------------------------------------------------------------------------
// Welcome
// ---------------------------------------------------------------------------

@Composable
private fun WelcomeStep(onCreateAccount: () -> Unit, onSignIn: () -> Unit) {
    val colors = GsTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(14.dp))

        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(190.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                colors.aurora.first().copy(alpha = 0.50f),
                                Color.Transparent
                            )
                        )
                    )
            )
            RefArtwork(RefArt.Bloom, size = 140.dp, contentDescription = "GS companion")
        }

        Text(
            text = "GS AI",
            style = MaterialTheme.typography.displayLarge,
            color = colors.textPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Your thoughtful companion for thinking, making and getting things done.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(Modifier.height(26.dp))

        RefCard {
            RefRow(
                title = "One assistant, every medium",
                subtitle = "Answers as charts, tables, plans and tools",
                icon = GsIcons.Wand
            )
            RefDivider()
            RefRow(
                title = "Your data stays yours",
                subtitle = "History on this device. No ads, no tracking.",
                icon = GsIcons.Lock
            )
            RefDivider()
            RefRow(
                title = "Runs where you choose",
                subtitle = "Cloud models or a model installed on this phone",
                icon = GsIcons.Cpu
            )
        }

        Spacer(Modifier.height(26.dp))

        RefPrimaryButton(label = "Create account", onClick = onCreateAccount)
        Spacer(Modifier.height(10.dp))
        RefQuietRow(icon = GsIcons.UserRound, label = "I already have an account", onClick = onSignIn)

        Spacer(Modifier.height(14.dp))
        Text(
            text = "By continuing you agree to the Terms and Privacy Policy.",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        Spacer(Modifier.height(18.dp))
    }
}

// ---------------------------------------------------------------------------
// Sign in
// ---------------------------------------------------------------------------

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
    val colors = GsTheme.colors
    AuthStepPage(
        eyebrow = "Welcome back",
        title = "Sign in",
        subtitle = "Pick up exactly where you left off.",
        onBack = onBack
    ) {
        RefTextField(
            value = email,
            onValueChange = onEmailChange,
            label = "Email",
            placeholder = "you@example.com",
            keyboardType = KeyboardType.Email,
            error = if (showErrors && email.isBlank()) "Enter your email" else null
        )
        Spacer(Modifier.height(14.dp))
        RefTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = "Password",
            isPassword = true,
            imeAction = ImeAction.Done,
            onDone = onSignIn,
            error = if (showErrors && password.isBlank()) "Enter your password" else null
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Forgot password?",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                color = colors.accentStrong,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = onForgotPassword
                    )
                    .padding(horizontal = 4.dp, vertical = 10.dp)
            )
            Spacer(Modifier.weight(1f))
            AuthCheck(label = "Remember me", checked = rememberMe, onCheckedChange = onRememberChange)
        }

        Spacer(Modifier.height(16.dp))
        RefPrimaryButton(label = "Sign in", onClick = onSignIn)
        Spacer(Modifier.height(22.dp))
        AuthDividerRow()
        Spacer(Modifier.height(18.dp))
        RefQuietRow(icon = GsIcons.Globe, label = "Continue with Google", onClick = onSocialSignIn)
        Spacer(Modifier.height(9.dp))
        RefQuietRow(icon = GsIcons.Smartphone, label = "Continue with Apple", onClick = onSocialSignIn)
        Spacer(Modifier.height(9.dp))
        RefQuietRow(icon = GsIcons.Fingerprint, label = "Use a passkey", onClick = onPasskey)
    }
}

// ---------------------------------------------------------------------------
// Sign up
// ---------------------------------------------------------------------------

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
    val colors = GsTheme.colors
    val strength = when {
        password.length >= 12 -> 3
        password.length >= 8 -> 2
        password.length >= 4 -> 1
        else -> 0
    }
    AuthStepPage(
        eyebrow = "New here",
        title = "Create your account",
        subtitle = "Two minutes now, then it stays out of your way.",
        onBack = onBack
    ) {
        RefTextField(
            value = name,
            onValueChange = onNameChange,
            label = "Full name",
            placeholder = "How should GS address you?",
            error = if (showErrors && name.isBlank()) "Enter your name" else null
        )
        Spacer(Modifier.height(14.dp))
        RefTextField(
            value = email,
            onValueChange = onEmailChange,
            label = "Email",
            placeholder = "you@example.com",
            keyboardType = KeyboardType.Email,
            error = if (showErrors && email.isBlank()) "Enter your email" else null
        )
        Spacer(Modifier.height(14.dp))
        RefTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = "Password",
            isPassword = true,
            imeAction = ImeAction.Done,
            onDone = onCreateAccount,
            error = if (showErrors && password.isBlank()) "Choose a password" else null
        )

        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            repeat(3) { segment ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (segment < strength) colors.accent else colors.divider
                        )
                )
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            text = when (strength) {
                3 -> "Strong password"
                2 -> "Good password"
                1 -> "Weak password"
                else -> "Use at least 4 characters"
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (strength >= 2) colors.success else colors.textTertiary,
            modifier = Modifier.padding(start = 4.dp)
        )

        Spacer(Modifier.height(16.dp))
        AuthCheck(
            label = "I agree to the Terms and Privacy Policy",
            checked = termsAccepted,
            onCheckedChange = onTermsChange
        )

        Spacer(Modifier.height(18.dp))
        RefPrimaryButton(label = "Create account", onClick = onCreateAccount)
    }
}

// ---------------------------------------------------------------------------
// Verify / 2FA / reset
// ---------------------------------------------------------------------------

@Composable
private fun VerifyEmailStep(
    email: String,
    code: SnapshotStateList<String>,
    onVerify: () -> Unit,
    onResend: () -> Unit
) {
    val colors = GsTheme.colors
    AuthStepPage(
        eyebrow = "One more step",
        title = "Check your inbox",
        subtitle = "We sent a 6-digit code to ${email.ifBlank { "your email" }}.",
        centered = true
    ) {
        HeroGlyph(GsIcons.Mail)
        Spacer(Modifier.height(28.dp))
        SixCodeRow(codes = code, onComplete = onVerify)
        Spacer(Modifier.height(24.dp))
        RefPrimaryButton(
            label = "Verify",
            onClick = onVerify,
            enabled = code.all { it.length == 1 }
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Resend code",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            color = colors.accentStrong,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClick = onResend
                )
                .padding(horizontal = 14.dp, vertical = 12.dp)
        )
    }
}

@Composable
private fun TwoFactorStep(
    code: SnapshotStateList<String>,
    onVerify: () -> Unit,
    onBackupCode: () -> Unit
) {
    val colors = GsTheme.colors
    AuthStepPage(
        eyebrow = "Security check",
        title = "Two-factor authentication",
        subtitle = "Enter the code from your authenticator app.",
        centered = true
    ) {
        HeroGlyph(GsIcons.Shield)
        Spacer(Modifier.height(28.dp))
        SixCodeRow(codes = code, onComplete = onVerify)
        Spacer(Modifier.height(24.dp))
        RefPrimaryButton(
            label = "Verify",
            onClick = onVerify,
            enabled = code.all { it.length == 1 }
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Use a backup code",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            color = colors.accentStrong,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClick = onBackupCode
                )
                .padding(horizontal = 14.dp, vertical = 12.dp)
        )
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
    AuthStepPage(
        eyebrow = "Account recovery",
        title = "Reset password",
        subtitle = "Enter your account email and we'll send a reset link.",
        onBack = onBack
    ) {
        RefTextField(
            value = email,
            onValueChange = onEmailChange,
            label = "Email",
            placeholder = "you@example.com",
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Done,
            onDone = onSend,
            error = if (showErrors && email.isBlank()) "Enter your email" else null
        )
        Spacer(Modifier.height(20.dp))
        RefPrimaryButton(label = "Send reset link", onClick = onSend)
    }
}

@Composable
private fun ResetSentStep(email: String, onBackToSignIn: () -> Unit) {
    AuthStepPage(
        eyebrow = "Check your inbox",
        title = "Reset link sent",
        subtitle = "Check ${email.ifBlank { "your inbox" }} for a link to reset your password.",
        centered = true
    ) {
        HeroGlyph(GsIcons.MailCheck)
        Spacer(Modifier.height(30.dp))
        RefPrimaryButton(label = "Back to sign in", onClick = onBackToSignIn)
    }
}

// ---------------------------------------------------------------------------
// Atoms shared by the auth steps
// ---------------------------------------------------------------------------

/** A soft halo carrying one line icon — used on the short confirm steps. */
@Composable
private fun HeroGlyph(icon: ImageVector) {
    val colors = GsTheme.colors
    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(126.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(colors.aurora.first().copy(alpha = 0.55f), Color.Transparent)
                    )
                )
        )
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(colors.glassSurface)
                .border(1.dp, colors.glassBorder, RoundedCornerShape(28.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.accentStrong,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

/** Full-width glass row with a leading icon — the secondary action style. */
@Composable
private fun RefQuietRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    val colors = GsTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(colors.glassSurface.copy(alpha = 0.58f))
            .border(1.dp, colors.glassBorder, RoundedCornerShape(999.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClickLabel = label,
                onClick = onClick
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(9.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = colors.textPrimary)
    }
}

/** A square check that owns its whole row as the hit target. */
@Composable
private fun AuthCheck(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val colors = GsTheme.colors
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Checkbox,
                onClickLabel = label,
                onClick = { onCheckedChange(!checked) }
            )
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (checked) colors.accentStrong else colors.glassSurface.copy(alpha = 0.7f))
                .border(1.dp, if (checked) colors.accentStrong else colors.borderStrong, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(
                    GsIcons.Check,
                    contentDescription = null,
                    tint = colors.onAccent,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
        Spacer(Modifier.width(9.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = colors.textSecondary
        )
    }
}

@Composable
private fun AuthDividerRow() {
    val colors = GsTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(colors.divider)
        )
        Text(
            text = "or continue with",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textTertiary
        )
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(colors.divider)
        )
    }
}

/**
 * Six single-digit cells with paste-distribution, auto-advance and a
 * backspace bridge for soft keyboards.
 *
 * The bridge: every empty cell holds a zero-width space, so pressing backspace
 * on an empty cell produces a text *change* (there is no character to delete,
 * so no KeyEvent reaches us on a soft keyboard). Deleting the bridge is the
 * one signal every IME is willing to give.
 */
@Composable
private fun SixCodeRow(codes: SnapshotStateList<String>, onComplete: () -> Unit) {
    val colors = GsTheme.colors
    val focusRequesters = remember { List(6) { FocusRequester() } }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        repeat(6) { index ->
            val bridge = codes[index].isEmpty()
            OutlinedTextField(
                value = if (bridge) "\u200B" else codes[index],
                onValueChange = { raw ->
                    val digits = raw.filter { it.isDigit() }
                    when {
                        digits.length > 1 -> {
                            var cursor = index
                            for (digit in digits) {
                                if (cursor > 5) break
                                codes[cursor] = digit.toString()
                                cursor++
                            }
                            focusRequesters[(cursor - 1).coerceIn(0, 5)].requestFocus()
                        }
                        digits.isNotEmpty() -> {
                            codes[index] = digits
                            if (index < 5) {
                                focusRequesters[index + 1].requestFocus()
                            } else if (codes.all { it.length == 1 }) {
                                onComplete()
                            }
                        }
                        bridge && index > 0 -> {
                            codes[index - 1] = ""
                            focusRequesters[index - 1].requestFocus()
                        }
                        else -> codes[index] = ""
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(0.86f)
                    .focusRequester(focusRequesters[index])
                    // Hardware keyboards DO deliver a real KeyEvent; the bridge
                    // above covers soft keyboards. Consumed here so it cannot
                    // double-fire through the text change.
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        if (event.key != Key.Backspace && event.key != Key.Delete) {
                            return@onPreviewKeyEvent false
                        }
                        if (codes[index].isNotEmpty() || index == 0) return@onPreviewKeyEvent false
                        codes[index - 1] = ""
                        focusRequesters[index - 1].requestFocus()
                        true
                    },
                textStyle = MaterialTheme.typography.titleLarge.copy(
                    textAlign = TextAlign.Center,
                    color = colors.textPrimary
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.accent,
                    unfocusedBorderColor = colors.glassBorder,
                    focusedContainerColor = colors.inputSurface,
                    unfocusedContainerColor = colors.inputSurface.copy(alpha = 0.72f),
                    cursorColor = colors.accentStrong,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary
                )
            )
        }
    }
}

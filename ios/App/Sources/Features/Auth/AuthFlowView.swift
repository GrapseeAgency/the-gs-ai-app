import SwiftUI

/// Auth — self-contained multi-step flow: welcome → sign in / sign up →
/// email verification → two-factor, plus password reset.
/// Standalone chrome (custom back circles); the host wires presentation,
/// so there are zero router references here.
struct AuthFlowView: View {
    let onComplete: () -> Void

    private enum AuthStep {
        case welcome, signIn, signUp, verifyEmail, twoFactor, resetPassword, resetSent
    }

    // MARK: State

    @State private var step: AuthStep = .welcome

    // Shared account fields
    @State private var fullName = ""
    @State private var email = ""
    @State private var password = ""
    @State private var showPassword = false
    @State private var rememberMe = true
    @State private var agreedToTerms = false

    // Verification codes
    @State private var emailCode = ""
    @State private var twoFactorCode = ""
    @FocusState private var emailCodeFocus: Bool
    @FocusState private var twoFactorFocus: Bool

    // Password reset
    @State private var resetEmail = ""

    // Backup code note
    @State private var showBackupNote = false

    private let codeLength = 6

    var body: some View {
        ZStack {
            Aero.background.ignoresSafeArea()

            ZStack {
                switch step {
                case .welcome:
                    welcomeStep.transition(stepTransition)
                case .signIn:
                    signInStep.transition(stepTransition)
                case .signUp:
                    signUpStep.transition(stepTransition)
                case .verifyEmail:
                    verifyEmailStep.transition(stepTransition)
                case .twoFactor:
                    twoFactorStep.transition(stepTransition)
                case .resetPassword:
                    resetPasswordStep.transition(stepTransition)
                case .resetSent:
                    resetSentStep.transition(stepTransition)
                }
            }
        }
        .alert("Use a backup code", isPresented: $showBackupNote) {
            Button("OK", role: .cancel) {}
        } message: {
            Text("Backup codes are created in Settings → Security. Enter one here any time your authenticator is out of reach.")
        }
    }

    // MARK: Step transition (asymmetric slide per spec)

    private var stepTransition: AnyTransition {
        .asymmetric(
            insertion: .move(edge: .trailing).combined(with: .opacity),
            removal: .move(edge: .leading).combined(with: .opacity)
        )
    }

    private func go(_ next: AuthStep) {
        withAnimation(Aero.spring) { step = next }
    }

    // MARK: Welcome

    private var welcomeStep: some View {
        VStack(spacing: 0) {
            Spacer()

            StaggerIn(index: 0) {
                VStack(spacing: Aero.Spacing.xs) {
                    Text("GS AI")
                        .font(Aero.displayTitle())
                        .foregroundStyle(Aero.text)
                    Text("Calm, fast and private — your AI command centre.")
                        .font(Aero.caption())
                        .foregroundStyle(Aero.textMuted)
                        .multilineTextAlignment(.center)
                }
                .frame(maxWidth: .infinity)
            }

            Spacer()

            StaggerIn(index: 1) {
                VStack(spacing: Aero.Spacing.s) {
                    valueRow("sparkles", "One AI for everything")
                    valueRow("lock.shield", "Your data stays yours")
                    valueRow("bolt", "Native speed, editorial craft")
                }
            }

            StaggerIn(index: 2) {
                VStack(spacing: Aero.Spacing.s) {
                    Button {
                        go(.signUp)
                    } label: {
                        Text("Create account")
                            .font(Aero.title())
                            .frame(maxWidth: .infinity)
                            .padding(14)
                            .background(RoundedRectangle(cornerRadius: Aero.Radius.card).fill(Aero.accent))
                            .foregroundStyle(Color.white)
                    }
                    .buttonStyle(KineticPressStyle())

                    Button {
                        go(.signIn)
                    } label: {
                        Text("Sign in")
                            .font(Aero.title())
                            .frame(maxWidth: .infinity)
                            .padding(14)
                            .background(RoundedRectangle(cornerRadius: Aero.Radius.card).fill(Aero.container))
                            .foregroundStyle(Aero.text)
                    }
                    .buttonStyle(KineticPressStyle())

                    Text("By continuing you agree to our Terms and Privacy Policy.")
                        .font(Aero.caption())
                        .foregroundStyle(Aero.textMuted)
                        .multilineTextAlignment(.center)
                        .padding(.top, Aero.Spacing.xs)
                }
                .padding(.top, Aero.Spacing.l)
            }
        }
        .padding(.horizontal, Aero.Spacing.m)
        .padding(.bottom, Aero.Spacing.l)
    }

    private func valueRow(_ icon: String, _ text: String) -> some View {
        HStack(spacing: Aero.Spacing.m) {
            Image(systemName: icon)
                .font(.system(size: 15))
                .foregroundStyle(Aero.accent)
                .frame(width: 40, height: 40)
                .background(Circle().fill(Aero.container))
            Text(text)
                .font(Aero.body())
                .foregroundStyle(Aero.text)
            Spacer()
        }
        .padding(12)
        .background(RoundedRectangle(cornerRadius: Aero.Radius.card).fill(Aero.surface))
        .overlay(RoundedRectangle(cornerRadius: Aero.Radius.card).stroke(Aero.outline, lineWidth: 1))
    }

    // MARK: Sign in

    private var signInStep: some View {
        formStep(back: .welcome, title: "Welcome back", subtitle: "Sign in to pick up where you left off.") {
            StaggerIn(index: 1) {
                AeroCard {
                    VStack(alignment: .leading, spacing: Aero.Spacing.s) {
                        capsuleField("Email", text: $email, keyboard: .emailAddress)
                        passwordField
                        HStack(spacing: Aero.Spacing.s) {
                            Button("Forgot password?") {
                                resetEmail = email
                                go(.resetPassword)
                            }
                            .font(Aero.label())
                            .foregroundStyle(Aero.accent)
                            .buttonStyle(KineticPressStyle())
                            Spacer()
                            Toggle("Remember me", isOn: $rememberMe)
                                .font(Aero.label())
                                .toggleStyle(SwitchToggleStyle(tint: Aero.accent))
                        }
                    }
                }
            }
            StaggerIn(index: 2) {
                VStack(spacing: Aero.Spacing.m) {
                    Button {
                        go(.verifyEmail)
                    } label: {
                        Text("Sign in")
                            .font(Aero.title())
                            .frame(maxWidth: .infinity)
                            .padding(14)
                            .background(RoundedRectangle(cornerRadius: Aero.Radius.card).fill(Aero.accent))
                            .foregroundStyle(Color.white)
                    }
                    .buttonStyle(KineticPressStyle())
                    .disabled(email.isEmpty || password.isEmpty)
                    .opacity(email.isEmpty || password.isEmpty ? 0.4 : 1)

                    dividerCaption("or continue with")

                    VStack(spacing: Aero.Spacing.s) {
                        socialButton("Continue with Google", icon: "safari")
                        socialButton("Continue with Apple", icon: "apple.logo")
                        socialButton("Use a passkey", icon: "touchid")
                    }
                }
            }
        }
    }

    // MARK: Sign up

    private var signUpStep: some View {
        formStep(back: .welcome, title: "Create your account", subtitle: "Two minutes now, one AI for everything after.") {
            StaggerIn(index: 1) {
                AeroCard {
                    VStack(alignment: .leading, spacing: Aero.Spacing.s) {
                        capsuleField("Full name", text: $fullName)
                        capsuleField("Email", text: $email, keyboard: .emailAddress)
                        passwordField
                        VStack(alignment: .leading, spacing: 6) {
                            HStack(spacing: Aero.Spacing.xs) {
                                ForEach(0..<3, id: \.self) { segment in
                                    Capsule()
                                        .fill(segment < passwordStrength ? AnyShapeStyle(Aero.accent) : AnyShapeStyle(Aero.container))
                                        .frame(height: 6)
                                        .frame(maxWidth: .infinity)
                                }
                            }
                            Text(strengthLabel)
                                .font(Aero.label())
                                .foregroundStyle(Aero.textMuted)
                        }
                        .animation(Aero.snappy, value: passwordStrength)
                        Toggle("I agree to the Terms and Privacy Policy", isOn: $agreedToTerms)
                            .font(Aero.caption())
                            .foregroundStyle(Aero.text)
                            .toggleStyle(SwitchToggleStyle(tint: Aero.accent))
                    }
                }
            }
            StaggerIn(index: 2) {
                Button {
                    go(.verifyEmail)
                } label: {
                    Text("Create account")
                        .font(Aero.title())
                        .frame(maxWidth: .infinity)
                        .padding(14)
                        .background(RoundedRectangle(cornerRadius: Aero.Radius.card).fill(Aero.accent))
                        .foregroundStyle(Color.white)
                }
                .buttonStyle(KineticPressStyle())
                .disabled(!signUpReady)
                .opacity(signUpReady ? 1 : 0.4)
            }
        }
    }

    private var signUpReady: Bool {
        !fullName.trimmingCharacters(in: .whitespaces).isEmpty
            && !email.isEmpty
            && password.count >= 6
            && agreedToTerms
    }

    private var passwordStrength: Int {
        if password.count >= 10 { return 3 }
        if password.count >= 6 { return 2 }
        if !password.isEmpty { return 1 }
        return 0
    }

    private var strengthLabel: String {
        switch passwordStrength {
        case 0: return "Password strength"
        case 1: return "Weak — keep going"
        case 2: return "Good — add symbols for extra strength"
        default: return "Strong password"
        }
    }

    // MARK: Verify email

    private var verifyEmailStep: some View {
        codeStep(
            back: .signIn,
            icon: "envelope.circle.fill",
            title: "Check your inbox",
            subtitle: "We sent a 6-digit code to \(email.isEmpty ? "your email" : email).",
            code: $emailCode,
            focus: $emailCodeFocus,
            buttonTitle: "Verify",
            footer: { resendFooter },
            onVerify: { go(.twoFactor) }
        )
    }

    private var resendFooter: some View {
        Button {
            emailCode = ""
            emailCodeFocus = true
        } label: {
            Text("Resend code")
                .font(Aero.label())
                .foregroundStyle(Aero.accent)
        }
        .buttonStyle(KineticPressStyle())
    }

    // MARK: Two-factor

    private var twoFactorStep: some View {
        codeStep(
            back: .verifyEmail,
            icon: "shield.fill",
            title: "Two-factor authentication",
            subtitle: "Enter the 6-digit code from your authenticator app.",
            code: $twoFactorCode,
            focus: $twoFactorFocus,
            buttonTitle: "Verify",
            footer: { backupFooter },
            onVerify: { onComplete() }
        )
    }

    private var backupFooter: some View {
        Button {
            showBackupNote = true
        } label: {
            Text("Use a backup code")
                .font(Aero.label())
                .foregroundStyle(Aero.textMuted)
        }
        .buttonStyle(KineticPressStyle())
    }

    // MARK: Password reset

    private var resetPasswordStep: some View {
        formStep(back: .signIn, title: "Reset password", subtitle: "We'll email you a secure link to choose a new password.") {
            StaggerIn(index: 1) {
                AeroCard {
                    VStack(alignment: .leading, spacing: Aero.Spacing.s) {
                        capsuleField("Email", text: $resetEmail, keyboard: .emailAddress)
                    }
                }
            }
            StaggerIn(index: 2) {
                Button {
                    go(.resetSent)
                } label: {
                    Text("Send reset link")
                        .font(Aero.title())
                        .frame(maxWidth: .infinity)
                        .padding(14)
                        .background(RoundedRectangle(cornerRadius: Aero.Radius.card).fill(Aero.accent))
                        .foregroundStyle(Color.white)
                }
                .buttonStyle(KineticPressStyle())
                .disabled(resetEmail.isEmpty)
                .opacity(resetEmail.isEmpty ? 0.4 : 1)
            }
        }
    }

    private var resetSentStep: some View {
        VStack(spacing: 0) {
            HStack {
                backCircle(.signIn)
                Spacer()
            }
            .padding(.horizontal, Aero.Spacing.m)
            .padding(.top, Aero.Spacing.s)

            Spacer()

            VStack(spacing: Aero.Spacing.l) {
                Image(systemName: "paperplane.fill")
                    .font(.system(size: 26))
                    .foregroundStyle(Aero.accent)
                    .frame(width: 64, height: 64)
                    .background(Circle().fill(Aero.accent.opacity(0.14)))

                VStack(spacing: Aero.Spacing.xs) {
                    Text("Reset link sent")
                        .font(Aero.headline())
                        .foregroundStyle(Aero.text)
                    Text("We emailed a secure link to \(resetEmail.isEmpty ? "your inbox" : resetEmail). It expires in 30 minutes.")
                        .font(Aero.caption())
                        .foregroundStyle(Aero.textMuted)
                        .multilineTextAlignment(.center)
                }

                Button {
                    go(.signIn)
                } label: {
                    Text("Back to sign in")
                        .font(Aero.title())
                        .frame(maxWidth: .infinity)
                        .padding(14)
                        .background(RoundedRectangle(cornerRadius: Aero.Radius.card).fill(Aero.container))
                        .foregroundStyle(Aero.text)
                }
                .buttonStyle(KineticPressStyle())
            }
            .padding(.horizontal, Aero.Spacing.m)

            Spacer()
        }
        .transition(stepTransition)
    }

    // MARK: Step scaffolds

    private func formStep<Content: View>(
        back: AuthStep?,
        title: String,
        subtitle: String? = nil,
        @ViewBuilder content: () -> Content
    ) -> some View {
        VStack(spacing: 0) {
            HStack {
                if let back {
                    backCircle(back)
                }
                Spacer()
            }
            .padding(.horizontal, Aero.Spacing.m)
            .padding(.top, Aero.Spacing.s)

            ScrollView {
                VStack(alignment: .leading, spacing: Aero.Spacing.m) {
                    VStack(alignment: .leading, spacing: Aero.Spacing.xs) {
                        Text(title)
                            .font(Aero.headline())
                            .foregroundStyle(Aero.text)
                        if let subtitle {
                            Text(subtitle)
                                .font(Aero.caption())
                                .foregroundStyle(Aero.textMuted)
                        }
                    }
                    content()
                }
                .padding(.horizontal, Aero.Spacing.m)
                .padding(.top, Aero.Spacing.m)
                .padding(.bottom, Aero.Spacing.xl)
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .scrollDismissesKeyboard(.interactively)
        }
        .transition(stepTransition)
    }

    private func codeStep<Footer: View>(
        back: AuthStep,
        icon: String,
        title: String,
        subtitle: String,
        code: Binding<String>,
        focus: FocusState<Bool>.Binding,
        buttonTitle: String,
        @ViewBuilder footer: () -> Footer,
        onVerify: @escaping () -> Void
    ) -> some View {
        VStack(spacing: 0) {
            HStack {
                backCircle(back)
                Spacer()
            }
            .padding(.horizontal, Aero.Spacing.m)
            .padding(.top, Aero.Spacing.s)

            Spacer()

            VStack(spacing: Aero.Spacing.l) {
                Image(systemName: icon)
                    .font(.system(size: 26))
                    .foregroundStyle(Aero.accent)
                    .frame(width: 64, height: 64)
                    .background(Circle().fill(Aero.accent.opacity(0.14)))

                VStack(spacing: Aero.Spacing.xs) {
                    Text(title)
                        .font(Aero.headline())
                        .foregroundStyle(Aero.text)
                        .multilineTextAlignment(.center)
                    Text(subtitle)
                        .font(Aero.caption())
                        .foregroundStyle(Aero.textMuted)
                        .multilineTextAlignment(.center)
                }

                codeEntry(code: code, focus: focus)

                Button(action: onVerify) {
                    Text(buttonTitle)
                        .font(Aero.title())
                        .frame(maxWidth: .infinity)
                        .padding(14)
                        .background(RoundedRectangle(cornerRadius: Aero.Radius.card).fill(Aero.accent))
                        .foregroundStyle(Color.white)
                }
                .buttonStyle(KineticPressStyle())
                .disabled(code.wrappedValue.count != codeLength)
                .opacity(code.wrappedValue.count == codeLength ? 1 : 0.4)

                footer()
            }
            .padding(.horizontal, Aero.Spacing.m)

            Spacer()
        }
        .transition(stepTransition)
    }

    // MARK: Code entry (one invisible field over six visual cells)

    private func codeEntry(code: Binding<String>, focus: FocusState<Bool>.Binding) -> some View {
        ZStack {
            HStack(spacing: 8) {
                ForEach(0..<codeLength, id: \.self) { index in
                    codeCell(index: index, code: code.wrappedValue)
                }
            }
            TextField("", text: code)
                .keyboardType(.numberPad)
                .focused(focus)
                // The number pad has no return key — the shared Done bar is
                // the only way to put the keyboard away (Task 85-e I6).
                .gsKeyboardDoneBar()
                .font(.system(size: 1))
                .opacity(0.02)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .contentShape(Rectangle())
        }
        .frame(maxWidth: .infinity)
        .contentShape(Rectangle())
        .onTapGesture { focus.wrappedValue = true }
        .onChange(of: code.wrappedValue) { newValue in
            let digits = newValue.filter { $0.isNumber }
            if digits != newValue || digits.count > codeLength {
                code.wrappedValue = String(digits.prefix(codeLength))
            }
        }
    }

    private func codeCell(index: Int, code: String) -> some View {
        let chars = Array(code)
        let digit = index < chars.count ? String(chars[index]) : ""
        let isActive = index == chars.count && chars.count < codeLength
        return Text(digit)
            .font(Aero.responsive(22, .semibold, relativeTo: .title2, design: .monospaced))
            .foregroundStyle(Aero.text)
            .frame(width: 44, height: 52)
            .background(RoundedRectangle(cornerRadius: 12).fill(Aero.container))
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(isActive ? Aero.accent : Aero.outline, lineWidth: 1)
            )
    }

    // MARK: Field helpers (capsule style per AssistantCreateView)

    private func capsuleField(
        _ placeholder: String,
        text: Binding<String>,
        keyboard: UIKeyboardType = .default
    ) -> some View {
        TextField(placeholder, text: text)
            .keyboardType(keyboard)
            .textInputAutocapitalization(keyboard == .emailAddress ? .never : nil)
            .autocorrectionDisabled()
            .font(Aero.body())
            .foregroundStyle(Aero.text)
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .background(Capsule().fill(Aero.container))
            .overlay(Capsule().stroke(Aero.outline, lineWidth: 1))
    }

    private var passwordField: some View {
        HStack(spacing: Aero.Spacing.s) {
            Group {
                if showPassword {
                    TextField("Password", text: $password)
                } else {
                    SecureField("Password", text: $password)
                }
            }
            .font(Aero.body())
            .foregroundStyle(Aero.text)

            Button {
                showPassword.toggle()
            } label: {
                Image(systemName: showPassword ? "eye.slash" : "eye")
                    .font(.system(size: 14))
                    .foregroundStyle(Aero.textMuted)
            }
            .buttonStyle(KineticPressStyle())
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(Capsule().fill(Aero.container))
        .overlay(Capsule().stroke(Aero.outline, lineWidth: 1))
    }

    private func dividerCaption(_ text: String) -> some View {
        HStack(spacing: Aero.Spacing.s) {
            Rectangle().fill(Aero.outline).frame(height: 1)
            Text(text)
                .font(Aero.caption())
                .foregroundStyle(Aero.textMuted)
            Rectangle().fill(Aero.outline).frame(height: 1)
        }
    }

    private func socialButton(_ title: String, icon: String) -> some View {
        Button {
            onComplete()
        } label: {
            HStack(spacing: Aero.Spacing.s) {
                Image(systemName: icon)
                    .font(.system(size: 15))
                    .foregroundStyle(Aero.text)
                Text(title)
                    .font(Aero.body())
                    .foregroundStyle(Aero.text)
                Spacer()
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 13)
            .background(RoundedRectangle(cornerRadius: Aero.Radius.card).fill(Aero.surface))
            .overlay(RoundedRectangle(cornerRadius: Aero.Radius.card).stroke(Aero.outline, lineWidth: 1))
        }
        .buttonStyle(KineticPressStyle())
    }

    private func backCircle(_ target: AuthStep) -> some View {
        Button {
            go(target)
        } label: {
            Image(systemName: "chevron.left")
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(Aero.text)
                .frame(width: 36, height: 36)
                .background(Circle().fill(Aero.container))
        }
        .buttonStyle(KineticPressStyle())
    }
}

// MARK: - Staggered entrance (private per-file helper)

private struct StaggerIn<Content: View>: View {
    let index: Int
    @ViewBuilder var content: () -> Content

    @State private var appeared = false

    var body: some View {
        content()
            .opacity(appeared ? 1 : 0)
            .offset(y: appeared ? 0 : 16)
            .onAppear {
                withAnimation(Aero.spring.delay(Aero.stagger(index))) {
                    appeared = true
                }
            }
    }
}

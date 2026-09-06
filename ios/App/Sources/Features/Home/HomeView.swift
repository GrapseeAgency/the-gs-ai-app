import SwiftUI

/**
 * AERUO KINETIC home canvas — benchmark pattern (ChatGPT · Claude · Kimi):
 * obsidian full-bleed, top bar (menu · model pill · new chat), centred brand
 * orb + time-aware serif greeting + upgrade pill, quick chips and one hero
 * input bar pinned to the bottom. Navigation lives in the drawer.
 */
struct HomeView: View {

    var onOpenDrawer: (() -> Void)? = nil

    private enum HomeWorkspace: String, Identifiable {
        case research, vision, writing, code, image
        var id: String { rawValue }
    }
    @State private var activeWorkspace: HomeWorkspace?
    @State private var breathe: CGFloat = 1.0

    // Forced-obsidian canvas (fixed benchmark-dark in both appearances)
    private let canvas = Aero.dynamic(
        light: UIColor(red: 0.039, green: 0.051, blue: 0.071, alpha: 1),
        dark: UIColor(red: 0.039, green: 0.051, blue: 0.071, alpha: 1))
    private let raised = Aero.dynamic(
        light: UIColor(red: 0.094, green: 0.118, blue: 0.157, alpha: 1),
        dark: UIColor(red: 0.094, green: 0.118, blue: 0.157, alpha: 1))
    private let ink = Aero.dynamic(
        light: UIColor(red: 0.929, green: 0.937, blue: 0.949, alpha: 1),
        dark: UIColor(red: 0.929, green: 0.937, blue: 0.949, alpha: 1))
    private let muted = Aero.dynamic(
        light: UIColor(red: 0.545, green: 0.576, blue: 0.631, alpha: 1),
        dark: UIColor(red: 0.545, green: 0.576, blue: 0.631, alpha: 1))

    var body: some View {
        ZStack {
            canvas.ignoresSafeArea()

            VStack(spacing: 0) {
                topBar
                Spacer(minLength: 8)
                hero
                Spacer(minLength: 8)
                bottomCluster
            }
            .padding(.horizontal, Aero.Spacing.m)
        }
        .toolbar(.hidden, for: .navigationBar)
        .onAppear {
            withAnimation(.easeInOut(duration: 2.4).repeatForever(autoreverses: true)) {
                breathe = 1.06
            }
        }
        .fullScreenCover(item: $activeWorkspace) { workspace in
            switch workspace {
            case .research: ResearchView()
            case .vision: VisionView()
            case .writing: WritingStudioView()
            case .code: CodeWorkspaceView()
            case .image: ImageStudioView()
            }
        }
    }

    // MARK: Top bar — menu · model pill · new chat

    private var topBar: some View {
        HStack(spacing: Aero.Spacing.s) {
            Button {
                onOpenDrawer?()
            } label: {
                circleIcon("line.3.horizontal")
            }
            .buttonStyle(KineticPressStyle())
            .accessibilityLabel("Open menu")

            Spacer()

            NavigationLink(value: AeroRoute.models) {
                HStack(spacing: 6) {
                    Circle()
                        .fill(LinearGradient(
                            colors: Aero.aurora,
                            startPoint: .topLeading, endPoint: .bottomTrailing))
                        .frame(width: 8, height: 8)
                    Text("GS Balanced · High")
                        .font(Aero.label())
                        .foregroundColor(ink)
                }
                .padding(.horizontal, Aero.Spacing.m)
                .padding(.vertical, 10)
                .background(Capsule().fill(raised))
            }
            .buttonStyle(KineticPressStyle())

            Spacer()

            NavigationLink(value: AeroRoute.chat(nil)) {
                circleIcon("plus")
            }
            .buttonStyle(KineticPressStyle())
            .accessibilityLabel("New chat")
        }
        .padding(.vertical, Aero.Spacing.s)
    }

    private func circleIcon(_ symbol: String) -> some View {
        ZStack {
            Circle().fill(raised).frame(width: 44, height: 44)
            Image(systemName: symbol)
                .font(.system(size: 16, weight: .medium))
                .foregroundColor(ink)
        }
    }

    // MARK: Hero — orb · greeting · upgrade

    private var hero: some View {
        VStack(spacing: Aero.Spacing.s) {
            ZStack {
                Circle()
                    .fill(LinearGradient(
                        colors: Aero.aurora,
                        startPoint: .topLeading, endPoint: .bottomTrailing))
                    .frame(width: 84, height: 84)
                    .scaleEffect(breathe)
                Circle()
                    .fill(canvas.opacity(0.35))
                    .frame(width: 66, height: 66)
                Image(systemName: "sparkles")
                    .font(.system(size: 20))
                    .foregroundColor(ink)
            }
            .padding(.bottom, Aero.Spacing.m)

            Text(greeting)
                .font(Aero.displayTitle())
                .foregroundColor(ink)
                .multilineTextAlignment(.center)

            Text("What should we make today?")
                .font(Aero.body())
                .foregroundColor(muted)
                .padding(.bottom, Aero.Spacing.s)

            NavigationLink(value: AeroRoute.billing) {
                HStack(spacing: 6) {
                    Image(systemName: "sparkles")
                        .font(.system(size: 11))
                        .foregroundColor(Aero.accent)
                    Text("Upgrade plan")
                        .font(Aero.label())
                        .foregroundColor(ink)
                }
                .padding(.horizontal, Aero.Spacing.m)
                .padding(.vertical, 10)
                .background(Capsule().fill(raised))
            }
            .buttonStyle(KineticPressStyle())
        }
        .frame(maxWidth: .infinity)
    }

    private var greeting: String {
        let hour = Calendar.current.component(.hour, from: Date())
        switch hour {
        case 23, 0...4: return "Up late, Admin?"
        case 5...11: return "Good morning, Admin"
        case 12...17: return "Good afternoon, Admin"
        default: return "Good evening, Admin"
        }
    }

    // MARK: Bottom cluster — suggestions · chips · hero input

    private var bottomCluster: some View {
        VStack(spacing: Aero.Spacing.m) {
            VStack(spacing: Aero.Spacing.xs) {
                suggestion("doc.text", "Summarise a PDF into a brief")
                suggestion("pencil.line", "Draft a launch email")
            }

            chips

            heroInput

            Text("GS can make mistakes — double-check important info.")
                .font(Aero.caption())
                .foregroundColor(muted)
                .frame(maxWidth: .infinity)
                .padding(.bottom, Aero.Spacing.s)
        }
    }

    private func suggestion(_ symbol: String, _ label: String) -> some View {
        NavigationLink(value: AeroRoute.chat(nil)) {
            HStack(spacing: Aero.Spacing.m) {
                ZStack {
                    Circle().fill(raised).frame(width: 38, height: 38)
                    Image(systemName: symbol)
                        .font(.system(size: 14))
                        .foregroundColor(ink)
                }
                Text(label)
                    .font(Aero.body())
                    .foregroundColor(ink)
                Spacer()
            }
            .padding(.horizontal, 6)
            .padding(.vertical, 6)
            .contentShape(Rectangle())
        }
        .buttonStyle(KineticPressStyle())
    }

    private var chips: some View {
        let items: [(String, String, HomeWorkspace?)] = [
            ("Projects", "folder", nil),
            ("Research", "safari", .research),
            ("Vision", "eye", .vision),
            ("Image", "photo", .image),
            ("Writing", "pencil.line", .writing),
            ("Code", "curlybraces", .code),
            ("Voice", "mic", nil),
            ("Library", "books.vertical", nil),
            ("Models", "speed", nil)
        ]
        let routes: [String: AeroRoute] = [
            "Projects": .projects,
            "Voice": .voice,
            "Library": .library,
            "Models": .models
        ]
        return ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: Aero.Spacing.s) {
                ForEach(items, id: \.0) { label, symbol, workspace in
                    if let workspace {
                        Button {
                            activeWorkspace = workspace
                        } label: {
                            chipLabel(label, symbol)
                        }
                        .buttonStyle(KineticPressStyle())
                    } else if let route = routes[label] {
                        NavigationLink(value: route) {
                            chipLabel(label, symbol)
                        }
                        .buttonStyle(KineticPressStyle())
                    }
                }
            }
            .padding(.horizontal, 2)
            .padding(.vertical, 2)
        }
    }

    private func chipLabel(_ label: String, _ symbol: String) -> some View {
        HStack(spacing: 6) {
            Image(systemName: symbol)
                .font(.system(size: 11))
                .foregroundColor(muted)
            Text(label)
                .font(Aero.label())
                .foregroundColor(ink)
        }
        .padding(.horizontal, Aero.Spacing.m)
        .padding(.vertical, 10)
        .background(Capsule().fill(raised))
    }

    private var heroInput: some View {
        HStack(spacing: Aero.Spacing.s) {
            NavigationLink(value: AeroRoute.chat(nil)) {
                ZStack {
                    Circle().fill(Aero.accent.opacity(0.16)).frame(width: 42, height: 42)
                    Image(systemName: "plus")
                        .font(.system(size: 16, weight: .medium))
                        .foregroundColor(Aero.accent)
                }
            }
            .buttonStyle(KineticPressStyle())
            .accessibilityLabel("Attach — new chat")

            NavigationLink(value: AeroRoute.chat(nil)) {
                Text("Ask anything")
                    .font(Aero.body())
                    .foregroundColor(muted)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.vertical, Aero.Spacing.s)
                    .contentShape(Rectangle())
            }
            .buttonStyle(KineticPressStyle())

            NavigationLink(value: AeroRoute.voice) {
                Image(systemName: "mic")
                    .font(.system(size: 16))
                    .foregroundColor(muted)
                    .frame(width: 42, height: 42)
                    .contentShape(Rectangle())
            }
            .buttonStyle(KineticPressStyle())
            .accessibilityLabel("Voice input")

            NavigationLink(value: AeroRoute.voice) {
                ZStack {
                    Circle()
                        .fill(LinearGradient(
                            colors: Aero.aurora,
                            startPoint: .topLeading, endPoint: .bottomTrailing))
                        .frame(width: 44, height: 44)
                    Image(systemName: "waveform")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundColor(ink)
                }
            }
            .buttonStyle(KineticPressStyle())
            .accessibilityLabel("Voice mode")
        }
        .padding(Aero.Spacing.s)
        .background(RoundedRectangle(cornerRadius: 28).fill(raised))
    }
}

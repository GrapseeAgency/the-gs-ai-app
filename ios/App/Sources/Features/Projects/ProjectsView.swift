import SwiftUI

// MARK: - Stagger entrance (private per-file helper)

/// Fades + lifts a section into place, delayed by its index —
/// the Aeruo Kinetic section entrance.
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

// MARK: - Projects — bundled workspaces

/// PROJECTS root (pushed or embedded — resolved by the frozen router).
/// Every card answers from [ProjectStore]: user-created projects with real
/// chat counts and a timestamp that moves when the reader touches the
/// project. The toolbar plus and the dashed "New project" card both open
/// the create alert — no dead controls, no seeded rows; a fresh install
/// shows an honest empty state until the reader builds their first project.
struct ProjectsView: View {

    @ObservedObject private var store = ProjectStore.shared
    @State private var showCreate = false
    @State private var newName = ""
    @State private var newBlurb = ""
    @State private var newInstructions = ""

    // MARK: Body

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Aero.Spacing.l) {
                StaggerIn(index: 0) { header }
                StaggerIn(index: 1) { newProjectCard }
                if store.projects.isEmpty {
                    StaggerIn(index: 2) {
                        Text("No projects yet — create one to bundle chats, instructions and context.")
                            .font(Aero.caption())
                            .foregroundStyle(Aero.textMuted)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                } else {
                    StaggerIn(index: 2) { projectList }
                }
            }
            .padding(.horizontal, Aero.Spacing.m)
            .padding(.top, Aero.Spacing.s)
            .padding(.bottom, Aero.Spacing.xl)
        }
        .background(Aero.background.ignoresSafeArea())
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button {
                    resetFields()
                    showCreate = true
                } label: {
                    Image(systemName: "plus.circle")
                        .font(.system(size: 17, weight: .medium))
                        .foregroundStyle(Aero.text)
                }
                .buttonStyle(KineticPressStyle())
                .accessibilityLabel("New project")
            }
        }
        .alert("New project", isPresented: $showCreate) {
            TextField("Name", text: $newName)
            TextField("What is it for? (optional)", text: $newBlurb)
            TextField("Custom instructions (optional)", text: $newInstructions)
            Button("Create") {
                ProjectStore.shared.create(
                    name: newName,
                    blurb: newBlurb,
                    instructions: newInstructions
                )
                resetFields()
            }
            Button("Cancel", role: .cancel) { resetFields() }
        }
    }

    // MARK: Header

    private var header: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.xs) {
            Text("Projects")
                .font(Aero.displayTitle())
                .foregroundStyle(Aero.text)
            Text("Chats, files and instructions — bundled per mission.")
                .font(Aero.caption())
                .foregroundStyle(Aero.textMuted)
        }
    }

    // MARK: New project (dashed outline card — live creation)

    private var newProjectCard: some View {
        Button {
            resetFields()
            showCreate = true
        } label: {
            HStack(spacing: Aero.Spacing.s) {
                Image(systemName: "plus")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Aero.accent)
                Text("New project")
                    .font(Aero.title())
                    .foregroundStyle(Aero.text)
                Spacer()
                Text("Create")
                    .font(Aero.label())
                    .foregroundStyle(Aero.textMuted)
            }
            .padding(Aero.Spacing.m)
            .background(RoundedRectangle(cornerRadius: Aero.Radius.card).fill(Aero.surface))
            .overlay(
                RoundedRectangle(cornerRadius: Aero.Radius.card)
                    .stroke(Aero.outline, style: StrokeStyle(lineWidth: 1, dash: [6, 4]))
            )
        }
        .buttonStyle(KineticPressStyle())
    }

    // MARK: Project cards (real data)

    private var projectList: some View {
        VStack(spacing: Aero.Spacing.s) {
            ForEach(store.projects) { project in
                NavigationLink(value: AeroRoute.project(project.id)) {
                    AeroCard {
                        VStack(alignment: .leading, spacing: Aero.Spacing.xs) {
                            Text(project.name)
                                .font(Aero.title())
                                .foregroundStyle(Aero.text)
                                .frame(maxWidth: .infinity, alignment: .leading)
                            if !project.blurb.isEmpty {
                                Text(project.blurb)
                                    .font(Aero.caption())
                                    .foregroundStyle(Aero.textMuted)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                            }
                            Text("\(project.chatIds.count) chats · updated \(relative(project.updatedAt))")
                                .font(Aero.label())
                                .foregroundStyle(Aero.accentDeep)
                                .frame(maxWidth: .infinity, alignment: .leading)
                        }
                    }
                }
                .buttonStyle(KineticPressStyle())
            }
        }
    }

    // MARK: Helpers

    private func resetFields() {
        newName = ""
        newBlurb = ""
        newInstructions = ""
    }

    private func relative(_ iso: String) -> String {
        guard let then = GSFormatters.date(from: iso) else { return "earlier" } // cached ISO formatter
        let interval = Date().timeIntervalSince(then)
        switch interval {
        case ..<60: return "just now"
        case ..<3600: return "\(Int(interval / 60))m ago"
        case ..<86400: return "\(Int(interval / 3600))h ago"
        case ..<(7 * 86400): return "\(Int(interval / 86400))d ago"
        default: return "earlier"
        }
    }
}

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
/// Creation is intentionally a placeholder this build: toolbar plus and the
/// dashed "New project" card both surface a coming-soon alert. Cards push
/// `.project(id)`.
struct ProjectsView: View {

    // MARK: Sample data

    private struct ProjectSample: Identifiable {
        let id: String
        let name: String
        let detail: String
        let meta: String
    }

    @State private var showComingAlert = false

    private let projects: [ProjectSample] = [
        .init(id: "project-brand", name: "Brand Refresh 2025", detail: "Repositioning, voice guidelines and the new visual identity.", meta: "8 chats · 14 files · 3 members · 2h ago"),
        .init(id: "project-launch", name: "Q3 Launch Plan", detail: "Go-to-market plan, comms calendar and the launch-day runbook.", meta: "5 chats · 9 files · 2 members · 1d ago"),
        .init(id: "project-research", name: "Research: AI market", detail: "Market sizing, competitor scan and a living source library.", meta: "12 chats · 21 files · 4 members · 3d ago")
    ]

    // MARK: Body

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Aero.Spacing.l) {
                StaggerIn(index: 0) { header }
                StaggerIn(index: 1) { newProjectCard }
                StaggerIn(index: 2) { projectList }
            }
            .padding(.horizontal, Aero.Spacing.m)
            .padding(.top, Aero.Spacing.s)
            .padding(.bottom, Aero.Spacing.xl)
        }
        .background(Aero.background.ignoresSafeArea())
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button {
                    showComingAlert = true
                } label: {
                    Image(systemName: "plus.circle")
                        .font(.system(size: 17, weight: .medium))
                        .foregroundStyle(Aero.text)
                }
                .buttonStyle(KineticPressStyle())
                .accessibilityLabel("New project")
            }
        }
        .alert("Projects come alive in the next build", isPresented: $showComingAlert) {
            Button("OK", role: .cancel) {}
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

    // MARK: New project (dashed outline card)

    private var newProjectCard: some View {
        Button {
            showComingAlert = true
        } label: {
            HStack(spacing: Aero.Spacing.s) {
                Image(systemName: "plus")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Aero.accent)
                Text("New project")
                    .font(Aero.title())
                    .foregroundStyle(Aero.text)
                Spacer()
                Text("Coming soon")
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

    // MARK: Project cards

    private var projectList: some View {
        VStack(alignment: .leading, spacing: Aero.Spacing.m) {
            SectionHeader(title: "Your projects")
            VStack(spacing: Aero.Spacing.m) {
                ForEach(projects) { project in
                    NavigationLink(value: AeroRoute.project(project.id)) {
                        AeroCard {
                            VStack(alignment: .leading, spacing: 6) {
                                HStack(spacing: Aero.Spacing.s) {
                                    Text(project.name)
                                        .font(Aero.title())
                                        .foregroundStyle(Aero.text)
                                        .lineLimit(1)
                                    Spacer(minLength: 0)
                                    Image(systemName: "chevron.right")
                                        .font(.system(size: 12, weight: .semibold))
                                        .foregroundStyle(Aero.textMuted)
                                }
                                Text(project.detail)
                                    .font(Aero.caption())
                                    .foregroundStyle(Aero.textMuted)
                                    .fixedSize(horizontal: false, vertical: true)
                                Text(project.meta)
                                    .font(Aero.label())
                                    .foregroundStyle(Aero.textMuted)
                            }
                        }
                    }
                    .buttonStyle(KineticPressStyle())
                }
            }
        }
    }
}

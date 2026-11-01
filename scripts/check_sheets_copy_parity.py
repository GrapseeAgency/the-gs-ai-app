#!/usr/bin/env python3
"""check_sheets_copy_parity.py — the reference pages' copy inventory, on both
platforms.

WHY THIS EXISTS
---------------
The Android screens are covered by `ReferenceSheetsContentTest`, which reads
the real Compose semantics tree. iOS has no equivalent introspection, and a
render test only proves SOMETHING was drawn. A row can be missing, renamed or
quietly reordered on one platform and every capture still looks plausible.

This checks the inventory that must exist on BOTH sides: each section header,
each row title and each closing card. It is deliberately a presence check, not
a layout check — Android's semantics test owns order and state; the Python card
gate owns the rendered rhythm.

WHAT IT DOES NOT CHECK
----------------------
Body paragraphs, prices, counts and statuses: those are generated (PlanStore,
NoticeInbox, Store) and their single source is asserted by the platform tests.
Duplicating them here would freeze formatting that is not a mock contract.

Usage:
  check_sheets_copy_parity.py
  check_sheets_copy_parity.py --self-test
Exit: 0 every string found on both platforms, 1 a missing string, 2 bad input.
"""

import argparse
import re
import sys

SCREENS = {
    "privacy-data-controls": dict(
        android="android/app/src/main/java/com/grapsee/gsai/ui/settings/ReferenceSettingsScreens.kt",
        ios="ios/App/Sources/Features/Settings/PrivacyDataControlsView.swift",
        copy=[
            "Privacy & Data Controls",
            "Your data, your control",
            "Your privacy matters",
            "Nexly is designed with your privacy in mind. You're always in control of your data, conversations, and personal information.",
            "Data & Permissions",
            "Manage what Nexly can access",
            "App Permissions",
            "Microphone, notifications, Do-Not-Disturb and biometric unlock",
            "Personalization & Ads",
            "No ad profile is built from your activity",
            "Third-Party Integrations",
            "No services are connected",
            "Conversation & Activity",
            "Control your chat data",
            "Chat History",
            "View, search, and manage your conversations",
            "Search History",
            "Searches are not recorded",
            "Data Retention",
            "Choose how long we keep your data",
            "Export & Deletion",
            "Download or delete your data",
            "Export Your Data",
            "Download a copy of your information",
            "Delete Your Account",
            "Permanently remove everything this app stores on this device",
            "We respect your privacy",
            "Your data is private to this app, never sold, and only used to improve your experience.",
        ],
        # No source-order probe here: SwiftUI defines `hero` after `body`, so
        # literal order is not display order. Android's runtime content test
        # owns the order; this spec owns the two inventories.
    ),
    "notifications-alerts": dict(
        android="android/app/src/main/java/com/grapsee/gsai/ui/settings/NotificationsAlertsScreen.kt",
        ios="ios/App/Sources/Features/Notifications/NotificationsAlertsView.swift",
        copy=[
            "Notifications & Alerts",
            "Notification Types",
            "Choose what to receive",
            "New Messages",
            "AI Updates",
            "Tips & Suggestions",
            "Account Activity",
            "Product Updates",
            "Notification Channels",
            "How you want to be notified",
            "Push Notifications",
            "Email Notifications",
            "In-App Notifications",
            "Quiet Hours",
            "Pause notifications when you need rest",
            "Do Not Disturb",
            "You're in control.",
        ],
        forbidden=[],
        order=[
            "New Messages",
            "AI Updates",
            "Tips & Suggestions",
            "Account Activity",
            "Product Updates",
            "Push Notifications",
            "Email Notifications",
            "In-App Notifications",
            "Do Not Disturb",
        ],
    ),
    "notifications-inbox": dict(
        android="android/app/src/main/java/com/grapsee/gsai/ui/notifications/NotificationsInboxScreen.kt",
        ios="ios/App/Sources/Features/Notifications/NotificationsView.swift",
        copy=[
            "Notifications",
            "Your updates, alerts and messages",
            "Mark all as read",
            "Today",
            "Earlier",
            "You're all caught up!",
            "No more notifications for now.",
            "Messages",
            "Updates",
            "Account",
            "Billing",
        ],
    ),
    "account-security": dict(
        android="android/app/src/main/java/com/grapsee/gsai/ui/settings/AccountSecurityScreen.kt",
        ios="ios/App/Sources/Features/Account/AccountSecurityView.swift",
        copy=[
            "Account & Security",
            "Keep your account safe and in your control",
            "Sign-in Methods",
            "Manage how you sign in",
            "Grapsee Mail",
            "Add another sign-in method",
            "Password & Authentication",
            "Manage your security",
            "Change Password",
            "Two-Factor Authentication",
            "Biometric Login",
            "Active Sessions",
            "See where you're logged in",
            "Current Session",
            "Other Sessions",
            "Manage Devices",
            "Control your devices",
            "All Devices",
            "Your account is protected",
        ],
        order=[
            "Grapsee Mail",
            "Google",
            "Apple",
            "Add another sign-in method",
            "Change Password",
            "Two-Factor Authentication",
            "Biometric Login",
            "Current Session",
            "Other Sessions",
            "All Devices",
            "Your account is protected",
        ],
    ),
    "subscription-usage": dict(
        android="android/app/src/main/java/com/grapsee/gsai/ui/subscription/SubscriptionUsageScreen.kt",
        ios="ios/App/Sources/Features/Billing/SubscriptionUsageView.swift",
        copy=[
            "Subscription & Usage",
            "Your plan, usage, and billing details",
            "Plan Details",
            "Current plan information",
            "Plan Name",
            "Billing Cycle",
            "Next Billing Date",
            "Price",
            "Usage Overview",
            "Your current month's usage",
            "Messages",
            "File Uploads",
            "Advanced Reasoning",
            "Billing & Payment",
            "Manage your payment method",
            "Payment Method",
            "Billing History",
            "Upgrade Options",
            "Need help?",
        ],
        forbidden=["$19.99", "Visa"],
        order=[
            "Plan Details",
            "Usage Overview",
            "Billing & Payment",
            "Upgrade Options",
            "Need help?",
        ],
    ),
    "help-support": dict(
        android="android/app/src/main/java/com/grapsee/gsai/ui/support/HelpSupportScreen.kt",
        ios="ios/App/Sources/Features/Support/HelpSupportView.swift",
        copy=[
            "Help & Support",
            "Browse by Category",
            "FAQs",
            "Troubleshooting",
            "Account & Billing",
            "Privacy & Security",
            "Feature Guides",
            "Technical Support",
            "Popular Help Topics",
            "Still need help?",
            "Contact Support",
            "Report a Problem",
            "Live Chat",
            "Your satisfaction matters to us",
        ],
        forbidden=["Reset my password"],
        order=[
            "FAQs",
            "Troubleshooting",
            "Account & Billing",
            "Privacy & Security",
            "Feature Guides",
            "Technical Support",
            "Popular Help Topics",
            "Still need help?",
        ],
    ),
    "connected-apps": dict(
        android="android/app/src/main/java/com/grapsee/gsai/ui/integrations/ConnectedAppsScreen.kt",
        ios="ios/App/Sources/Features/Integrations/ConnectedAppsView.swift",
        copy=[
            "Connected Apps & Integrations",
            "More possibilities together",
            "Your Connected Apps",
            "Manage Permissions",
            "Google",
            "GitHub",
            "Notion",
            "Spotify",
            "Slack",
            "Microsoft",
            "Available Integrations",
            "Browse All",
            "Dropbox",
            "Figma",
            "Trello",
            "Discord",
            "Zoom",
            "Your data, your control",
            "Connect New App or Service",
        ],
        order=[
            "Google",
            "GitHub",
            "Notion",
            "Spotify",
            "Slack",
            "Microsoft",
            "Dropbox",
            "Figma",
            "Trello",
            "Discord",
            "Zoom",
            "Your data, your control",
            "Connect New App or Service",
        ],
    ),
}

# The two pending references that were Android-only until this pass. They use
# the settings kit rather than the owner sheets' denser kit, so they live here
# as their own entries: the same presence contract, none of the card geometry.
SCREENS["model-selection"] = dict(
    android="android/app/src/main/java/com/grapsee/gsai/ui/settings/ReferenceSettingsScreens.kt",
    ios="ios/App/Sources/Features/Settings/AiIntelligenceView.swift",
    copy=[
        "Model Selection & Reasoning Effort",
        "Choose the best model and how deeply Nexly thinks",
        "AI Model",
        "Different models for different needs",
        "Nexly Pro",
        "Best balance of speed, intelligence and creativity. Great for most everyday tasks.",
        "Recommended",
        "Nexly Advanced",
        "Deep reasoning, complex problem solving and analysis. Ideal for research, coding and detailed tasks.",
        "Nexly Code",
        "Specialized for programming and technical tasks. Great for developers and code-related work.",
        "Nexly Creative",
        "Focused on imagination, visuals and creative content. Best for design, writing and artistic tasks.",
        "Reasoning Effort",
        "Control how much Nexly thinks",
        "Minimal",
        "Quick answers for simple questions.",
        "~ 1–2 seconds",
        "Light",
        "Balanced speed and depth.",
        "~ 2–5 seconds",
        "Standard",
        "Best for most tasks and questions.",
        "~ 5–10 seconds",
        "High",
        "Deeper analysis and detailed reasoning.",
        "~ 10–30 seconds",
        "Model Preferences",
        "Customize your experience",
        "Auto-select model",
        "Nexly always chooses the best model for each task.",
        "Remember my choices",
        "Keep using your preferred model and reasoning effort.",
        "Reset to default",
        "Go back to Nexly's recommended settings.",
        "Smarter choices. Better conversations.",
        "The right model + the right thinking = the best Nexly experience.",
        "GS Intelligence",
        "One assistant, two engines. The cloud model handles depth; the on-device model keeps working offline.",
        "Nexly Cloud",
        "Full-quality answers through the GS backend",
        "On-device",
    ],
    # No source-order probe: Swift puts the model/effort tables above the view
    # that draws them, so literal order is not display order. Android's
    # ReferenceRowInteractionTest owns the rendered order and state.
)

SCREENS["chat-history-search"] = dict(
    android="android/app/src/main/java/com/grapsee/gsai/ui/chat/ChatSearchScreen.kt",
    ios="ios/App/Sources/Features/Chats/ChatSearchView.swift",
    copy=[
        "Chat History & Search",
        "Find, revisit, and manage your conversations",
        "Search your conversations…",
        "Total Conversations",
        "Your chat history with Nexly",
        "Last Active",
        "Most recent conversation",
        "Recent Chats",
        "View all",
        "No archived chats. Archive a conversation from its ⋮ menu and it waits here.",
        "No conversations yet. Your chats appear here as you start them.",
        "No chats match this filter. Try All, or clear the filters.",
        "Quick Actions",
        "Search Chats",
        "Find specific topics",
        "Manage History",
        "Delete or archive chats",
        "Saved items",
        "Your saved answers",
        "Export Chat History",
        "Download your data",
        "Start a new conversation",
        "Get back to what matters",
        "No matches",
        "Nothing matched",
        "Try a shorter word or clear the filters.",
        "Search runs across every conversation on this device. Nothing is sent to a server.",
        "Filter chats",
        "Archived conversations",
        "Only chats you have archived",
        "Has files",
        "Only chats with attachments",
        "Clear filters",
        "Chat actions",
    ],
)
SCREENS["voice-speech"] = dict(
    android="android/app/src/main/java/com/grapsee/gsai/ui/settings/ReferenceSettingsScreens.kt",
    ios="ios/App/Sources/Features/Settings/VoiceSpeechView.swift",
    copy=[
        "Voice & Speech",
        "Listen, speak, and keep your hands free.",
        "Read aloud",
        "Answers can be spoken with your device's speech engine. Voice mode listens when you tap the mic.",
        "Voice",
        "Open voice mode",
        "Speak a question instead of typing it",
        "Speech engine",
        "Device default",
        "Haptic confirmation",
        "A tick when dictation starts and stops",
        "Speech is synthesized locally on this device. Audio is not uploaded to GS.",
    ],
)

SCREENS["new-chat"] = dict(
    android="android/app/src/main/java/com/grapsee/gsai/ui/home/NewChatScreen.kt",
    ios="ios/App/Sources/Features/Home/NewChatView.swift",
    copy=[
        "Start a New Chat",
        "Ask anything, get instant answers,",
        "or explore something new.",
        "Type your message...",
        "Web Search",
        "Write",
        "Analyze",
        "Image",
        "More",
        "Recent Chats",
        "View all",
        "Explore AI Models",
        "Choose the right model for your needs.",
        "Nexly",
        "Your AI companion.",
        "Always.",
    ],
)

SCREENS["model-picker"] = dict(
    android="android/app/src/main/java/com/grapsee/gsai/ui/settings/ModelSelectionScreen.kt",
    ios="ios/App/Sources/Features/Settings/ModelSelectionView.swift",
    copy=[
        "Model Selection",
        "Choose the best model for your needs.",
        "Available Models",
        "Recommended",
        # The four model titles are generated from the GsModel enum on both
        # platforms, so they live in ModelRouter rather than these files; the
        # interaction test asserts them on the rendered page instead.
        "Our most capable model. Great for complex tasks, creative work and deep analysis.",
        "Fast, efficient and perfect for everyday questions and quick tasks.",
        "Advanced image understanding and multimodal capabilities.",
        "Specialized for programming, debugging and technical tasks.",
        "Best for complex tasks",
        "Creative",
        "Balanced",
        "Efficient",
        "Everyday",
        "Multimodal",
        "Coding",
        "Debugging",
        "Technical",
        "Reasoning Effort",
        "Control how much time and thought the model spends on its response.",
        "Minimal",
        "Medium",
        "Auto Select Model",
        "Let Nexly choose the best model for your message.",
        "Continue",
    ],
)

SCREENS["data-export"] = dict(
    android="android/app/src/main/java/com/grapsee/gsai/ui/settings/DataExportAccountDeletionScreen.kt",
    ios="ios/App/Sources/Features/Settings/DataExportAccountDeletionView.swift",
    copy=[
        "Data Export & Account Deletion",
        "Take control of your data. Export what you need or delete your account when you're ready.",
        "Export Your Data",
        "Download your information",
        "Export Conversations",
        "Download your chat history, messages, and attachment details.",
        "Personal Data Archive",
        "Get a copy of your account data, profile information, and settings.",
        "Export Format",
        "Choose your preferred file format for export.",
        "Download Complete Archive",
        "Saved to a file you choose on this device. Large exports may take some time.",
        "Save Archive",
        "Data Retention",
        "Control how long we keep your data",
        "Data Retention Period",
        "Choose how long to keep conversations you have not opened.",
        "Auto-Delete Inactive Data",
        "Automatically delete conversations after the retention period.",
        "Clear Chat History",
        "Permanently remove all conversations on this device.",
        "Clear History",
        "Delete Your Account",
        "This action is permanent",
        "Permanently Delete Account",
        "I understand that this action is permanent and I want to delete my account.",
        "Delete Account",
    ],
)


def read(path):
    try:
        with open(path, encoding="utf-8") as handle:
            source = handle.read()
    except OSError as exc:
        raise SystemExit(f"::error::cannot read {path}: {exc}")
    # A string in a comment is not a row. Strip comments before searching so a
    # doc comment that names the sheet cannot keep a deleted row's check green.
    source = re.sub(r"/\*.*?\*/", "", source, flags=re.S)
    source = re.sub(r"//[^\n]*", "", source)
    return source


def missing_in(text, copy):
    return [item for item in copy if item not in text]


def out_of_order(text, order):
    """Strings whose first occurrence moved backwards in the source.

    Only used where the literals appear once, in the view body, so a hit is a
    row that was actually reordered rather than a mapping table elsewhere.
    """
    last = -1
    moved = []
    for item in order:
        index = text.find(item)
        if index < 0:
            continue
        if index < last:
            moved.append(item)
        else:
            last = index
    return moved


def run(extra=None, forbidden_extra=None):
    """Returns (screen, platform, path, missing, forbidden, moved) rows."""
    problems = []
    for screen, spec in SCREENS.items():
        copy = list(spec["copy"])
        if extra:
            copy.append(extra)
        forbidden = list(spec.get("forbidden", []))
        if forbidden_extra:
            forbidden.append(forbidden_extra)
        for platform in ("android", "ios"):
            path = spec[platform]
            text = read(path)
            absent = missing_in(text, copy)
            stale = [item for item in forbidden if item in text]
            moved = out_of_order(text, spec.get("order", []))
            if absent or stale or moved:
                problems.append((screen, platform, path, absent, stale, moved))
    return problems


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument(
        "--self-test",
        action="store_true",
        help="prove the checker can fail by requiring an impossible string",
    )
    args = ap.parse_args()

    problems = run()
    if problems:
        for screen, platform, path, absent, stale, moved in problems:
            if absent:
                print(
                    f"::error::{screen} [{platform}] is missing "
                    f"{len(absent)} mock string(s) in {path}: {absent}"
                )
            if stale:
                print(
                    f"::error::{screen} [{platform}] still draws the mock's "
                    f"unbacked value(s) in {path}: {stale}"
                )
            if moved:
                print(
                    f"::error::{screen} [{platform}] draws these rows out of "
                    f"the mock's order in {path}: {moved}"
                )
        return 1

    if args.self_test:
        marker = "THIS-STRING-IS-NOT-ON-ANY-SHEET-9F3C"
        problems = run(extra=marker)
        if not problems:
            print("::error::self-test could not make the missing-copy probe fail")
            return 1
        print(
            f"self-test ok — the missing-copy probe went red on "
            f"{len(problems)} skin(s)"
        )

        # The honesty half: a value the build cannot back must be detectable.
        present = SCREENS["notifications-alerts"]["copy"][0]
        problems = run(forbidden_extra=present)
        if not any(stale for _, _, _, _, stale, _ in problems):
            print("::error::self-test could not make the forbidden-copy probe fail")
            return 1
        print("self-test ok — the forbidden-copy probe went red too")

        if not out_of_order("alpha beta gamma", ["gamma", "alpha"]):
            print("::error::self-test could not make the order probe fail")
            return 1
        print("self-test ok — the order probe went red too")

    print(f"ok: {len(SCREENS)} sheet(s) carry their full copy inventory on both platforms")
    return 0


if __name__ == "__main__":
    sys.exit(main())

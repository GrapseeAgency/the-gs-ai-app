#!/usr/bin/env python3
"""check_sheets_copy_parity.py — the six owner sheets' copy inventory, on both
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

from __future__ import annotations

import re

from darwin.models import AutomationPlan, OpenAppAction, TextAction

BLOCKED_PACKAGES = {
    "com.android.settings.deviceinfo",
    "com.google.android.factoryreset",
}

SECRET_PATTERN = re.compile(
    r"(?:password|passwd|passcode|\bpin\b|otp|one[- ]?time|secret|token|cvv)",
    re.IGNORECASE,
)


class PolicyViolation(ValueError):
    """Raised when a plan violates Darwin's local safety policy."""


def validate_plan(plan: AutomationPlan, max_actions: int) -> None:
    if len(plan.actions) > max_actions:
        raise PolicyViolation(f"Plan has {len(plan.actions)} actions; maximum is {max_actions}.")

    for action in plan.actions:
        if isinstance(action, OpenAppAction) and action.package in BLOCKED_PACKAGES:
            raise PolicyViolation(f"Package is blocked by policy: {action.package}")
        if isinstance(action, TextAction) and SECRET_PATTERN.search(action.value):
            raise PolicyViolation("Text action appears to contain or request a credential/secret.")

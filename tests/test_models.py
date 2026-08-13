import pytest
from pydantic import ValidationError

from darwin.models import AutomationPlan


def test_plan_parses_discriminated_actions() -> None:
    plan = AutomationPlan.model_validate(
        {
            "goal": "Open settings and return home",
            "target_package": "com.android.settings",
            "actions": [
                {"type": "open_app", "package": "com.android.settings"},
                {"type": "key", "key": "HOME"},
            ],
        }
    )
    assert [action.type for action in plan.actions] == ["open_app", "key"]


def test_arbitrary_shell_action_is_rejected() -> None:
    with pytest.raises(ValidationError):
        AutomationPlan.model_validate(
            {"goal": "Run a shell command", "actions": [{"type": "shell", "value": "id"}]}
        )

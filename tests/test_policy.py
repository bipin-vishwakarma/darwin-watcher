import pytest

from darwin.models import AutomationPlan, TextAction, WaitAction
from darwin.policy import PolicyViolation, validate_plan


def test_credential_like_text_is_blocked() -> None:
    plan = AutomationPlan(goal="Enter a test value", actions=[TextAction(value="password 123")])
    with pytest.raises(PolicyViolation):
        validate_plan(plan, max_actions=12)


def test_max_action_limit_is_enforced() -> None:
    plan = AutomationPlan(
        goal="Wait repeatedly",
        actions=[WaitAction(seconds=0) for _ in range(3)],
    )
    with pytest.raises(PolicyViolation):
        validate_plan(plan, max_actions=2)

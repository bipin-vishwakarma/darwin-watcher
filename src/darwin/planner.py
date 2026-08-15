from __future__ import annotations

from darwin.models import (
    AutomationPlan,
    KeyAction,
    OpenAppAction,
    UnlockAction,
    WaitAction,
    WakeAction,
)


def demo_plan(package: str) -> AutomationPlan:
    return AutomationPlan(
        goal=f"Wake the device, open {package}, wait briefly, and return home.",
        target_package=package,
        actions=[
            WakeAction(),
            UnlockAction(),
            OpenAppAction(package=package),
            WaitAction(seconds=2),
            KeyAction(key="HOME"),
        ],
    )


def smart_plan(goal: str, package: str | None, model: str) -> AutomationPlan:
    """Generate a typed plan while keeping execution constrained by local policy."""

    try:
        from langchain_openai import ChatOpenAI
    except ImportError as exc:
        raise RuntimeError('Smart planning requires: pip install -e ".[ai]"') from exc

    system = (
        "You are Darwin's Android test planner. Produce a short AutomationPlan. "
        "Only automate an authorized test device. Never bypass authentication, enter "
        "credentials, purchase anything, change accounts, delete data, or modify security "
        "settings. Prefer wake, unlock, open_app, wait, and navigation keys. Coordinates "
        "are allowed only when the user supplied enough context. Keep plans under 12 actions."
    )
    request = goal
    if package:
        request += f"\nThe permitted target package is {package}."

    llm = ChatOpenAI(model=model, temperature=0).with_structured_output(AutomationPlan)
    plan = llm.invoke([("system", system), ("human", request)])
    if not isinstance(plan, AutomationPlan):
        plan = AutomationPlan.model_validate(plan)
    if package:
        if plan.target_package not in (None, package):
            raise RuntimeError("Planner returned a target package outside the requested target.")
        if any(isinstance(action, OpenAppAction) and action.package != package for action in plan.actions):
            raise RuntimeError("Planner attempted to open a package outside the requested target.")
    return plan

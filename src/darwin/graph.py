from __future__ import annotations

from typing import TypedDict

from langgraph.graph import END, START, StateGraph

from darwin.adb import AdbClient, AdbError
from darwin.models import ActionResult, AutomationPlan
from darwin.policy import validate_plan


class DarwinState(TypedDict, total=False):
    plan: AutomationPlan
    approved: bool
    dry_run: bool
    device_serial: str | None
    device_state: str
    results: list[ActionResult]
    foreground_package: str | None
    error: str | None


def build_graph(client: AdbClient, max_actions: int = 12):
    def validate(state: DarwinState) -> DarwinState:
        try:
            validate_plan(state["plan"], max_actions=max_actions)
            return {"error": None}
        except ValueError as exc:
            return {"error": str(exc)}

    def inspect_device(state: DarwinState) -> DarwinState:
        if state.get("dry_run", True):
            return {"device_state": "dry-run"}
        devices = client.devices()
        if client.serial:
            matching = [status for serial, status in devices if serial == client.serial]
            if not matching:
                return {"error": f"Device not found: {client.serial}"}
            return {"device_state": matching[0]}
        ready = [serial for serial, status in devices if status == "device"]
        if len(ready) != 1:
            return {"error": "Connect exactly one authorized device or pass --serial."}
        client.serial = ready[0]
        return {"device_serial": ready[0], "device_state": "device"}

    def execute(state: DarwinState) -> DarwinState:
        if not state.get("dry_run", True) and not state.get("approved", False):
            return {"error": "Real execution was not approved."}
        results: list[ActionResult] = []
        for index, action in enumerate(state["plan"].actions, start=1):
            try:
                detail = client.execute(action)
                results.append(
                    ActionResult(index=index, action=action.type, ok=True, detail=detail or "OK")
                )
            except AdbError as exc:
                results.append(
                    ActionResult(index=index, action=action.type, ok=False, detail=str(exc))
                )
                return {"results": results, "error": f"Action {index} failed: {exc}"}
        return {"results": results}

    def verify(state: DarwinState) -> DarwinState:
        if state.get("dry_run", True):
            return {}
        try:
            return {"foreground_package": client.foreground_package()}
        except AdbError as exc:
            return {"error": f"Verification failed: {exc}"}

    def after_validate(state: DarwinState) -> str:
        return "stop" if state.get("error") else "continue"

    def after_inspect(state: DarwinState) -> str:
        return "stop" if state.get("error") else "continue"

    workflow = StateGraph(DarwinState)
    workflow.add_node("validate", validate)
    workflow.add_node("inspect_device", inspect_device)
    workflow.add_node("execute", execute)
    workflow.add_node("verify", verify)
    workflow.add_edge(START, "validate")
    workflow.add_conditional_edges(
        "validate", after_validate, {"continue": "inspect_device", "stop": END}
    )
    workflow.add_conditional_edges(
        "inspect_device", after_inspect, {"continue": "execute", "stop": END}
    )
    workflow.add_edge("execute", "verify")
    workflow.add_edge("verify", END)
    return workflow.compile()


def graph_mermaid() -> str:
    client = AdbClient(dry_run=True)
    return build_graph(client).get_graph().draw_mermaid()

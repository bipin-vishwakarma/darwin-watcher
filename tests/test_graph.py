from typing import Any

from darwin.adb import AdbClient, AdbError
from darwin.graph import build_graph
from darwin.models import Action
from darwin.planner import demo_plan


class FakeClient(AdbClient):
    def __init__(self, *, state: str = "device", fail_execute: bool = False) -> None:
        super().__init__(serial="emulator-5554", dry_run=False)
        self.state = state
        self.fail_execute = fail_execute
        self.executed = False
        self.verified = False

    def devices(self) -> list[tuple[str, str]]:
        return [("emulator-5554", self.state)]

    def execute(self, action: Action) -> str:
        self.executed = True
        if self.fail_execute:
            raise AdbError("boom")
        return "OK"

    def foreground_package(self) -> str | None:
        self.verified = True
        return "com.android.settings"


def invoke(client: AdbClient, *, dry_run: bool) -> dict[str, Any]:
    return build_graph(client).invoke(
        {
            "plan": demo_plan("com.android.settings"),
            "approved": True,
            "dry_run": dry_run,
            "results": [],
        }
    )


def test_graph_executes_demo_as_dry_run() -> None:
    result = invoke(AdbClient(dry_run=True), dry_run=True)
    assert result.get("error") is None
    assert len(result["results"]) == 5
    assert all(item.ok for item in result["results"])


def test_graph_rejects_specified_device_that_is_not_ready() -> None:
    client = FakeClient(state="unauthorized")
    result = invoke(client, dry_run=False)
    assert result["error"] == "Device is not ready: emulator-5554 is unauthorized"
    assert not client.executed


def test_graph_stops_after_action_failure() -> None:
    client = FakeClient(fail_execute=True)
    result = invoke(client, dry_run=False)
    assert result["error"] == "Action 1 failed: boom"
    assert not client.verified

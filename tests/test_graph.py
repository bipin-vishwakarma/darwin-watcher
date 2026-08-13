from darwin.adb import AdbClient
from darwin.graph import build_graph
from darwin.planner import demo_plan


def test_graph_executes_demo_as_dry_run() -> None:
    client = AdbClient(dry_run=True)
    result = build_graph(client).invoke(
        {
            "plan": demo_plan("com.android.settings"),
            "approved": True,
            "dry_run": True,
            "results": [],
        }
    )
    assert result.get("error") is None
    assert len(result["results"]) == 5
    assert all(item.ok for item in result["results"])

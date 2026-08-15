from __future__ import annotations

import json
from pathlib import Path
from typing import Annotated

import typer
from pydantic import ValidationError
from rich.console import Console
from rich.panel import Panel
from rich.table import Table

from darwin.adb import AdbClient
from darwin.config import Settings
from darwin.graph import build_graph, graph_mermaid
from darwin.models import AutomationPlan
from darwin.planner import demo_plan, smart_plan
from darwin.policy import PolicyViolation, validate_plan

app = typer.Typer(
    name="darwin",
    help="Safe, LangGraph-powered Android automation through ADB.",
    no_args_is_help=True,
)
console = Console()


def show_plan(plan: AutomationPlan) -> None:
    table = Table(title=plan.goal)
    table.add_column("#", justify="right")
    table.add_column("Action")
    table.add_column("Parameters")
    for index, action in enumerate(plan.actions, start=1):
        payload = action.model_dump(exclude={"type"}, exclude_none=True)
        table.add_row(str(index), action.type, json.dumps(payload) if payload else "-")
    console.print(table)


def execute_plan(
    plan: AutomationPlan,
    execute: bool,
    yes: bool,
    serial: str | None,
    adb_path: str | None,
) -> None:
    settings = Settings()
    dry_run = not execute
    client = AdbClient(
        executable=adb_path or settings.adb_path,
        serial=serial or settings.device_serial,
        dry_run=dry_run,
    )
    validate_plan(plan, settings.max_actions)
    show_plan(plan)
    approved = dry_run
    if execute:
        approved = yes or typer.confirm("Execute this plan on the connected device?")
        if not approved:
            raise typer.Abort()

    result = build_graph(client, settings.max_actions).invoke(
        {
            "plan": plan,
            "approved": approved,
            "dry_run": dry_run,
            "device_serial": client.serial,
            "results": [],
        }
    )

    result_table = Table(title="Execution report" if execute else "Dry-run report")
    result_table.add_column("#")
    result_table.add_column("Action")
    result_table.add_column("Status")
    result_table.add_column("Detail")
    for item in result.get("results", []):
        result_table.add_row(
            str(item.index), item.action, "OK" if item.ok else "FAILED", item.detail
        )
    console.print(result_table)
    if result.get("foreground_package"):
        console.print(f"Foreground package: [cyan]{result['foreground_package']}[/cyan]")
    if result.get("error"):
        console.print(f"[red]{result['error']}[/red]")
        raise typer.Exit(code=1)


@app.command()
def doctor(
    serial: Annotated[str | None, typer.Option(help="Target device serial.")] = None,
    adb_path: Annotated[str | None, typer.Option(help="Path or name of adb.")] = None,
) -> None:
    """Check local ADB availability and connected devices."""
    settings = Settings()
    client = AdbClient(
        executable=adb_path or settings.adb_path,
        serial=serial or settings.device_serial,
    )
    if not client.is_available():
        console.print(
            Panel("ADB was not found. Install Android Platform Tools and add adb to PATH.")
        )
        raise typer.Exit(code=1)
    devices = client.devices()
    console.print("[green]ADB is available.[/green]")
    if not devices:
        console.print("No devices detected. Connect an authorized device or start an emulator.")
    for serial_name, state in devices:
        console.print(f"- {serial_name}: {state}")


@app.command("demo")
def demo_command(
    package: Annotated[
        str, typer.Option(help="Android application package name.")
    ] = "com.android.settings",
    execute: Annotated[
        bool, typer.Option(help="Run real ADB actions; default is dry-run.")
    ] = False,
    yes: Annotated[bool, typer.Option("--yes", help="Skip the execution confirmation.")] = False,
    serial: Annotated[str | None, typer.Option(help="Target device serial.")] = None,
    adb_path: Annotated[str | None, typer.Option(help="Path or name of adb.")] = None,
) -> None:
    """Preview or execute the built-in wake/open/home workflow."""
    execute_plan(demo_plan(package), execute, yes, serial, adb_path)


@app.command("run")
def run_command(
    plan_file: Annotated[Path, typer.Argument(exists=True, dir_okay=False, readable=True)],
    execute: Annotated[
        bool, typer.Option(help="Run real ADB actions; default is dry-run.")
    ] = False,
    yes: Annotated[bool, typer.Option("--yes", help="Skip the execution confirmation.")] = False,
    serial: Annotated[str | None, typer.Option(help="Target device serial.")] = None,
    adb_path: Annotated[str | None, typer.Option(help="Path or name of adb.")] = None,
) -> None:
    """Preview or execute a typed JSON automation plan."""
    plan = AutomationPlan.model_validate_json(plan_file.read_text(encoding="utf-8"))
    execute_plan(plan, execute, yes, serial, adb_path)


@app.command("smart")
def smart_command(
    goal: Annotated[str, typer.Argument(help="Plain-English automation goal.")],
    package: Annotated[str | None, typer.Option(help="Only package the planner may open.")] = None,
    model: Annotated[str | None, typer.Option(help="OpenAI model name.")] = None,
    execute: Annotated[
        bool, typer.Option(help="Run real ADB actions; default is dry-run.")
    ] = False,
    yes: Annotated[bool, typer.Option("--yes", help="Skip the execution confirmation.")] = False,
    serial: Annotated[str | None, typer.Option(help="Target device serial.")] = None,
    adb_path: Annotated[str | None, typer.Option(help="Path or name of adb.")] = None,
) -> None:
    """Generate a constrained plan with an LLM, then preview or execute it."""
    settings = Settings()
    plan = smart_plan(goal, package, model or settings.model)
    execute_plan(plan, execute, yes, serial, adb_path)


@app.command("graph")
def graph_command() -> None:
    """Print the LangGraph workflow as Mermaid markup."""
    console.print(graph_mermaid())


def main() -> None:
    try:
        app()
    except (ValidationError, PolicyViolation, RuntimeError) as exc:
        console.print(f"[red]Error:[/red] {exc}")
        raise typer.Exit(code=1) from exc

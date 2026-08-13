from __future__ import annotations

from typing import Annotated, Literal

from pydantic import BaseModel, Field, TypeAdapter


class WakeAction(BaseModel):
    type: Literal["wake"] = "wake"


class UnlockAction(BaseModel):
    type: Literal["unlock"] = "unlock"


class OpenAppAction(BaseModel):
    type: Literal["open_app"] = "open_app"
    package: str = Field(pattern=r"^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$")


class TapAction(BaseModel):
    type: Literal["tap"] = "tap"
    x: int = Field(ge=0, le=10000)
    y: int = Field(ge=0, le=10000)


class SwipeAction(BaseModel):
    type: Literal["swipe"] = "swipe"
    x1: int = Field(ge=0, le=10000)
    y1: int = Field(ge=0, le=10000)
    x2: int = Field(ge=0, le=10000)
    y2: int = Field(ge=0, le=10000)
    duration_ms: int = Field(default=300, ge=50, le=3000)


class TextAction(BaseModel):
    type: Literal["text"] = "text"
    value: str = Field(min_length=1, max_length=200)


class KeyAction(BaseModel):
    type: Literal["key"] = "key"
    key: Literal["BACK", "HOME", "ENTER", "TAB", "DPAD_UP", "DPAD_DOWN"]


class WaitAction(BaseModel):
    type: Literal["wait"] = "wait"
    seconds: float = Field(ge=0, le=10)


Action = Annotated[
    WakeAction
    | UnlockAction
    | OpenAppAction
    | TapAction
    | SwipeAction
    | TextAction
    | KeyAction
    | WaitAction,
    Field(discriminator="type"),
]

action_adapter = TypeAdapter(Action)


class AutomationPlan(BaseModel):
    """A bounded set of actions Darwin is permitted to execute."""

    goal: str = Field(min_length=3, max_length=500)
    target_package: str | None = Field(
        default=None,
        pattern=r"^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$",
    )
    actions: list[Action] = Field(min_length=1, max_length=50)


class DeviceStatus(BaseModel):
    serial: str
    state: str
    model: str | None = None
    foreground_package: str | None = None


class ActionResult(BaseModel):
    index: int
    action: str
    ok: bool
    detail: str

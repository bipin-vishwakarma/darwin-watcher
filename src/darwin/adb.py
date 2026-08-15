import os
import re
import shutil
import subprocess
import time
from dataclasses import dataclass
from pathlib import Path

from darwin.models import (
    Action,
    KeyAction,
    OpenAppAction,
    SwipeAction,
    TapAction,
    TextAction,
    UnlockAction,
    WaitAction,
    WakeAction,
)


class AdbError(RuntimeError):
    """Raised when ADB cannot complete a requested operation."""


def resolve_adb_path(executable: str = "adb") -> str:
    """Find the full path to adb if executable is not directly in PATH."""
    found = shutil.which(executable)
    if found:
        return found
    p = Path(executable)
    if p.is_file():
        return str(p.resolve())

    for var in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        val = os.environ.get(var)
        if val:
            candidate = Path(val) / "platform-tools" / ("adb.exe" if os.name == "nt" else "adb")
            if candidate.is_file():
                return str(candidate.resolve())

    if os.name == "nt":
        local_app = os.environ.get("LOCALAPPDATA")
        if local_app:
            candidate = Path(local_app) / "Android" / "Sdk" / "platform-tools" / "adb.exe"
            if candidate.is_file():
                return str(candidate.resolve())
        for drive in ("C", "D"):
            candidate = Path(f"{drive}:/platform-tools/adb.exe")
            if candidate.is_file():
                return str(candidate.resolve())
    else:
        for candidate in (
            Path.home() / "Android" / "Sdk" / "platform-tools" / "adb",
            Path.home() / "Library" / "Android" / "sdk" / "platform-tools" / "adb",
        ):
            if candidate.is_file():
                return str(candidate.resolve())

    return executable


@dataclass(slots=True)
class AdbClient:
    executable: str = "adb"
    serial: str | None = None
    dry_run: bool = True

    def _resolved_executable(self) -> str:
        return resolve_adb_path(self.executable)

    def _base_command(self) -> list[str]:
        command = [self._resolved_executable()]
        if self.serial:
            command.extend(["-s", self.serial])
        return command

    def is_available(self) -> bool:
        resolved = self._resolved_executable()
        return bool(shutil.which(resolved) or Path(resolved).is_file())

    def connect_if_needed(self) -> None:
        if self.serial and re.match(r"^\d+\.\d+\.\d+\.\d+:\d+$", self.serial):
            current_devices = [s for s, _ in self._fetch_devices()]
            if self.serial not in current_devices:
                subprocess.run(
                    [self._resolved_executable(), "connect", self.serial],
                    check=False,
                    capture_output=True,
                    text=True,
                    timeout=10,
                )

    def _fetch_devices(self) -> list[tuple[str, str]]:
        if not self.is_available():
            return []
        completed = subprocess.run(
            [self._resolved_executable(), "devices"],
            check=False,
            capture_output=True,
            text=True,
            timeout=10,
        )
        if completed.returncode != 0:
            output = (completed.stderr or completed.stdout).strip()
            raise AdbError(output or f"ADB exited with code {completed.returncode}")
        devices: list[tuple[str, str]] = []
        found_header = False
        for line in completed.stdout.splitlines():
            line = line.strip()
            if not line or line.startswith("*"):
                continue
            if line.startswith("List of devices attached"):
                found_header = True
                continue
            if found_header:
                parts = line.split()
                if len(parts) >= 2:
                    devices.append((parts[0], parts[1]))
        return devices

    def devices(self) -> list[tuple[str, str]]:
        self.connect_if_needed()
        return self._fetch_devices()

    def run(self, *args: str, timeout: float = 20) -> str:
        self.connect_if_needed()
        command = [*self._base_command(), *args]
        if self.dry_run:
            return f"DRY RUN: {subprocess.list2cmdline(command)}"
        if not self.is_available():
            raise AdbError(f"ADB executable not found: {self.executable}")
        completed = subprocess.run(
            command,
            check=False,
            capture_output=True,
            text=True,
            timeout=timeout,
        )
        output = (completed.stdout or completed.stderr).strip()
        if completed.returncode != 0:
            raise AdbError(output or f"ADB exited with code {completed.returncode}")
        return output

    def foreground_package(self) -> str | None:
        output = self.run("shell", "dumpsys", "window", "windows")
        if output.startswith("DRY RUN"):
            return None
        match = re.search(r"(?:mCurrentFocus|mFocusedApp).*? ([\w.]+)/", output)
        return match.group(1) if match else None

    def execute(self, action: Action) -> str:
        if isinstance(action, WakeAction):
            return self.run("shell", "input", "keyevent", "KEYCODE_WAKEUP")
        if isinstance(action, UnlockAction):
            # This reveals the lock screen and performs a normal upward swipe.
            # It cannot and must not bypass a secure credential challenge.
            self.run("shell", "wm", "dismiss-keyguard")
            return self.run("shell", "input", "swipe", "500", "1800", "500", "500", "300")
        if isinstance(action, OpenAppAction):
            return self.run(
                "shell",
                "monkey",
                "-p",
                action.package,
                "-c",
                "android.intent.category.LAUNCHER",
                "1",
            )
        if isinstance(action, TapAction):
            return self.run("shell", "input", "tap", str(action.x), str(action.y))
        if isinstance(action, SwipeAction):
            return self.run(
                "shell",
                "input",
                "swipe",
                str(action.x1),
                str(action.y1),
                str(action.x2),
                str(action.y2),
                str(action.duration_ms),
            )
        if isinstance(action, TextAction):
            encoded = action.value.replace("%", "%25").replace(" ", "%s")
            return self.run("shell", "input", "text", encoded)
        if isinstance(action, KeyAction):
            return self.run("shell", "input", "keyevent", f"KEYCODE_{action.key}")
        if isinstance(action, WaitAction):
            if self.dry_run:
                return f"DRY RUN: wait {action.seconds:g}s"
            time.sleep(action.seconds)
            return f"Waited {action.seconds:g}s"
        raise AdbError(f"Unsupported action: {action}")

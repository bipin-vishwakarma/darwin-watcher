# Darwin

Darwin is a safe-by-default Android automation agent. It uses **LangGraph 1.2**
to validate a goal, check an Android device, build or load an action plan,
execute it through ADB, and verify the final foreground app.

The first milestone can:

- wake and swipe-unlock a device that has no secure lock, or one already made
  accessible by Android's supported trust mechanisms;
- open an app by Android package name;
- perform a small allow-listed set of actions: tap, swipe, text input, key
  presses, and waits;
- run deterministic JSON plans or optionally use an LLM to turn a plain-English
  goal into the same constrained schema;
- default to a dry run and require explicit confirmation before real actions.

Darwin deliberately does **not** bypass a PIN, password, pattern, biometric
lock, factory-reset protection, or app security. Use it only on devices and
apps you own or are authorized to test.

## Architecture

```text
goal / JSON plan
      |
      v
validate -> inspect device -> plan -> approve -> execute -> verify
                 |                         |
                 +------ ADB adapter ------+
```

LangGraph owns orchestration and state. The ADB adapter owns device I/O. The
policy layer rejects dangerous packages, shell escape hatches, secret-looking
text, unsupported actions, and overlong plans.

## Requirements

- Python 3.11+
- Android Platform Tools (`adb` on `PATH`)
- an Android device or emulator with USB debugging enabled
- authorization for the device and target app

For physical devices, enable Developer options and USB debugging, connect the
device, and accept its RSA debugging prompt. Keep real credentials out of plans
and environment files.

## Setup

```powershell
py -3.13 -m venv .venv
.venv\Scripts\Activate.ps1
python -m pip install -e ".[dev]"
adb devices
```

Preview the built-in demo safely:

```powershell
darwin demo --package com.android.settings
```

Execute it after inspecting the preview:

```powershell
darwin demo --package com.android.settings --execute
```

The demo wakes the device, requests a swipe unlock, opens the target package,
waits, and presses Home. `--execute` still asks for confirmation unless `--yes`
is supplied.

## Run a deterministic plan

Copy `examples/basic_plan.json`, update the package and coordinates for your
test device, then run:

```powershell
darwin run examples/basic_plan.json
darwin run examples/basic_plan.json --execute
```

Coordinates differ by resolution and orientation. Prefer emulator snapshots or
a dedicated test device with stable display settings.

## Optional smart planning

Install the AI extra and configure an OpenAI API key:

```powershell
python -m pip install -e ".[ai,dev]"
$env:OPENAI_API_KEY = "your-key"
darwin smart "Open Android settings, wait two seconds, then go home" --package com.android.settings
darwin smart "Open Android settings, wait two seconds, then go home" --package com.android.settings --execute
```

The model can only return Darwin's typed, allow-listed actions; local policy
validation still runs before execution. Review generated plans carefully.

## Useful commands

```powershell
darwin doctor
darwin graph
darwin --help
```

Use `--serial` when more than one device is connected. You can also set
`DARWIN_DEVICE_SERIAL`.

## Development

```powershell
ruff check .
pytest
```

## Roadmap

- UI hierarchy inspection and semantic element targeting
- screenshot-based verification
- reusable workflows and checkpoints
- Appium driver for cross-platform test suites
- human approval UI and execution audit reports


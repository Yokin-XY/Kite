# Kite Home Card Schema

This file is the practical authoring guide for Kite home cards.

Use it when a human, Hermes, or another AI needs to write a card JSON file that
appears on the Kite home screen.

## Where To Put Cards

Kite loads home cards from the shared cards directory.

Android-side preferred path:

```text
/sdcard/Download/KF/cards
```

Android-side fallback path:

```text
/sdcard/Android/media/com.kftest.app/KF/cards
```

Ubuntu/PRoot-side path:

```text
/exchange/cards
```

These point to the same shared card area. A JSON file written from Ubuntu into
`/exchange/cards` is visible to Android, and a JSON file written by Android into
the shared cards directory is visible to Ubuntu.

Kite scans:

```text
/exchange/cards/*.json
/exchange/cards/<pack>/recipe.json
```

After adding, editing, or deleting a card, refresh the Kite home page or restart
the app if the UI has not refreshed yet.

## ID Rule

Do not fill the card ID.

Use this:

```json
"base": {
  "id": ""
}
```

Or omit `base.id`.

Kite assigns a local ID on this machine, writes it back into the JSON file, and
uses that ID for run state, shortcuts, process binding, and stop actions.

Reason: copied/shared cards must not carry someone else's local ID. Leaving the
ID empty avoids collisions.

Do not use a top-level `id` for new cards.

## Minimal Card

```json
{
  "base": {
    "id": "",
    "name": "Hermes Process Test",
    "description": "Start Hermes and let Kite own the process group",
    "icon": {
      "type": "builtin",
      "name": "server"
    }
  },
  "launch": {
    "openInstance": true
  },
  "recipe": [
    {
      "type": "shell",
      "cmd": "hermes",
      "workdir": "/workspace",
      "surfaceMode": "panel"
    }
  ]
}
```

Save it as:

```text
/exchange/cards/hermes-process-test.json
```

The file name is only a file name. It is not the runtime ID.

## Step Types

`shell`

Runs a Linux command inside the Ubuntu/PRoot workspace. Kite wraps the command
with `kf-runner` when available, so closing the card can stop the process group.

```json
{
  "type": "shell",
  "cmd": "python3 -m http.server 8648",
  "workdir": "/workspace",
  "surfaceMode": "panel"
}
```

`open_web`

Opens a URL in Kite's web surface. The URL is opened by Android/Kite, not by
Ubuntu.

```json
{
  "type": "open_web",
  "url": "http://127.0.0.1:8648"
}
```

`terminal`

Opens or feeds terminal text. Use this only when the user must interact with a
terminal.

```json
{
  "type": "terminal",
  "text": "python3 --version\n"
}
```

## Surface Mode

`surfaceMode` controls how much UI the step asks for:

```text
auto    platform decides
panel   show the instance panel
silent  run without opening the panel unless the user opens it
```

For process-container testing, prefer:

```json
"surfaceMode": "panel"
```

## Process-Container Test Card

Use this when testing whether closing a card stops all child processes.

```json
{
  "base": {
    "id": "",
    "name": "Kite Process Container Test",
    "description": "Runs a long process for stop verification",
    "icon": {
      "type": "builtin",
      "name": "server"
    }
  },
  "launch": {
    "openInstance": true
  },
  "recipe": [
    {
      "type": "shell",
      "cmd": "bash -lc 'echo KITE_PROCESS_TEST_START; sleep 300'",
      "workdir": "/workspace",
      "surfaceMode": "panel",
      "timeoutMs": 600000
    }
  ]
}
```

Test flow:

```text
1. Save the JSON into /exchange/cards/kite-process-container-test.json.
2. Refresh Kite home.
3. Start the card.
4. Ask Hermes to inspect processes containing KITE_PROCESS_TEST_START or sleep 300.
5. Close the card.
6. Ask Hermes to inspect again.
7. The process group should be gone.
```

## What Not To Put In A Card

Do not put platform transport details in card JSON:

```text
bridgeUrl
bridgePort
token
KF startup details
Android package names for the bridge
```

Cards describe workflow. Kite/KF owns the transport.

Do not write local runtime state into the card:

```text
runId
pid
rootPid
processGroupId
systemSessionId
status
last run time
```

Those are generated at runtime.

## Resource Store Home Card Templates

Resource manifests may include `homeCards[].recipe`. The same ID rule applies:

```json
{
  "homeCards": [
    {
      "label": "Hermes WebUI",
      "policy": "manual",
      "recipe": {
        "base": {
          "id": "",
          "name": "Hermes WebUI"
        },
        "recipe": [
          {
            "type": "shell",
            "cmd": "hermes-web-ui start --port 8648",
            "surfaceMode": "panel"
          },
          {
            "type": "open_web",
            "url": "http://127.0.0.1:8648"
          }
        ]
      }
    }
  ]
}
```

When the user chooses to add the resource's home card, Kite copies the nested
recipe into the shared card directory and assigns a local ID.

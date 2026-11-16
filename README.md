# KnobDeck Companion for JetBrains IDEs

Lets the [KnobDeck](https://knobdeck.harshalkudale.com) dial and deck control IntelliJ IDEA, Android
Studio, PyCharm, WebStorm, GoLand, Rider, CLion and the other IntelliJ-based IDEs (2024.3 and newer).

KnobDeck's **IntelliJ IDE** plugin calls this plugin's local action server instead of sending
keyboard shortcuts, so its buttons work whatever your keymap is.

## Setup

1. Install **KnobDeck Companion** from the JetBrains Marketplace (or **Install Plugin from Disk…**
   with the zip from a [GitHub release](https://github.com/Knobdeck/knobdeck-intellij-companion/releases)). The action server starts right away on `127.0.0.1:21420`.
2. Optional: open **Settings → Tools → KnobDeck** to set a password or change the port.
3. In KnobDeck, install the **IntelliJ IDE** plugin and enter the same port and password in its setup.

Only one IDE can use a port. If you run two IDEs at once, give the second one another port, and
set that port on its KnobDeck buttons (each button has an optional **Action Port**).

## What KnobDeck can do with it

Run and Debug (optionally a named run configuration), Step Over / Into / Out, Resume, Pause, Stop,
Reset Frame, Search Everywhere, Terminal, Project Structure, Update Project, New Project, and any IDE
action by its ID (**Customized Action**). To find an ID, use **Tools → KnobDeck: Copy an Action ID…**.

## Settings

| Setting | Default | |
|---|---|---|
| Let KnobDeck control this IDE | on | Runs the action server. |
| Port | `21420` | |
| Password | empty | KnobDeck must send this password. |
| Only run actions while an IDE window is focused | off | Turn on to ignore KnobDeck while you're in another app. |

## Privacy and security

The plugin sends nothing anywhere and collects no data. Its server listens only on `127.0.0.1` and
refuses requests from web pages. See [PRIVACY.md](PRIVACY.md) and the
[KnobDeck privacy policy](https://knobdeck.harshalkudale.com/legal/privacy).

## Development

See [CONTRIBUTING.md](CONTRIBUTING.md). This is one of the
[KnobDeck companions](https://github.com/Knobdeck/knobdeck-companion).

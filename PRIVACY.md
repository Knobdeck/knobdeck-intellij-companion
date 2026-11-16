# Privacy

The KnobDeck Companion for JetBrains IDEs:

- **collects nothing and sends nothing.** It makes no network requests, has no analytics, no
  telemetry and no crash reporting.
- **listens only on your own computer** (`127.0.0.1`), so other devices on your network can't reach
  it, and it refuses requests from web pages.
- **answers only KnobDeck**, which asks it to run IDE actions and to list your run configurations so
  you can pick one on a button. That information goes no further than KnobDeck on the same computer.
- **stores only its own settings** (port, optional password, on/off) in the IDE's settings.

The password, if you set one, is stored in plain text in the IDE's settings and in KnobDeck's
plugin settings on the same computer. It stops other programs on your computer from using the
companion; it isn't meant to protect anything beyond that.

For KnobDeck itself see the [KnobDeck privacy policy](https://knobdeck.harshalkudale.com/legal/privacy).
Questions: support@harshalkudale.com.

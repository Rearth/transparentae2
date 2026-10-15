# Transparent AE2

Renders moving items inside AE2 networks. Works with see-through cables (like normal me cables) and also 
adds transparent versions of the smart / dense cables.

Items are shown moving through AE2 glass cables and specially treated smart or dense cables. The
animation follows the route that AE2 assignd between a machine and the controller. The real item
transfer still happens instantly. The moving item is only a visual effect.

## Features

- Shows imported, exported, inserted, extracted, and crafting-input items moving through an ME
  network.
- Existing AE2 transfer / working logic is untouched.
- Adds a reusable Cable Transparency Applicator Item. Use it on a smart or dense cable to show items through
  that cable. Use it again to remove the treatment.
- Adds ready-made transparent smart, dense smart, and dense covered cable items.
- Adds a network item animation toggle to the terminal toolbar, including crafting, pattern encoding,
  pattern access, and wireless terminals.

## What it does not do

- It does not change how AE2 stores, moves, crafts, or secures items.
- It does not turn the animation into a real item entity. It cannot be picked up or interact with
  the world.
- It does not slow down item transfers to match the animation.
- It does not currently show fluid transfers.
- It does not show routes on controllerless, booting, or conflicting AE2 networks.
- It does not show a route when every cable on that route is an opaque cable.

## Performance impact

The server observes completed AE2 item transfers and calculates their controller routes once per
tick. Similar transfers are grouped together before the route is sent to nearby players. The client
keeps a limited number of recent animations and only renders nearby items.

Most small installations should see little impact. Very large or very busy ME networks can require
more route calculations and network traffic, especially when many different items move at once. If
needed, the server can disable transfer-path calculation completely. The calculations can be disabled
on the server level, or the client can disable the rendering. Both can be toggled independently.

## Configuration

Server configuration (`config/transparentae2-server.toml` in the server directory; a world-specific
`<world>/serverconfig/transparentae2-server.toml` overrides it if present):

- `enableTransferPaths` enables or disables route calculation and animation packets.
- `enableTransferLogging` enables per-transfer and resolved-path DEBUG messages. Defaults to `false`,
  even when the server enables DEBUG logging. This is independent of animations. Enable only while
  troubleshooting: busy networks can generate very large logs. Resolved-path messages also require
  transfer paths and network animations to be enabled.

To silence transfer logs, leave `enableTransferLogging = false`. To collect transfer diagnostics,
set it to `true` and ensure DEBUG logging is enabled for
`rearth.transparentae2.TransparentAE2` in the server's Log4j configuration. Stop the server before
editing its configuration files, then restart it.

NeoForge's default logging configuration writes DEBUG messages to `logs/debug.log`, even when the
console only shows INFO messages. Server admins can set these JVM arguments in `user_jvm_args.txt`:

```text
-Dforge.logging.console.level=info
-Dforge.logging.file.level=info
-Dforge.logging.debugFile.level=info
```

These control the console, `logs/latest.log`, and `logs/debug.log`, respectively, for all mods using
the default logging configuration. Use `debug` instead of `info` for more detail. Raising only the
console threshold does not silence `debug.log`. To change only Transparent AE2's logger level, add
`<Logger name="rearth.transparentae2.TransparentAE2" level="info"/>` inside `<Loggers>` in a custom
Log4j configuration, preserving the server's existing appenders and other settings, and select that
file with `-Dlog4j.configurationFile=log4j2.xml` in `user_jvm_args.txt`.

Client configuration:

- `renderTransferItems` enables or disables moving item animations.
- `itemMovementSpeed` changes the animation speed.
- `maxTransfers` limits how many recent animations the client keeps.

Network setting:

- The terminal toolbar's **Network item animations** button enables or disables animations for the
  entire connected ME network, for every player. It defaults to enabled. Animations require this
  setting, `enableTransferPaths`, and `renderTransferItems` to all be enabled.
- The setting is saved with AE2's grid node data and survives world reloads. Split networks inherit
  the setting; when networks merge, the receiving network keeps its setting.
- Disabling the setting stops new animations; existing animations finish naturally. Portable storage
  cells and disconnected wireless terminals have no network toggle.

## Credits

Cable Transparency Applicator Texture: From MalcolmRiley's [unused textures](https://github.com/malcolmriley/unused-textures)

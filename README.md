# Transparent AE2

Transparent AE2 makes item movement inside an Applied Energistics 2 network visible.

Items are shown moving through AE2 glass cables and specially treated smart or dense cables. The
animation follows the route that AE2 assigned between a machine and the controller. The real item
transfer still happens instantly; the moving item is only a visual effect.

## Features

- Shows imported, exported, inserted, extracted, and crafting-input items moving through an ME
  network.
- Adds a reusable Cable Treatment Applicator. Use it on a smart or dense cable to show items through
  that cable. Use it again to remove the treatment.
- Adds ready-made transparent smart, dense smart, and dense covered cable items.
- Adds a network item animation toggle to the terminal toolbar, including crafting, pattern encoding,
  pattern access, and wireless terminals.
- Keeps a treated cable treated when it is broken, placed again, or recolored.
- Handles gaps in a route, such as P2P or quantum links, by moving the item between the visible
  parts instead of drawing a false connection through the world.

## What it does not do

- It does not change how AE2 stores, moves, crafts, or secures items.
- It does not turn the animation into a real item entity. It cannot be picked up or interact with
  the world.
- It does not slow down item transfers to match the animation.
- It does not currently show fluid transfers.
- It does not show routes on controllerless, booting, or conflicting AE2 networks.
- It does not show a route when every cable on that route is an opaque, untreated cable.

## Performance impact

The server observes completed AE2 item transfers and calculates their controller routes once per
tick. Similar transfers are grouped together before the route is sent to nearby players. The client
keeps a limited number of recent animations and only renders nearby items.

Most small installations should see little impact. Very large or very busy ME networks can require
more route calculations and network traffic, especially when many different items move at once. If
needed, the server can disable transfer-path calculation completely.

## Configuration

Server configuration:

- `enableTransferPaths` enables or disables route calculation and animation packets.

Client configuration:

- `renderTransferItems` enables or disables moving item animations.
- `itemMovementSpeed` changes the animation speed.
- `maxTransfers` limits how many recent animations the client keeps.

The client settings are available from Transparent AE2's entry in NeoForge's Mods screen.

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

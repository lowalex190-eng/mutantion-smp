# MutationSMP  (Paper 1.21.11, Java 21)

Every PvP kill gives you a random mutation. Every mutation has a buff AND a drawback.
Die and you lose your newest one.

## Build
Requires JDK 21 and Maven.

    mvn clean package

Your plugin is `target/MutationSMP.jar`. Drop it in `plugins/` and restart.

## Commands
| Command | Who | What |
|---|---|---|
| `/mutation` | everyone | your mutations |
| `/mutation list` | everyone | all mutations |
| `/mutation info <id>` | everyone | one mutation |
| `/mutation check <player>` | everyone | see someone else's mutations |
| `/mutation give <player> <id/random>` | admin | give a mutation |
| `/mutation remove <player> <id/newest/all>` | admin | remove |
| `/mutation reroll <player>` | admin | replace with random ones |
| `/mutation clear <player>` | admin | remove all |
| `/mutation reload` | admin | reload config.yml |

Permissions: `mutationsmp.use` (default true), `mutationsmp.admin` (default op).

## Config (plugins/MutationSMP/config.yml)
Max mutations, starting mutation, loss on death (ALL/PVP/NONE), reward mode
(RANDOM/STEAL), same-victim anti-farm cooldown, boss-kill rewards, chat broadcasts,
and per-mutation enable/weight.

## Tweaking numbers
Attribute numbers (hearts, speed %, armor, reach, size) are in `MutationType.java`.
Event numbers (damage multipliers, proc chances) are in `MutationListener.java`.
Timer-based behaviour is in `MutationTicker.java`.

## Notes
- Data is stored in each player's persistent data, so there is no database or extra file.
- Removing Night Stalker / Gillborn also removes any matching potion effect the player has.

## Armor overlays (resource pack)
Install `MutationSMP-Pack.zip` for every player, and the armor of anyone with a mutation
shows a glowing overlay for their newest mutation.

1. Upload `MutationSMP-Pack.zip` somewhere with a direct download link.
2. In `server.properties` set:
       resource-pack=<direct link>
       resource-pack-sha1=<sha1 of the zip>
       require-resource-pack=true
3. Restart. Players are prompted to download on join.

IMPORTANT: keep `require-resource-pack=true`. If a player doesn't have the pack, an armor
piece with a mutation overlay can render invisible for them.

Turn it off with `armor-overlay.enabled: false` (armor is reset to vanilla within a second).
Leather and turtle shell are not supported. Overlays are applied to the armor item itself,
so it can briefly keep an overlay while sitting in a chest or inventory; it is corrected as
soon as someone wears it.

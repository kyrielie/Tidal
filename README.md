# Tidal
## Beach waves!

Tidal adds ambient water waves! They are most noticeable at beaches & shores where they get really big, but can also spawn around islands to help make the water feel more alive.  

Waves can also crash against blocks if they're in the way, making rocky cliffs next to water even more dramatic! During full moons, the waves glow at night too.

**Note: Tidal is currently in alpha!** There's plenty of known bugs and plans for optimizations, some include:
- Many unique textures for things like the top side of waves which are washing up, waves which just crashed into a wall, and particle textures for crashed waves which just fell into water, are still needed.
- Waves do not continue spawning after 50 block updates in a chunk(chunk doesn't seem to get rescanned).
- Waves do not always spawn correctly after moving from a chunk reload(facing wrong way).
- Waves in areas like rivers are too long after washing up, and should probably be scaled down.
- The scanning process for finding water blocks can be sped up via multithreading.
- Waves "wash up" in midair instead of falling.
- Info calculated for areas to spawn waves, specifically the length of waves & their spawn position, should be cached per chunk instead of recalculating every wave spawn tick.
- The wet overlay rendering can probably be optimized.
- There's probably some Z-fighting issues with the waves(they were scaled up to their current scale very last minute)
- Waves hitbox for crashing into a wall is too small(because of the new scale).
- Waves cannot be viewed from beneath them.
- Sounds for the waves
- Config.

Lots of work happens behind the scenes to figure out where & how to spawn waves. If waves are not spawning, or spawning unusually(e.g. going wrong way), you can recalculate this info by reloading the chunks via `F3 + a`.

## Dependencies
Requires Fabric API. In the near future, a config library might also be required.

### Note for resource packs
If you decide to change the wave textures via a resource pack, **the naming and mcmeta is very specific!!**  
The names & locations of textures _must_ be the same.  
A custom mcmeta format is used. Instead of `animation`, use `wave_animation`. This provides you with 2 options:
- `frametime` - The amount of ticks each frame should exist. (Default = 5)
- `frame_height` - The height(in pixels) of each frame. (Default = 16)

It is expected that the animation is done with a single vertical sprite sheet image. The amount of frames will be determined using the `frame_height` variable.
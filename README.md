# TNT Optimiser

A Fabric mod for Minecraft 26.1.2 that reduces the server-side CPU cost of large TNT chain reactions,
without changing the explosion radius, entity damage or knockback.

## What it does

Vanilla resolves every PrimedTntEntity / Explosion independently and, for each one, casts ~4096 rays
outward from the origin to decide which blocks to destroy - so in a chain reaction the blocks in
overlapping blast radii get scanned and resistance-checked once per explosion that reaches them. That
repeated work - not the explosion math - is the dominant server-tick cost of cannons, TNT-duping farms
and world-eater builds.

This mod replaces vanilla's ray-cast scan with a breadth-first traversal (option 2 of the design):

- The blast sphere is visited block-by-block exactly once, carrying a power budget from the origin.
  The budget decays 0.75 per block of travel (matching vanilla's 0.225-per-0.3-block ray step) and a
  block is destroyed when the budget reaching it still exceeds its blast resistance, per the same
  formula vanilla uses: power -= (resistance + 0.3) * 0.3.
- The resulting crater shape differs from vanilla's ray pattern in detail - this is the algorithm
  swap by design, not a bug. Radius, entity damage and knockback are untouched because those are
  computed outside the scan (hurtEntities / explosion impulse).
- Cross-explosion deduplication sits on top: a shared per-tick tracker records every block already
  resolved this tick, so a second explosion whose BFS frontier reaches such a block skips it entirely
  instead of redoing the resistance calculation. Per-block resistance is also memoised.

### Fast path for isolated TNT

At the start of ServerExplosion.explode() the blast box is registered and checked for overlap with any
other explosion already queued this tick. If it is isolated, the optimised scan is skipped entirely and
vanilla runs unmodified - zero overhead, vanilla speed.

## Building

Requires Java 25 (the project targets release = 25).

`powershell
./gradlew build
`

The runnable mod jar is produced at build/libs/tnt-optimiser-1.0.0.jar.

## Installing

Drop build/libs/tnt-optimiser-1.0.0.jar into the mods/ folder of a 26.1.2 Fabric server (or a client
used for singleplayer - the mod loads on both and only affects server-side explosion resolution). No
configuration is required.

## Mapping note

Yarn mappings for 26.1.2 do not exist yet, so Fabric Loom resolves official Mojang mappings for this
project. All mixin targets are written against the official names (ServerExplosion,
ExplosionDamageCalculator, Explosion.BlockInteraction, Identifier, MinecraftServer.tickServer, ...).
If/when Yarn catches up and the project switches mappings, these targets will need renaming.

## Testing

1. Isolated TNT (correctness): fire a single TNT with and without the mod; radius, damage and knockback
   must be identical. The exact set of destroyed blocks will differ slightly (BFS vs ray-cast shape) -
   check for a reasonable crater with no over-destruction past the blast radius and no floating blocks
   left behind.
2. Chain reaction (dedup): build a fixed rig (e.g. a 20x20x20 TNT cube) lit all at once, and assert no
   block has its resistance recalculated twice in the same tick (the per-tick visited set guarantees this).
3. Load test: large chain reaction (500+ TNT) - measure server tick time before/after via a tick-timer
   or a /forge tps-equivalent, not wall-clock eyeballing.
4. Regression: confirm redstone TNT, minecart TNT, wind-charge and creeper explosions all still behave
   (creepers use DESTROY_WITH_DECAY and go through the same BFS; blocks that are only triggered -
   KEEP / TRIGGER_BLOCK - fall through to vanilla).

## Package layout

com.jayjoke.tntoptimised
  TNTOptimiser.java                    ModInitializer
  explosion/
    TickExplosionTracker.java          per-tick, per-dimension shared state
    BlastResistanceCache.java          memoised per-block resistance lookups
    ExplosionBatch.java                one explosion's blast box, for the fast-path overlap test
    OptimisedExplosionLogic.java       the BFS scan + dedup facade the mixins call
  mixin/
    ExplosionMixin.java                hooks ServerExplosion.explode / calculateExplodedPositions
    ExplosionDamageCalculatorMixin.java  memoises getBlockExplosionResistance
    ServerWorldTickMixin.java          flushes the tracker at the end of MinecraftServer.tickServer

The client source set is intentionally left empty: this mod has no client-side behaviour.

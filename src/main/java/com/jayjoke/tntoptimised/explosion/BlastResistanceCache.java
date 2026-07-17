package com.jayjoke.tntoptimised.explosion;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/**
 * Per-dimension memoisation of per-block explosion resistance.
 *
 * <p>Vanilla's {@code ExplosionDamageCalculator.getBlockExplosionResistance} is a pure function of the
 * block state at a position (it returns {@code max(block.getExplosionResistance(), fluid.getExplosionResistance())}
 * and ignores the explosion/source entity). Because it is source-independent, the value computed for a given
 * (position, state) is identical for every explosion that scans that block in the same tick, so it is safe to
 * share across overlapping explosions. We key by packed position and validate against the block state so that a
 * block destroyed earlier in the tick (now air, a different state instance) is recomputed rather than served a
 * stale value.
 */
final class BlastResistanceCache {
	private final Long2ObjectMap<Float> resistanceByPosition = new Long2ObjectOpenHashMap<>();
	private final Long2ObjectMap<BlockState> stateByPosition = new Long2ObjectOpenHashMap<>();

	/**
	 * @return the cached resistance for the position if it was previously computed for the same block state,
	 *         otherwise {@code null} (caller should let vanilla compute and then {@link #store}).
	 */
	Optional<Float> peek(long packedPosition, BlockState state) {
		if (stateByPosition.get(packedPosition) != state) {
			return null;
		}
		if (!resistanceByPosition.containsKey(packedPosition)) {
			return null;
		}
		return Optional.ofNullable(resistanceByPosition.get(packedPosition));
	}

	void store(long packedPosition, BlockState state, Optional<Float> value) {
		if (state == null) {
			return;
		}
		stateByPosition.put(packedPosition, state);
		resistanceByPosition.put(packedPosition, value.orElse(null));
	}

	void clear() {
		resistanceByPosition.clear();
		stateByPosition.clear();
	}
}

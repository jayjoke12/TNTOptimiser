package com.jayjoke.tntoptimised.explosion;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared, per-tick bookkeeping for every explosion resolved on the server.
 *
 * <p>State is partitioned by dimension so that two explosions at identical coordinates in different
 * dimensions in the same tick can never mask each other. The whole structure is reset at the end of
 * every server tick by {@link OptimisedExplosionLogic#endTick()}.
 */
final class TickExplosionTracker {
	private final Map<Identifier, DimensionState> perDimension = new HashMap<>();

	DimensionState dimension(Identifier dimension) {
		return perDimension.computeIfAbsent(dimension, key -> new DimensionState());
	}

	/**
	 * Registers an explosion's blast box for subsequent overlap checks and reports whether it is isolated
	 * (its box overlaps no explosion already registered this tick). An isolated explosion takes the vanilla
	 * path with zero tracker overhead.
	 */
	boolean beginExplosion(Identifier dimension, Vec3 center, float radius) {
		DimensionState state = dimension(dimension);
		ExplosionBatch batch = new ExplosionBatch(center, radius);
		boolean isolated = state.batches.stream().noneMatch(existing -> existing.overlaps(batch));
		state.batches.add(batch);
		return isolated;
	}

	void clear() {
		perDimension.values().forEach(DimensionState::clear);
		perDimension.clear();
	}

	static final class DimensionState {
		final LongOpenHashSet visitedThisTick = new LongOpenHashSet();
		final BlastResistanceCache resistanceCache = new BlastResistanceCache();
		final List<ExplosionBatch> batches = new ArrayList<>();

		void clear() {
			visitedThisTick.clear();
			resistanceCache.clear();
			batches.clear();
		}
	}
}

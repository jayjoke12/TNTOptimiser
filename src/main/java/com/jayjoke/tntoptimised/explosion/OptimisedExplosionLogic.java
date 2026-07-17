package com.jayjoke.tntoptimised.explosion;

import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.PriorityQueue;

/**
 * Single entry point the mixins call into. Holds the per-tick {@link TickExplosionTracker} and exposes the
 * small, side-effecting operations the explosion hot path needs.
 *
 * <p>This mod follows the BFS design: vanilla's ray-cast scan is replaced by a breadth-first traversal that
 * visits each block in the blast sphere once, carrying a power budget that decays with blast resistance
 * (modelled on {@code ServerExplosion.calculateExplodedPositions}). The resulting crater shape differs from
 * vanilla's ray pattern by design — that is the algorithm swap, not a bug — but radius, entity damage and
 * knockback are untouched because those are computed elsewhere and are not part of the scan.
 *
 * <p>Blocks already resolved by an earlier explosion in the same tick are skipped entirely, so the resistance
 * calculation for them is never repeated. Per-block resistance is also memoised in {@link BlastResistanceCache}.
 */
public final class OptimisedExplosionLogic {
	private static final TickExplosionTracker TRACKER = new TickExplosionTracker();

	private OptimisedExplosionLogic() {
	}

	/**
	 * @return {@code true} if this explosion is isolated this tick and should run vanilla code unmodified.
	 */
	public static boolean beginExplosion(Identifier dimension, Vec3 center, float radius) {
		return TRACKER.beginExplosion(dimension, center, radius);
	}

	public static boolean wasVisited(Identifier dimension, long packedPosition) {
		return TRACKER.dimension(dimension).visitedThisTick.contains(packedPosition);
	}

	/**
	 * @return {@code true} if this is the first explosion to resolve this block this tick.
	 */
	public static boolean markVisited(Identifier dimension, long packedPosition) {
		return TRACKER.dimension(dimension).visitedThisTick.add(packedPosition);
	}

	public static Optional<Float> peekResistance(Identifier dimension, long packedPosition, BlockState state) {
		return TRACKER.dimension(dimension).resistanceCache.peek(packedPosition, state);
	}

	public static void storeResistance(Identifier dimension, long packedPosition, BlockState state, Optional<Float> value) {
		TRACKER.dimension(dimension).resistanceCache.store(packedPosition, state, value);
	}

	public static void endTick() {
		TRACKER.clear();
	}

	// Vanilla's ray steps 0.3 blocks and loses 0.225 power per step, so one full block of travel costs 0.75.
	private static final float STEP_DECAY = 0.75f;
	private static final float RESISTANCE_OFFSET = 0.3f;
	private static final float RESISTANCE_SCALE = 0.3f;
	private static final Direction[] NEIGHBOURS = Direction.values();

	/**
	 * BFS replacement for {@code ServerExplosion.calculateExplodedPositions}. Visits every block in the blast
	 * sphere once, propagating a power budget from the origin. A block is added to the destroyed list when the
	 * budget reaching it still exceeds its blast resistance and the damage calculator allows it.
	 *
	 * @param dimension  the dimension the explosion is in (for the shared per-tick tracker)
	 * @param level      the server level (chunk/block access)
	 * @param center     the explosion centre
	 * @param radius     the blast radius
	 * @param explosion  the explosion, passed through to the damage calculator
	 * @param calculator the explosion's damage calculator (already memoised by the mixin)
	 */
	public static List<BlockPos> runBfs(Identifier dimension, ServerLevel level, Vec3 center, float radius, Explosion explosion, ExplosionDamageCalculator calculator) {
		List<BlockPos> destroyed = new ArrayList<>();
		BlockPos origin = BlockPos.containing(center);
		int bound = (int) Math.ceil(radius) + 1;
		long boundSquared = (long) bound * bound;

		Long2FloatOpenHashMap bestPower = new Long2FloatOpenHashMap();
		LongOpenHashSet processed = new LongOpenHashSet();
		PriorityQueue<BlockPos> frontier = new PriorityQueue<>(Comparator.comparingDouble(pos -> -bestPower.get(pos.asLong())));

		bestPower.put(origin.asLong(), radius);
		frontier.add(origin);

		while (!frontier.isEmpty()) {
			BlockPos pos = frontier.poll();
			long packed = pos.asLong();
			if (!processed.add(packed)) {
				continue;
			}
			// Already resolved by an earlier explosion this tick: skip it (and don't re-propagate).
			if (wasVisited(dimension, packed)) {
				continue;
			}
			markVisited(dimension, packed);
			if (!level.isInWorldBounds(pos) || pos.distSqr(origin) > boundSquared) {
				continue;
			}

			BlockState state = level.getBlockState(pos);
			FluidState fluid = level.getFluidState(pos);
			float incoming = bestPower.get(packed);
			Optional<Float> resistance = calculator.getBlockExplosionResistance(explosion, level, pos, state, fluid);

			float testPower;
			if (resistance.isPresent()) {
				testPower = incoming - (resistance.get() + RESISTANCE_OFFSET) * RESISTANCE_SCALE;
				if (testPower > 0.0f && calculator.shouldBlockExplode(explosion, level, pos, state, testPower)) {
					destroyed.add(pos);
				}
			} else {
				testPower = incoming;
			}

			float nextPower = testPower - STEP_DECAY;
			if (nextPower > 0.0f) {
				for (Direction direction : NEIGHBOURS) {
					BlockPos neighbour = pos.relative(direction);
					long neighbourPacked = neighbour.asLong();
					if (processed.contains(neighbourPacked) || neighbour.distSqr(origin) > boundSquared) {
						continue;
					}
					if (nextPower > bestPower.get(neighbourPacked)) {
						bestPower.put(neighbourPacked, nextPower);
						frontier.add(neighbour);
					}
				}
			}
		}
		return destroyed;
	}
}

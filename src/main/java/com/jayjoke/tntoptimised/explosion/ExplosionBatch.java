package com.jayjoke.tntoptimised.explosion;

import net.minecraft.world.phys.Vec3;

/**
 * Axis-aligned integer bounding box of a single explosion's blast radius, used only for the cheap
 * "is this explosion isolated" fast-path check. Two boxes intersecting is a necessary (not sufficient)
 * condition for the underlying spheres to overlap, so a non-overlapping box guarantees the explosions
 * cannot share any blocks and the tracker can be skipped entirely.
 */
final class ExplosionBatch {
	private final int minX;
	private final int minY;
	private final int minZ;
	private final int maxX;
	private final int maxY;
	private final int maxZ;

	ExplosionBatch(Vec3 center, float radius) {
		int reach = (int) Math.ceil(radius);
		this.minX = (int) Math.floor(center.x - reach);
		this.minY = (int) Math.floor(center.y - reach);
		this.minZ = (int) Math.floor(center.z - reach);
		this.maxX = (int) Math.ceil(center.x + reach);
		this.maxY = (int) Math.ceil(center.y + reach);
		this.maxZ = (int) Math.ceil(center.z + reach);
	}

	boolean overlaps(ExplosionBatch other) {
		return minX <= other.maxX && maxX >= other.minX
				&& minY <= other.maxY && maxY >= other.minY
				&& minZ <= other.maxZ && maxZ >= other.minZ;
	}
}

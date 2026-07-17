package com.jayjoke.tntoptimised.mixin;

import com.jayjoke.tntoptimised.explosion.OptimisedExplosionLogic;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Hooks the 26.1.2 server explosion hot path ({@code ServerExplosion}, the implementation of the
 * {@code Explosion} interface).
 *
 * <ul>
 *   <li>At the start of {@code explode()} we register the blast box and learn whether this explosion is
 *       isolated this tick. Isolated explosions skip all optimised machinery and run vanilla (fast path).</li>
 *   <li>At the start of {@code calculateExplodedPositions()} we replace vanilla's ray-cast scan with a BFS
 *       ({@link OptimisedExplosionLogic#runBfs}) for any non-isolated explosion that actually destroys
 *       blocks. The BFS visits each block in the blast sphere once and skips blocks already resolved by an
 *       earlier explosion this tick.</li>
 * </ul>
 *
 * Entity damage, knockback and the ray-cast's *effect* on the final block set are not touched — the BFS
 * produces the destroyed-block list that {@code interactWithBlocks} / {@code createFire} consume unchanged.
 * Creepers ({@code DESTROY_WITH_DECAY}) and TNT ({@code DESTROY}) both go through the BFS; blocks that are
 * merely triggered ({@code KEEP}, {@code TRIGGER_BLOCK}) fall through to vanilla.
 */
@Mixin(ServerExplosion.class)
public abstract class ExplosionMixin {
	@Unique
	private boolean optimisedActive;

	@Shadow
	private Explosion.BlockInteraction blockInteraction;
	@Shadow
	private ExplosionDamageCalculator damageCalculator;
	@Shadow
	public abstract ServerLevel level();
	@Shadow
	public abstract Vec3 center();
	@Shadow
	public abstract float radius();

	@Inject(method = "explode", at = @At("HEAD"))
	private void optimisedRegisterExplosion(CallbackInfoReturnable<Integer> callback) {
		Identifier dimension = this.level().dimension().identifier();
		this.optimisedActive = !OptimisedExplosionLogic.beginExplosion(dimension, this.center(), this.radius());
	}

	@Inject(method = "calculateExplodedPositions", at = @At("HEAD"), cancellable = true)
	private void optimisedScanBfs(CallbackInfoReturnable<List<BlockPos>> cir) {
		boolean destroysBlocks = this.blockInteraction == Explosion.BlockInteraction.DESTROY
				|| this.blockInteraction == Explosion.BlockInteraction.DESTROY_WITH_DECAY;
		if (!(this.optimisedActive && destroysBlocks)) {
			return;
		}
		Identifier dimension = this.level().dimension().identifier();
		cir.setReturnValue(OptimisedExplosionLogic.runBfs(
				dimension, this.level(), this.center(), this.radius(), (ServerExplosion) (Object) this, this.damageCalculator));
		cir.cancel();
	}
}


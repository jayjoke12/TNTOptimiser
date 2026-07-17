package com.jayjoke.tntoptimised.mixin;

import com.jayjoke.tntoptimised.explosion.OptimisedExplosionLogic;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Memoises {@code ExplosionDamageCalculator.getBlockExplosionResistance}. The base implementation is a pure
 * function of the block state at a position (it ignores the explosion/source entity), so its result for a
 * given (position, state) is identical for every explosion that scans that block in the same tick.
 *
 * <p>TNT funnels through {@code EntityBasedExplosionDamageCalculator}, which calls {@code super} (this base
 * method) first; intercepting here therefore covers TNT and every other explosion. The cached value is
 * validated against the live block state, so a block destroyed earlier in the tick (now air) is recomputed
 * rather than served a stale result.
 */
@Mixin(ExplosionDamageCalculator.class)
public abstract class ExplosionDamageCalculatorMixin {
	@Inject(method = "getBlockExplosionResistance", at = @At("HEAD"), cancellable = true)
	private void optimisedMemoizeResistanceHead(Explosion explosion, BlockGetter reader, BlockPos pos, BlockState state, FluidState fluidState, CallbackInfoReturnable<Optional<Float>> cir) {
		Identifier dimension = explosion.level().dimension().identifier();
		Optional<Float> cached = OptimisedExplosionLogic.peekResistance(dimension, pos.asLong(), state);
		if (cached != null) {
			cir.setReturnValue(cached);
			cir.cancel();
		}
	}

	@Inject(method = "getBlockExplosionResistance", at = @At("RETURN"))
	private void optimisedMemoizeResistanceReturn(Explosion explosion, BlockGetter reader, BlockPos pos, BlockState state, FluidState fluidState, CallbackInfoReturnable<Optional<Float>> cir) {
		Identifier dimension = explosion.level().dimension().identifier();
		OptimisedExplosionLogic.storeResistance(dimension, pos.asLong(), state, cir.getReturnValue());
	}
}

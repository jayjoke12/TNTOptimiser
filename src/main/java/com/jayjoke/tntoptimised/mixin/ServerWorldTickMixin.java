package com.jayjoke.tntoptimised.mixin;

import com.jayjoke.tntoptimised.explosion.OptimisedExplosionLogic;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

/**
 * Flushes the per-tick shared state once the whole server tick (every dimension) has finished, so the next
 * tick starts with a clean tracker. All explosions resolve during {@code tickServer}, so clearing at its
 * return is correct.
 */
@Mixin(MinecraftServer.class)
public abstract class ServerWorldTickMixin {
	@Inject(method = "tickServer", at = @At("RETURN"))
	private void optimisedClearTracker(BooleanSupplier shouldKeepTicking, CallbackInfo callback) {
		OptimisedExplosionLogic.endTick();
	}
}

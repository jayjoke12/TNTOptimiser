package com.jayjoke.tntoptimised;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TNTOptimiser implements ModInitializer {
	public static final String MOD_ID = "tnt-optimiser";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// The per-tick shared explosion state is flushed by ServerWorldTickMixin at the end of every server
		// tick; this entrypoint only needs to register the mod.
		LOGGER.info("TNT Optimiser initialised");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}

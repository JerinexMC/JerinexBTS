package org.stepan1411.pvp_bot.config;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.Path;
public class WorldConfigHelper { public static Path getWorldConfigDir() { return FabricLoader.getInstance().getConfigDir().resolve("pvpbot"); } }

OKAYLISTEN

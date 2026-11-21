package org.stepan1411.pvp_bot.bot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.class_2168;
import net.minecraft.class_243;
import net.minecraft.class_3222;
import net.minecraft.server.MinecraftServer;
import org.stepan1411.pvp_bot.config.WorldConfigHelper;
import org.stepan1411.pvp_bot.fixes.ProfileLagFix;


import org.stepan1411.pvp_bot.stats.StatsReporter;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class BotManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Set<String> bots = new HashSet<>();
    private static final Map<String, BotData> botDataMap = new HashMap<>();
    private static final Map<String, Integer> nullTickCount = new HashMap<>();
    private static final int MAX_NULL_TICKS = 5;
    private static Path savePath;
    private static boolean initialized = false;

    public static class BotData {
        public String name;
        public double x, y, z;
        public float yaw, pitch;
        public String dimension;
        public String gamemode;
        
        public BotData() {}
        
        public BotData(class_3222 bot) {
            this.name = bot.method_5477().getString();
            this.x = bot.method_23317();
            this.y = bot.method_23318();
            this.z = bot.method_23321();
            this.yaw = bot.method_36454();
            this.pitch = bot.method_36455();
            this.dimension = bot.method_51469().method_27983().method_29177().toString();
            this.gamemode = bot.field_13974.method_14257().method_15434();
        }
    }
    
    public static void init(MinecraftServer server) {
        if (initialized) return;
        

        bots.clear();
        botDataMap.clear();
        
        Path configDir = FabricLoader.getInstance().getConfigDir().resolve("pvpbot");
        try {
            Files.createDirectories(configDir);
        } catch (Exception e) {
            System.out.println("[PVP_BOT] Failed to create config directory: " + e.getMessage());
        }
        
        savePath = org.stepan1411.pvp_bot.config.WorldConfigHelper.getWorldConfigDir().resolve("bots.json");
        loadBots();
        BotSettings settings = BotSettings.get();
        if (settings.isBotsRelogs() && !botDataMap.isEmpty()) {
            System.out.println("[PVP_BOT] Restoring " + botDataMap.size() + " bots...");
            Map<String, BotData> botsToRestore = new HashMap<>(botDataMap);
            bots.clear();
            botDataMap.clear();
            server.execute(() -> restoreBotsDelayed(server, botsToRestore, 0));
        } else if (!settings.isBotsRelogs()) {
            bots.clear();
            botDataMap.clear();
            saveBots();
        }
        
        initialized = true;
    }
    
    private static void restoreBotsDelayed(MinecraftServer server, Map<String, BotData> botsToRestore, int index) {
        restoreBotsDelayedWithRetry(server, botsToRestore, index, 0);
    }
    
    private static void restoreBotsDelayedWithRetry(MinecraftServer server, Map<String, BotData> botsToRestore, int index, int retryCount) {
        final int MAX_RETRIES = 2;
        
        if (index >= botsToRestore.size()) {
            System.out.println("[PVP_BOT] Restored " + bots.size() + " bots");
            return;
        }
        
        String[] names = botsToRestore.keySet().toArray(new String[0]);
        if (index < names.length) {
            String name = names[index];
            BotData data = botsToRestore.get(name);
            class_3222 existingPlayer = server.method_3760().method_14566(name);
            if (existingPlayer != null && !bots.contains(name)) {
                System.out.println("[PVP_BOT] Skipping bot '" + name + "': real player with this name is online");
                final int nextIndex = index + 1;
                server.execute(() -> restoreBotsDelayedWithRetry(server, botsToRestore, nextIndex, 0));
                return;
            }
            var dispatcher = server.method_3734().method_9235();
            boolean success = false;
            
            if (BotSettings.get().isProfileLagFix()) ProfileLagFix.preloadProfileCache(server, name);
            try {
                String command = String.format(java.util.Locale.US,
                    "playerspawn %s at %.2f %.2f %.2f facing %.2f %.2f in %s on %s",
                    name, data.x, data.y, data.z, data.yaw, data.pitch,
                    data.gamemode != null ? data.gamemode : "survival",
                    data.dimension
                );
                if (retryCount == 0) {
                    System.out.println("[PVP_BOT] Restoring bot: " + name);
                } else {
                    System.out.println("[PVP_BOT] Retry #" + retryCount + " for bot: " + name);
                }
                dispatcher.execute(command, server.method_3739());
                
                bots.add(name);
                botDataMap.put(name, data);
                success = true;
                System.out.println("[PVP_BOT] Successfully restored bot: " + name);
            } catch (Exception e) {

                try {
                    String simpleCommand = String.format(java.util.Locale.US,
                        "playerspawn %s at %.2f %.2f %.2f",
                        name, data.x, data.y, data.z
                    );
                    dispatcher.execute(simpleCommand, server.method_3739());
                    bots.add(name);
                    botDataMap.put(name, data);
                    success = true;
                    System.out.println("[PVP_BOT] Restored bot with simple command: " + name);
                } catch (Exception e2) {
                    if (retryCount < MAX_RETRIES) {
                        System.out.println("[PVP_BOT] Failed to restore bot '" + name + "', will retry... (" + (retryCount + 1) + "/" + MAX_RETRIES + ")");
                    } else {
                        System.out.println("[PVP_BOT] Failed to restore bot '" + name + "' after " + (MAX_RETRIES + 1) + " attempts: " + e2.getMessage());
                    }
                }
            }
            

            if (!success && retryCount < MAX_RETRIES) {
                final int currentRetry = retryCount + 1;
                server.execute(() -> {
                    final int[] delay = {0};
                    server.execute(new Runnable() {
                        @Override
                        public void run() {
                            delay[0]++;
                            if (delay[0] < 20) {
                                server.execute(this);
                            } else {
                                restoreBotsDelayedWithRetry(server, botsToRestore, index, currentRetry);
                            }
                        }
                    });
                });
            } else {

                final int nextIndex = index + 1;
                server.execute(() -> {
                    final int[] delay = {0};
                    server.execute(new Runnable() {
                        @Override
                        public void run() {
                            delay[0]++;
                            if (delay[0] < 10) {
                                server.execute(this);
                            } else {
                                restoreBotsDelayedWithRetry(server, botsToRestore, nextIndex, 0);
                            }
                        }
                    });
                });
            }
        }
    }
    
    
    public static void updateBotData(MinecraftServer server) {
        int updated = 0;
        int skipped = 0;
        int missing = 0;
        for (String name : bots) {
            class_3222 bot = server.method_3760().method_14566(name);
            if (bot != null && bot.method_5805()) {

                botDataMap.put(name, new BotData(bot));
                updated++;
            } else if (!botDataMap.containsKey(name)) {


                missing++;
                System.out.println("[PVP_BOT] WARNING: Bot " + name + " in list but has no data!");
            } else {

                skipped++;
            }
        }

    }
    
    
    public static void saveBots() {
        if (savePath == null) return;
        
        try (Writer writer = Files.newBufferedWriter(savePath)) {
            GSON.toJson(botDataMap, writer);
        } catch (Exception e) {
            System.out.println("[PVP_BOT] Failed to save bots: " + e.getMessage());
        }
    }
    
    
    private static void loadBots() {
        if (savePath == null || !Files.exists(savePath)) {
            return;
        }
        
        try (Reader reader = Files.newBufferedReader(savePath)) {
            Map<String, BotData> loaded = GSON.fromJson(reader, new TypeToken<Map<String, BotData>>(){}.getType());
            if (loaded != null) {
                botDataMap.putAll(loaded);
                bots.addAll(loaded.keySet());
            }
        } catch (Exception e) {
            System.out.println("[PVP_BOT] Failed to load bots: " + e.getMessage());
        }
    }
    
    
    public static void reset(MinecraftServer server) {
        updateBotData(server);
        saveBots();
        initialized = false;
    }
    
    public static void switchWorld(MinecraftServer server) {

        updateBotData(server);
        saveBots();

        bots.clear();
        botDataMap.clear();


        savePath = org.stepan1411.pvp_bot.config.WorldConfigHelper.getWorldConfigDir().resolve("bots.json");


        loadBots();


        BotSettings settings = BotSettings.get();
        if (settings.isBotsRelogs() && !botDataMap.isEmpty()) {
            System.out.println("[PVP_BOT] Switching world, restoring " + botDataMap.size() + " bots...");
            Map<String, BotData> botsToRestore = new HashMap<>(botDataMap);
            bots.clear();
            botDataMap.clear();

            server.execute(() -> restoreBotsDelayed(server, botsToRestore, 0));
        }
    }


    public static boolean spawnBot(MinecraftServer server, String name, class_2168 source) {
        return spawnBot(server, name, source, null);
    }

    public static boolean spawnBot(MinecraftServer server, String name, class_2168 source, class_243 pos) {
        boolean isNewBot = !bots.contains(name);

        class_3222 existingPlayer = server.method_3760().method_14566(name);
        if (existingPlayer != null && existingPlayer.method_5805()) {

            if (!bots.contains(name)) {
                bots.add(name);
                botDataMap.put(name, new BotData(existingPlayer));
                saveBots();
                System.out.println("[PVP_BOT] Added existing bot to list: " + name);
            }
            return false;
        }


        var dispatcher = server.method_3734().method_9235();
        if (BotSettings.get().isProfileLagFix()) ProfileLagFix.preloadProfileCache(server, name);
        try {
            String coords = (pos != null)
                ? pos.field_1352 + " " + pos.field_1351 + " " + pos.field_1350
                : "~ ~ ~";
            dispatcher.execute("playerspawn " + name + " at " + coords + " facing 0 0 in survival", source);
        } catch (Exception e) {

            try {
                dispatcher.execute("playerspawn " + name, source);

                dispatcher.execute("gamemode survival " + name, server.method_3739());
            } catch (Exception e2) {

            }
        }



        server.execute(() -> {
            class_3222 newBot = server.method_3760().method_14566(name);
            if (newBot != null && !bots.contains(name)) {
                bots.add(name);
                botDataMap.put(name, new BotData(newBot));
                saveBots();
                System.out.println("[PVP_BOT] Added bot to list (delayed): " + name);
                

            } else if (newBot != null && bots.contains(name)) {

                botDataMap.put(name, new BotData(newBot));
                saveBots();
                System.out.println("[PVP_BOT] Updated bot data (delayed): " + name);
            }
        });
        

        class_3222 newBot = server.method_3760().method_14566(name);
        if (newBot != null) {
            if (!bots.contains(name)) {
                bots.add(name);
                botDataMap.put(name, new BotData(newBot));
                saveBots();
                System.out.println("[PVP_BOT] Added bot to list (immediate): " + name);
                StatsReporter.sendSpawn(name);

            }
            return true;
        }
        

        if (!bots.contains(name)) {
            bots.add(name);
            StatsReporter.sendSpawn(name);

            BotData defaultData = new BotData();
            defaultData.name = name;
            defaultData.x = source.method_9222().field_1352;
            defaultData.y = source.method_9222().field_1351;
            defaultData.z = source.method_9222().field_1350;
            defaultData.yaw = source.method_9210().field_1342;
            defaultData.pitch = source.method_9210().field_1343;
            defaultData.dimension = source.method_9225().method_27983().method_29177().toString();
            defaultData.gamemode = "survival";
            botDataMap.put(name, defaultData);
            saveBots();
            System.out.println("[PVP_BOT] Added bot to list (default data): " + name);
        }
        
        return true;
    }

    public static boolean removeBot(MinecraftServer server, String name, class_2168 source) {

        boolean wasInList = bots.remove(name);
        botDataMap.remove(name);
        saveBots();
        if (wasInList) StatsReporter.sendKill();
        

        BotCombat.removeState(name);
        BotUtils.removeState(name);
        BotNavigation.resetIdle(name);
        
        var dispatcher = server.method_3734().method_9235();
        if (BotSettings.get().isClearOnRemove()) {
            try {
                dispatcher.execute("clear " + name, server.method_3739());
            } catch (Exception e) { }
        }
        String command = "player " + name + " kill";
        try {
            dispatcher.execute(command, source);
        } catch (Exception e) {

        }
        

        
        return wasInList;
    }

    public static class_3222 getBot(MinecraftServer server, String name) {
        return server.method_3760().method_14566(name);
    }

    public static void removeAllBots(MinecraftServer server, class_2168 source) {
        var dispatcher = server.method_3734().method_9235();
        int count = bots.size();
        boolean clear = BotSettings.get().isClearOnRemove();
        for (String name : new HashSet<>(bots)) {

            BotCombat.removeState(name);
            BotUtils.removeState(name);
            BotNavigation.resetIdle(name);
            
            if (clear) {
                try {
                    dispatcher.execute("clear " + name, server.method_3739());
                } catch (Exception e) { }
            }

            String command = "player " + name + " kill";
            try {
                dispatcher.execute(command, source);
            } catch (Exception e) {

            }
        }
        bots.clear();
        botDataMap.clear();
        saveBots();
        for (int i = 0; i < count; i++) StatsReporter.sendKill();
        

    }

    public static int getBotCount() {
        return bots.size();
    }

    public static Set<String> getAllBots() {
        return new HashSet<>(bots);
    }
    
    public static void reloadBots() {
        bots.clear();
        botDataMap.clear();
        savePath = org.stepan1411.pvp_bot.config.WorldConfigHelper.getWorldConfigDir().resolve("bots.json");
        loadBots();
    }
    
    
    public static void cleanupDeadBots(MinecraftServer server) {
        boolean changed = false;
        for (String name : new HashSet<>(bots)) {
            class_3222 bot = server.method_3760().method_14566(name);

            if (bot == null) {
                int ticks = nullTickCount.getOrDefault(name, 0) + 1;
                nullTickCount.put(name, ticks);
                if (ticks >= MAX_NULL_TICKS) {
                    bots.remove(name);
                    botDataMap.remove(name);
                    BotCombat.removeState(name);
                    BotUtils.removeState(name);
                    BotNavigation.resetIdle(name);
                    nullTickCount.remove(name);
                    changed = true;
                    StatsReporter.sendKill();
                    System.out.println("[PVP_BOT] Removed dead bot (entity gone): " + name);
                }
                continue;
            }

            nullTickCount.remove(name);
            boolean isDead = !bot.method_5805() || bot.method_6032() <= 0 || bot.method_29504();
            if (isDead) {
                bots.remove(name);
                botDataMap.remove(name);
                BotCombat.removeState(name);
                BotUtils.removeState(name);
                BotNavigation.resetIdle(name);
                StatsReporter.sendKill();


                try {
                    var dispatcher = server.method_3734().method_9235();
                    dispatcher.execute("player " + name + " kill", server.method_3739());
                } catch (Exception e) {
                    System.err.println("[PVP_BOT] Error kicking dead bot: " + e.getMessage());
                }
                
                changed = true;
                System.out.println("[PVP_BOT] Removed dead bot: " + name);
            }
        }
        if (changed) {
            saveBots();
        }
    }
}

package org.stepan1411.pvp_bot.bot;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.class_243;
import net.minecraft.class_3222;
import net.minecraft.server.MinecraftServer;
import org.stepan1411.pvp_bot.stats.StatsReporter;

public class BotTicker {

    private static int tickCounter = 0;
    private static int autoSaveCounter = 0;
    private static int heroBotCommandCounter = 0;
    private static int statsReportCounter = 0;
    private static final int AUTO_SAVE_INTERVAL = 1200;
    private static final int HERO_BOT_COMMAND_INTERVAL = 20;
    private static final int STATS_REPORT_INTERVAL = 100;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(BotTicker::onServerTick);
    }

    private static void onServerTick(MinecraftServer server) {
        tickCounter++;
        autoSaveCounter++;
        
        int interval = BotSettings.get().getCheckInterval();
        

        if (tickCounter % 10 == 0) {
            BotManager.cleanupDeadBots(server);

        }
        


        if (autoSaveCounter >= AUTO_SAVE_INTERVAL) {
            BotManager.updateBotData(server);
            BotManager.saveBots();
            autoSaveCounter = 0;
        }
        

        BotPathVisualizer.update(server);
        
        for (String botName : BotManager.getAllBots()) {
            class_3222 bot = BotManager.getBot(server, botName);
            if (bot != null && bot.method_5805()) {

                boolean isElytraMacing = BotCombat.isElytraMaceActive(botName);
                boolean isWindBursting = BotCombat.isWindBurstActive(botName);

                if (!isElytraMacing && !isWindBursting) {
                    BotUtils.update(bot, server);
                    org.stepan1411.pvp_bot.fixes.EatingMovementFix.apply(bot);
                }

                boolean isFollowingWithoutAttack = BotPath.isFollowing(botName) && !BotPath.shouldAttack(botName);
                if (!isFollowingWithoutAttack) {
                    BotCombat.update(bot, server);
                }
                

                if (BotPath.isFollowing(botName)) {
                    boolean shouldAttack = BotPath.shouldAttack(botName);
                    var target = BotCombat.getTarget(botName);
                    boolean hasTarget = target != null && target.method_5805();
                    

                    if (shouldAttack && hasTarget) {
                        class_243 nextPoint = BotPath.getNextPoint(botName);
                        

                        if (!BotPath.isInCombat(botName)) {
                            BotPath.startCombat(botName, nextPoint);
                        }
                        


                        
                    } else {

                        

                        if (BotPath.isInCombat(botName)) {
                            BotPath.endCombat(botName);
                        }
                        

                        class_243 pausedPoint = BotPath.getPausedPoint(botName);
                        if (pausedPoint != null) {
                            class_243 botPos = new class_243(bot.method_23317(), bot.method_23318(), bot.method_23321());
                            double distanceToPaused = botPos.method_1022(pausedPoint);
                            

                            if (distanceToPaused < 1.5) {
                                BotPath.clearPausedPoint(botName);
                            } else {

                                BotNavigation.lookAtPosition(bot, pausedPoint);
                                BotNavigation.moveTowardPosition(bot, pausedPoint, 1.0);
                            }
                        } else {

                            class_243 nextPoint = BotPath.getNextPoint(botName);
                            if (nextPoint != null) {
                                class_243 botPos = new class_243(bot.method_23317(), bot.method_23318(), bot.method_23321());
                                double distance = botPos.method_1022(nextPoint);
                                

                                if (distance < 1.5) {
                                    BotPath.advanceToNextPoint(botName);
                                } else {

                                    BotNavigation.lookAtPosition(bot, nextPoint);
                                    BotNavigation.moveTowardPosition(bot, nextPoint, 1.0);
                                }
                            }
                        }
                    }
                }
                

                if (tickCounter >= interval) {
                    var utilsState = BotUtils.getState(botName);
                    if (!utilsState.isEating && !BotCombat.isElytraMaceActive(botName) && !BotCombat.isWindBurstActive(botName)) {
                        BotEquipment.autoEquip(bot);
                    }
                }
            }
        }
        
        if (tickCounter >= interval) {
            tickCounter = 0;
        }

        statsReportCounter++;
        if (statsReportCounter >= STATS_REPORT_INTERVAL) {
            statsReportCounter = 0;
            StatsReporter.sendHeartbeat();
        }

        heroBotCommandCounter++;
        if (heroBotCommandCounter >= HERO_BOT_COMMAND_INTERVAL) {
            heroBotCommandCounter = 0;
            try {
                var src = server.method_3739();
                var silent = new net.minecraft.class_2168(
                    new net.minecraft.class_2165() {
                        public void method_43496(net.minecraft.class_2561 message) {}
                        public boolean method_9200() { return false; }
                        public boolean method_9202() { return false; }
                        public boolean method_9201() { return false; }
                    },
                    src.method_9222(),
                    src.method_9210(),
                    src.method_9225(),
                    s -> true,
                    src.method_9214(),
                    src.method_9223(),
                    src.method_9211(),
                    src.method_9228()
                );
                boolean value = BotSettings.get().isBotLeaveOnDeath();
                server.method_3734().method_9235().execute("herobot botLeaveOnDeath " + value, silent);
            } catch (Exception e) {
            }
        }
    }
}

package org.stepan1411.pvp_bot.bot;

import net.minecraft.class_243;
import net.minecraft.class_3222;
import net.minecraft.server.MinecraftServer;


public class HerobotMovement {
    
    private static boolean herobotAvailable = false;
    
    static {
        try {

            Class.forName("hero.bane.herobot.HeroBot");
            herobotAvailable = true;
        } catch (ClassNotFoundException e) {
            herobotAvailable = false;
        }
    }
    
    
    public static boolean isHerobotAvailable() {
        return herobotAvailable;
    }
    
    
    public static boolean walkTowards(class_3222 bot, class_243 targetPos) {
        if (!herobotAvailable) {
            return false;
        }
        
        try {
            MinecraftServer server = bot.method_64396().method_9211();
            if (server == null) {
                return false;
            }
            
            String botName = bot.method_5477().getString();
            class_243 botPos = new class_243(bot.method_23317(), bot.method_23318(), bot.method_23321());
            

            double dx = targetPos.field_1352 - botPos.field_1352;
            double dz = targetPos.field_1350 - botPos.field_1350;
            double dist = Math.sqrt(dx * dx + dz * dz);
            
            if (dist < 0.5) {

                stopMovement(bot);
                return true;
            }
            

            double yaw = Math.toDegrees(Math.atan2(-dx, dz));
            

            executeCommand(server, String.format("player %s look %.1f 0", botName, yaw));
            

            executeCommand(server, String.format("player %s move forward", botName));
            

            if (dist > 3.0) {
                executeCommand(server, String.format("player %s sprint", botName));
            } else {
                executeCommand(server, String.format("player %s unsprint", botName));
            }
            

            double dy = targetPos.field_1351 - botPos.field_1351;
            if (dy > 0.5 && bot.method_24828()) {
                executeCommand(server, String.format("player %s jump", botName));
            }
            
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    
    public static void stopMovement(class_3222 bot) {
        if (!herobotAvailable) {
            return;
        }
        
        try {
            MinecraftServer server = bot.method_64396().method_9211();
            if (server == null) {
                return;
            }
            
            String botName = bot.method_5477().getString();
            

            executeCommand(server, String.format("player %s stop", botName));
            executeCommand(server, String.format("player %s unsprint", botName));
        } catch (Exception e) {

        }
    }
    
    
    public static void jump(class_3222 bot) {
        if (!herobotAvailable) {
            return;
        }
        
        try {
            MinecraftServer server = bot.method_64396().method_9211();
            if (server == null) {
                return;
            }
            
            String botName = bot.method_5477().getString();
            executeCommand(server, String.format("player %s jump", botName));
        } catch (Exception e) {

        }
    }
    
    
    public static void sprint(class_3222 bot, boolean enable) {
        if (!herobotAvailable) {
            return;
        }
        
        try {
            MinecraftServer server = bot.method_64396().method_9211();
            if (server == null) {
                return;
            }
            
            String botName = bot.method_5477().getString();
            if (enable) {
                executeCommand(server, String.format("player %s sprint", botName));
            } else {
                executeCommand(server, String.format("player %s unsprint", botName));
            }
        } catch (Exception e) {

        }
    }
    
    
    public static void lookAt(class_3222 bot, class_243 targetPos) {
        if (!herobotAvailable) {
            return;
        }
        
        try {
            MinecraftServer server = bot.method_64396().method_9211();
            if (server == null) {
                return;
            }
            
            String botName = bot.method_5477().getString();
            class_243 botPos = bot.method_33571();
            

            double dx = targetPos.field_1352 - botPos.field_1352;
            double dy = targetPos.field_1351 - botPos.field_1351;
            double dz = targetPos.field_1350 - botPos.field_1350;
            
            double horizontalDist = Math.sqrt(dx * dx + dz * dz);
            double yaw = Math.toDegrees(Math.atan2(-dx, dz));
            double pitch = -Math.toDegrees(Math.atan2(dy, horizontalDist));
            
            executeCommand(server, String.format("player %s look %.1f %.1f", botName, yaw, pitch));
        } catch (Exception e) {

        }
    }
    
    
    public static void executeCommand(MinecraftServer server, String command) {
        try {
            server.method_3734().method_9235().execute(command, server.method_3739());
        } catch (Exception e) {

        }
    }
}

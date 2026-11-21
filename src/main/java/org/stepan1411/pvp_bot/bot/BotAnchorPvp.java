package org.stepan1411.pvp_bot.bot;

import net.minecraft.class_1297;
import net.minecraft.class_1661;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_1937;
import net.minecraft.class_2338;
import net.minecraft.class_243;
import net.minecraft.class_2680;
import net.minecraft.class_3222;
import net.minecraft.class_4969;


public class BotAnchorPvp {
    

    private static class AnchorState {
        int step = 0;
        class_2338 lastAnchorPos = null;
        long lastActionTime = 0;
        int cooldownTicks = 0;
        int stuckCounter = 0;
        int lastStep = -1;
        int anchorNotFoundCounter = 0;
        int anchorPlaceFailCounter = 0;
        java.util.Set<class_2338> triedPositions = new java.util.HashSet<>();
        int anchorPlaceAttempts = 0;
    }
    
    private static final java.util.Map<String, AnchorState> states = new java.util.HashMap<>();
    
    
    private static AnchorState getState(String botName) {
        return states.computeIfAbsent(botName, k -> new AnchorState());
    }
    
    
    public static boolean canUseAnchorPvp(class_3222 bot, class_1297 target, BotSettings settings) {
        if (!settings.isAnchorPvpEnabled()) return false;
        

        String dimension = bot.method_51469().method_27983().method_29177().toString();
        if (dimension.contains("nether")) return false;
        
        double distance = bot.method_5739(target);
        if (distance < 2.0 || distance > 8.0) return false;
        

        double healthPercent = bot.method_6032() / bot.method_6063();
        if (healthPercent < 0.4) return false;
        
        class_1661 inventory = bot.method_31548();
        return hasRespawnAnchor(inventory) && hasGlowstone(inventory);
    }
    
    
    public static boolean doAnchorPvp(class_3222 bot, class_1297 target, BotSettings settings, net.minecraft.server.MinecraftServer server) {
        if (BotUtils.getState(bot.method_5477().getString()).isEating) {
            return false;
        }
        AnchorState state = getState(bot.method_5477().getString());
        class_1937 world = bot.method_51469();
        double distance = bot.method_5739(target);
        

        if (state.step == state.lastStep) {
            state.stuckCounter++;
            if (state.stuckCounter > 100) {
                System.out.println("[Anchor PVP] " + bot.method_5477().getString() + " STUCK on step " + state.step + "! Resetting state.");
                state.step = 0;
                state.lastAnchorPos = null;
                state.cooldownTicks = 0;
                state.stuckCounter = 0;
                return true;
            }
        } else {
            state.stuckCounter = 0;
            state.lastStep = state.step;
        }
        

        if (state.cooldownTicks > 0) {
            state.cooldownTicks--;
            maintainDistance(bot, target, settings);
            return true;
        }
        

        if (distance > 8.0) {
            moveToward(bot, target, settings.getMoveSpeed());
            state.step = 0;
            state.lastAnchorPos = null;
            return true;
        }
        

        if (distance < 2.0) {
            moveAway(bot, target, settings.getMoveSpeed());
            return true;
        }
        

        switch (state.step) {
            case 0:
                return stepPlaceAnchor(bot, target, state, server, world, settings);
                
            case 1:
                return stepChargeAnchor(bot, target, state, server, world, settings);
                
            case 2:
                return stepDetonateAnchor(bot, target, state, server, world, settings, distance);
                
            default:
                state.step = 0;
                return true;
        }
    }
    
    
    private static boolean stepPlaceAnchor(class_3222 bot, class_1297 target, AnchorState state,
                                          net.minecraft.server.MinecraftServer server, class_1937 world, BotSettings settings) {
        class_1661 inventory = bot.method_31548();
        

        class_2338 existingAnchor = findExistingAnchor(bot, target, world, 5.0);
        if (existingAnchor != null) {
            double distToExisting = Math.sqrt(bot.method_5649(existingAnchor.method_10263() + 0.5, existingAnchor.method_10264() + 0.5, existingAnchor.method_10260() + 0.5));
            
            if (distToExisting <= 4.0) {
                System.out.println("[Anchor PVP] " + bot.method_5477().getString() + " using existing anchor at " + existingAnchor);
                state.lastAnchorPos = existingAnchor;
                state.step = 1;
                state.cooldownTicks = 0;
                state.stuckCounter = 0;
                return true;
            } else {
                System.out.println("[Anchor PVP] " + bot.method_5477().getString() + " approaching existing anchor, distance: " + String.format("%.2f", distToExisting));
                moveToward(bot, target, settings.getMoveSpeed());
                return true;
            }
        }
        

        

        int anchorSlot = findRespawnAnchor(inventory);
        if (anchorSlot < 0) {
            return false;
        }
        

        class_2338 anchorPos = findBestAnchorPosition(bot, target, world, state.triedPositions);
        if (anchorPos == null) {
            System.out.println("[Anchor PVP] " + bot.method_5477().getString() + " could not find anchor position!");
            if (!state.triedPositions.isEmpty()) {
                System.out.println("[Anchor PVP] " + bot.method_5477().getString() + " clearing tried positions (" + state.triedPositions.size() + " positions)");
                state.triedPositions.clear();
            }
            maintainDistance(bot, target, settings);
            return true;
        }
        

        double distToPos = Math.sqrt(bot.method_5649(anchorPos.method_10263() + 0.5, anchorPos.method_10264() + 0.5, anchorPos.method_10260() + 0.5));
        
        if (distToPos > 3.0) {
            moveToward(bot, target, settings.getMoveSpeed());
            return true;
        }
        

        bot.method_18800(0, bot.method_18798().field_1351, 0);
        

        if (!selectItem(bot, anchorSlot)) {
            return true;
        }
        

        lookAt(bot, anchorPos);
        

        state.anchorPlaceAttempts++;
        

        if (state.anchorPlaceAttempts >= 3) {
            System.out.println("[Anchor PVP] " + bot.method_5477().getString() + " failed to place anchor 3 times at " + anchorPos + ", trying different position");
            state.triedPositions.add(anchorPos);
            state.anchorPlaceAttempts = 0;
            state.cooldownTicks = 3;
            return true;
        }
        

        try {
            server.method_3734().method_9235().execute(
                "player " + bot.method_5477().getString() + " use once",
                server.method_3739()
            );
            
            System.out.println("[Anchor PVP] " + bot.method_5477().getString() + " placed anchor at " + anchorPos + " (attempt " + state.anchorPlaceAttempts + "/3)");
            
            state.lastAnchorPos = anchorPos;
            state.step = 1;
            state.cooldownTicks = 3;
            state.stuckCounter = 0;
            state.anchorPlaceAttempts = 0;
            
        } catch (Exception e) {
            System.out.println("[Anchor PVP] " + bot.method_5477().getString() + " error placing anchor: " + e.getMessage());
        }
        
        return true;
    }

    
    private static boolean stepChargeAnchor(class_3222 bot, class_1297 target, AnchorState state,
                                           net.minecraft.server.MinecraftServer server, class_1937 world, BotSettings settings) {
        class_1661 inventory = bot.method_31548();
        

        if (state.lastAnchorPos == null) {
            System.out.println("[Anchor PVP] " + bot.method_5477().getString() + " no anchor position!");
            state.step = 0;
            return true;
        }
        

        class_2680 blockAtPos = world.method_8320(state.lastAnchorPos);
        
        if (!(blockAtPos.method_26204() instanceof class_4969)) {
            System.out.println("[Anchor PVP] " + bot.method_5477().getString() + " NO anchor at position! Returning to step 0");
            if (state.lastAnchorPos != null) {
                state.triedPositions.add(state.lastAnchorPos);
            }
            state.step = 0;
            state.lastAnchorPos = null;
            state.anchorPlaceAttempts = 0;
            return true;
        }
        

        int charges = blockAtPos.method_11654(class_4969.field_23153);
        
        if (charges >= 1) {

            System.out.println("[Anchor PVP] " + bot.method_5477().getString() + " anchor charged (" + charges + "/4), moving to step 2");
            state.step = 2;
            state.cooldownTicks = 0;
            return true;
        }
        

        int glowstoneSlot = findGlowstone(inventory);
        if (glowstoneSlot < 0) {
            System.out.println("[Anchor PVP] " + bot.method_5477().getString() + " no glowstone in inventory! Exiting Anchor PVP.");
            state.step = 0;
            state.lastAnchorPos = null;
            state.stuckCounter = 0;
            return false;
        }
        

        if (state.anchorPlaceFailCounter >= 5) {
            System.out.println("[Anchor PVP] " + bot.method_5477().getString() + " STUCK - anchor charging failed 5 times! Resetting state.");
            state.step = 0;
            state.lastAnchorPos = null;
            state.cooldownTicks = 10;
            state.anchorPlaceFailCounter = 0;
            state.triedPositions.clear();
            return true;
        }
        

        bot.method_18800(0, bot.method_18798().field_1351, 0);
        

        if (!selectItem(bot, glowstoneSlot)) {
            return true;
        }
        

        lookAt(bot, state.lastAnchorPos);
        

        try {
            server.method_3734().method_9235().execute(
                "player " + bot.method_5477().getString() + " use once",
                server.method_3739()
            );
            
            System.out.println("[Anchor PVP] " + bot.method_5477().getString() + " charged anchor once (charges: " + (charges + 1) + "/4)");
            

            state.step = 2;
            state.cooldownTicks = 2;
            state.stuckCounter = 0;
            
        } catch (Exception e) {
            System.out.println("[Anchor PVP] " + bot.method_5477().getString() + " error charging anchor: " + e.getMessage());
            state.step = 0;
            state.lastAnchorPos = null;
            state.anchorPlaceFailCounter = 0;
        }
        
        return true;
    }
    
    
    private static boolean stepDetonateAnchor(class_3222 bot, class_1297 target, AnchorState state,
                                             net.minecraft.server.MinecraftServer server, class_1937 world,
                                             BotSettings settings, double distance) {
        

        if (state.lastAnchorPos == null) {
            System.out.println("[Anchor PVP] " + bot.method_5477().getString() + " no anchor position!");
            state.step = 0;
            return true;
        }
        

        class_2680 blockAtPos = world.method_8320(state.lastAnchorPos);
        
        if (!(blockAtPos.method_26204() instanceof class_4969)) {
            System.out.println("[Anchor PVP] " + bot.method_5477().getString() + " NO anchor at position! Returning to step 0");
            state.anchorNotFoundCounter++;
            
            if (state.anchorNotFoundCounter >= 3) {
                System.out.println("[Anchor PVP] " + bot.method_5477().getString() + " STUCK - anchor not found 3 times! Resetting state.");
                state.step = 0;
                state.lastAnchorPos = null;
                state.cooldownTicks = 10;
                state.anchorNotFoundCounter = 0;
                state.anchorPlaceFailCounter = 0;
                state.triedPositions.clear();
                return true;
            }
            
            state.step = 0;
            state.lastAnchorPos = null;
            return true;
        }
        

        int charges = blockAtPos.method_11654(class_4969.field_23153);
        
        if (charges == 0) {

            System.out.println("[Anchor PVP] " + bot.method_5477().getString() + " anchor not charged, returning to step 1");
            state.step = 1;
            state.cooldownTicks = 0;
            return true;
        }
        

        double distToAnchor = Math.sqrt(bot.method_5649(
            state.lastAnchorPos.method_10263() + 0.5,
            state.lastAnchorPos.method_10264() + 0.5,
            state.lastAnchorPos.method_10260() + 0.5
        ));
        

        double targetDistToAnchor = Math.sqrt(target.method_5649(
            state.lastAnchorPos.method_10263() + 0.5,
            state.lastAnchorPos.method_10264() + 0.5,
            state.lastAnchorPos.method_10260() + 0.5
        ));
        

        if (distToAnchor < 3.0) {
            System.out.println("[Anchor PVP] " + bot.method_5477().getString() + " too close to anchor (" + String.format("%.2f", distToAnchor) + "), retreating");
            moveAway(bot, target, settings.getMoveSpeed());
            return true;
        }
        

        if (targetDistToAnchor > 5.0) {
            System.out.println("[Anchor PVP] " + bot.method_5477().getString() + " target too far from anchor (" + String.format("%.2f", targetDistToAnchor) + "), waiting");
            maintainDistance(bot, target, settings);
            return true;
        }
        

        state.anchorNotFoundCounter = 0;
        state.anchorPlaceFailCounter = 0;
        

        bot.method_18800(0, bot.method_18798().field_1351, 0);
        

        class_1661 inventory = bot.method_31548();
        int anchorSlot = findRespawnAnchor(inventory);
        if (anchorSlot >= 0) {
            selectItem(bot, anchorSlot);
        } else {

            int weaponSlot = findMeleeWeapon(inventory);
            if (weaponSlot >= 0) {
                selectItem(bot, weaponSlot);
            }
        }
        

        lookAt(bot, state.lastAnchorPos);
        

        try {
            server.method_3734().method_9235().execute(
                "player " + bot.method_5477().getString() + " use once",
                server.method_3739()
            );
            
            System.out.println("[Anchor PVP] " + bot.method_5477().getString() + " DETONATED anchor! (charges: " + charges + ")");
            

            state.step = 0;
            state.lastAnchorPos = null;
            state.cooldownTicks = 5;
            state.stuckCounter = 0;
            
        } catch (Exception e) {
            System.out.println("[Anchor PVP] " + bot.method_5477().getString() + " error detonating anchor: " + e.getMessage());
            state.step = 0;
            state.lastAnchorPos = null;
        }
        
        return true;
    }
    
    
    private static void maintainDistance(class_3222 bot, class_1297 target, BotSettings settings) {
        double distance = bot.method_5739(target);
        
        if (distance < 3.0) {
            moveAway(bot, target, settings.getMoveSpeed() * 0.7);
        } else if (distance > 5.5) {
            moveToward(bot, target, settings.getMoveSpeed() * 0.7);
        } else {
            lookAtEntity(bot, target);
        }
    }
    
    
    private static void moveToward(class_3222 bot, class_1297 target, double speed) {
        class_243 targetPos = new class_243(target.method_23317(), target.method_23318(), target.method_23321());
        class_243 botPos = new class_243(bot.method_23317(), bot.method_23318(), bot.method_23321());
        class_243 direction = targetPos.method_1020(botPos).method_1029();
        
        bot.method_18800(direction.field_1352 * speed, bot.method_18798().field_1351, direction.field_1350 * speed);
        bot.field_64356 = true;
        
        String botName = bot.method_5477().getString();
        String pathWalkType = null;
        if (BotPath.isFollowing(botName)) {
            var follower = BotPath.getFollower(botName);
            if (follower != null) {
                var pathData = BotPath.getPath(follower.pathName);
                if (pathData != null) {
                    pathWalkType = pathData.walkType;
                }
            }
        }
        
        if (pathWalkType != null) {
            if (pathWalkType.equals("bhop") && bot.method_24828()) {
                bot.method_6043();
                bot.method_5728(true);
            } else if (pathWalkType.equals("sprint")) {
                bot.method_5728(true);
            } else if (pathWalkType.equals("walk")) {
                bot.method_5728(false);
            }
        }
        
        lookAtEntity(bot, target);
    }
    
    
    private static void moveAway(class_3222 bot, class_1297 target, double speed) {
        class_243 targetPos = new class_243(target.method_23317(), target.method_23318(), target.method_23321());
        class_243 botPos = new class_243(bot.method_23317(), bot.method_23318(), bot.method_23321());
        class_243 direction = botPos.method_1020(targetPos).method_1029();
        
        bot.method_18800(direction.field_1352 * speed, bot.method_18798().field_1351, direction.field_1350 * speed);
        bot.field_64356 = true;
        
        lookAtEntity(bot, target);
    }
    
    
    private static void lookAt(class_3222 bot, class_2338 pos) {
        class_243 target = new class_243(pos.method_10263() + 0.5, pos.method_10264() + 0.5, pos.method_10260() + 0.5);
        class_243 botPos = bot.method_33571();
        
        double dx = target.field_1352 - botPos.field_1352;
        double dy = target.field_1351 - botPos.field_1351;
        double dz = target.field_1350 - botPos.field_1350;
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        
        float yaw = (float) (Math.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
        float pitch = (float) -(Math.atan2(dy, horizontalDist) * (180.0 / Math.PI));
        
        bot.method_36456(yaw);
        bot.method_36457(pitch);
        bot.method_5847(yaw);
    }
    
    
    private static void lookAtEntity(class_3222 bot, class_1297 target) {
        class_243 targetPos = target.method_33571();
        class_243 botPos = bot.method_33571();
        
        double dx = targetPos.field_1352 - botPos.field_1352;
        double dy = targetPos.field_1351 - botPos.field_1351;
        double dz = targetPos.field_1350 - botPos.field_1350;
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        
        float yaw = (float) (Math.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
        float pitch = (float) -(Math.atan2(dy, horizontalDist) * (180.0 / Math.PI));
        
        bot.method_36456(yaw);
        bot.method_36457(pitch);
        bot.method_5847(yaw);
    }
    
    
    private static boolean selectItem(class_3222 bot, int slot) {
        class_1661 inventory = bot.method_31548();
        

        if (slot >= 9) {
            class_1799 item = inventory.method_5438(slot);
            class_1799 current = inventory.method_5438(0);
            inventory.method_5447(slot, current);
            inventory.method_5447(0, item);
            slot = 0;
        }
        

        org.stepan1411.pvp_bot.utils.InventoryHelper.setSelectedSlot(inventory, slot);
        return true;
    }
    
    
    private static class_2338 findExistingAnchor(class_3222 bot, class_1297 target, class_1937 world, double maxDistance) {
        class_2338 targetPos = target.method_24515();
        
        int radius = (int) Math.ceil(maxDistance);
        
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    class_2338 pos = targetPos.method_10069(dx, dy, dz);
                    
                    double distFromTarget = Math.sqrt(target.method_5649(pos.method_10263() + 0.5, pos.method_10264() + 0.5, pos.method_10260() + 0.5));
                    if (distFromTarget > maxDistance) continue;
                    
                    class_2680 blockState = world.method_8320(pos);
                    if (!(blockState.method_26204() instanceof class_4969)) continue;
                    
                    double distFromBot = Math.sqrt(bot.method_5649(pos.method_10263() + 0.5, pos.method_10264() + 0.5, pos.method_10260() + 0.5));
                    if (distFromBot > 4.0) continue;
                    
                    System.out.println("[Anchor PVP] Found suitable anchor at " + pos + ", distance from enemy: " + String.format("%.2f", distFromTarget) + ", from bot: " + String.format("%.2f", distFromBot));
                    return pos;
                }
            }
        }
        
        return null;
    }
    
    
    private static class_2338 findBestAnchorPosition(class_3222 bot, class_1297 target, class_1937 world, java.util.Set<class_2338> triedPositions) {
        class_2338 targetPos = target.method_24515();
        
        class_2338[] candidates = {
            targetPos.method_10095(),
            targetPos.method_10072(),
            targetPos.method_10078(),
            targetPos.method_10067(),
            targetPos.method_10095().method_10078(),
            targetPos.method_10095().method_10067(),
            targetPos.method_10072().method_10078(),
            targetPos.method_10072().method_10067(),
        };
        
        for (class_2338 pos : candidates) {
            if (triedPositions.contains(pos)) continue;
            
            if (!world.method_8320(pos).method_26215() && !world.method_8320(pos).method_45474()) continue;
            
            double dist = Math.sqrt(bot.method_5649(pos.method_10263() + 0.5, pos.method_10264() + 0.5, pos.method_10260() + 0.5));
            
            if (dist <= 4.0) {
                System.out.println("[Anchor PVP] Found suitable position: " + pos);
                return pos;
            }
        }
        
        System.out.println("[Anchor PVP] No suitable positions found!");
        return null;
    }
    
    
    private static int findRespawnAnchor(class_1661 inventory) {
        for (int i = 0; i < 36; i++) {
            class_1799 stack = inventory.method_5438(i);
            if (stack.method_7909() == class_1802.field_23141) return i;
        }
        return -1;
    }
    
    
    private static int findGlowstone(class_1661 inventory) {
        for (int i = 0; i < 36; i++) {
            class_1799 stack = inventory.method_5438(i);
            if (stack.method_7909() == class_1802.field_8801) return i;
        }
        return -1;
    }
    
    
    private static int findMeleeWeapon(class_1661 inventory) {
        int bestSlot = -1;
        int bestPriority = -1;
        
        for (int i = 0; i < 36; i++) {
            class_1799 stack = inventory.method_5438(i);
            int priority = -1;
            
            if (stack.method_7909().toString().contains("sword")) {
                priority = 10;
            } else if (stack.method_7909().toString().contains("axe")) {
                priority = 5;
            }
            
            if (priority > bestPriority) {
                bestPriority = priority;
                bestSlot = i;
            }
        }
        
        return bestSlot;
    }
    
    
    private static boolean hasRespawnAnchor(class_1661 inventory) {
        return findRespawnAnchor(inventory) >= 0;
    }
    
    
    private static boolean hasGlowstone(class_1661 inventory) {
        return findGlowstone(inventory) >= 0;
    }
    
    
    public static void reset(String botName) {
        states.remove(botName);
    }
}

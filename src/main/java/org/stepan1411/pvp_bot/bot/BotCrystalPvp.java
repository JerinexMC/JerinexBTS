package org.stepan1411.pvp_bot.bot;

import net.minecraft.class_1268;
import net.minecraft.class_1297;
import net.minecraft.class_1661;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_1937;
import net.minecraft.class_2338;
import net.minecraft.class_243;
import net.minecraft.class_3222;


public class BotCrystalPvp {
    

    private static class CrystalState {
        int step = 0;
        class_2338 lastObsidianPos = null;
        long lastActionTime = 0;
        int cooldownTicks = 0;
        int stuckCounter = 0;
        int lastStep = -1;
        int crystalNotFoundCounter = 0;
        int crystalPlaceFailCounter = 0;
        java.util.Set<class_2338> triedPositions = new java.util.HashSet<>();
        int obsidianPlaceAttempts = 0;
        

        boolean swordAttackDone = false;
        int swordAttackCooldown = 0;
    }
    
    private static final java.util.Map<String, CrystalState> states = new java.util.HashMap<>();
    
    
    private static CrystalState getState(String botName) {
        return states.computeIfAbsent(botName, k -> new CrystalState());
    }
    
    
    public static boolean canUseCrystalPvp(class_3222 bot, class_1297 target, BotSettings settings) {
        if (!settings.isCrystalPvpEnabled()) return false;
        
        double distance = bot.method_5739(target);
        if (distance < 2.5 || distance > 8.0) return false;
        
        class_1661 inventory = bot.method_31548();
        return hasObsidian(inventory) && hasEndCrystal(inventory);
    }
    
    
    public static boolean doCrystalPvp(class_3222 bot, class_1297 target, BotSettings settings, net.minecraft.server.MinecraftServer server) {
        if (BotUtils.getState(bot.method_5477().getString()).isEating) {
            return false;
        }
        CrystalState state = getState(bot.method_5477().getString());
        class_1937 world = bot.method_51469();
        double distance = bot.method_5739(target);
        

        if (state.step == state.lastStep) {
            state.stuckCounter++;
            if (state.stuckCounter > 100) {
                System.out.println("[Crystal PVP] " + bot.method_5477().getString() + " STUCK on step " + state.step + "! Resetting state.");
                state.step = 0;
                state.lastObsidianPos = null;
                state.cooldownTicks = 0;
                state.stuckCounter = 0;
                state.swordAttackDone = false;
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
        

        if (state.swordAttackCooldown > 0) {
            state.swordAttackCooldown--;
        }
        

        if (distance > 8.0) {
            moveToward(bot, target, settings.getMoveSpeed());
            state.step = 0;
            state.lastObsidianPos = null;
            return true;
        }
        

        if (distance < 2.5) {
            moveAway(bot, target, settings.getMoveSpeed());
            return true;
        }
        


        class_1297 nearCrystal = findNearestEndCrystal(bot, target, 6.0);
        if (nearCrystal != null && state.step != 2 && state.cooldownTicks == 0) {
            System.out.println("[Crystal PVP] " + bot.method_5477().getString() + " РѕР±РЅР°СЂСѓР¶РёР» РєСЂРёСЃС‚Р°Р»Р», РїРµСЂРµС…РѕРґ Рє С€Р°РіСѓ 2 (С‚РµРєСѓС‰РёР№ step: " + state.step + ")");
            state.step = 2;
            state.cooldownTicks = 0;
        }
        

        switch (state.step) {
            case 0:
                return stepPlaceObsidian(bot, target, state, server, world, settings);
                
            case 1:
                return stepPlaceCrystal(bot, target, state, server, world, settings);
                
            case 2:
                return stepAttackCrystal(bot, target, state, server, world, settings, distance);
                
            default:
                state.step = 0;
                return true;
        }
    }
    
    
    private static boolean stepPlaceObsidian(class_3222 bot, class_1297 target, CrystalState state, 
                                            net.minecraft.server.MinecraftServer server, class_1937 world, BotSettings settings) {
        class_1661 inventory = bot.method_31548();
        

        if (!state.swordAttackDone && state.swordAttackCooldown <= 0) {
            double distance = bot.method_5739(target);
            if (distance <= settings.getMeleeRange() + 1.0) {
                System.out.println("[Crystal PVP] " + bot.method_5477().getString() + " Step 0: Attacking player with sword first");
                boolean attacked = attackPlayerWithSword(bot, target, state, server, settings);
                if (attacked) {
                    state.swordAttackDone = true;
                    state.swordAttackCooldown = 10;
                    return true;
                }
            }
        }
        

        class_2338 existingObsidian = findExistingObsidian(bot, target, world, 5.0);
        if (existingObsidian != null) {
            double distToExisting = Math.sqrt(bot.method_5649(existingObsidian.method_10263() + 0.5, existingObsidian.method_10264() + 0.5, existingObsidian.method_10260() + 0.5));
            
            if (distToExisting <= 4.0) {

                System.out.println("[Crystal PVP] " + bot.method_5477().getString() + " using existing obsidian at " + existingObsidian);
                state.lastObsidianPos = existingObsidian;
                state.step = 1;
                state.cooldownTicks = 0;
                state.stuckCounter = 0;
                state.swordAttackDone = false;
                return true;
            } else {

                System.out.println("[Crystal PVP] " + bot.method_5477().getString() + " approaching existing obsidian, distance: " + String.format("%.2f", distToExisting));
                moveToward(bot, target, settings.getMoveSpeed());
                return true;
            }
        }
        

        

        int obsidianSlot = findObsidian(inventory);
        if (obsidianSlot < 0) {
            return false;
        }
        

        class_2338 obsidianPos = findBestObsidianPosition(bot, target, world, state.triedPositions);
        if (obsidianPos == null) {
            System.out.println("[Crystal PVP] " + bot.method_5477().getString() + " could not find obsidian position!");

            if (!state.triedPositions.isEmpty()) {
                System.out.println("[Crystal PVP] " + bot.method_5477().getString() + " clearing tried positions (" + state.triedPositions.size() + " positions)");
                state.triedPositions.clear();
            }

            maintainDistance(bot, target, settings);
            return true;
        }
        

        double distToPos = Math.sqrt(bot.method_5649(obsidianPos.method_10263() + 0.5, obsidianPos.method_10264() + 0.5, obsidianPos.method_10260() + 0.5));
        
        if (distToPos > 3.0) {
            moveToward(bot, target, settings.getMoveSpeed());
            return true;
        }
        

        bot.method_18800(0, bot.method_18798().field_1351, 0);
        

        if (!selectItem(bot, obsidianSlot)) {
            return true;
        }
        

        lookAt(bot, obsidianPos);
        

        state.obsidianPlaceAttempts++;
        

        if (state.obsidianPlaceAttempts >= 3) {
            System.out.println("[Crystal PVP] " + bot.method_5477().getString() + " failed to place obsidian 3 times at " + obsidianPos + ", trying different position");
            state.triedPositions.add(obsidianPos);
            state.obsidianPlaceAttempts = 0;
            state.cooldownTicks = 3;
            return true;
        }
        

        try {
            server.method_3734().method_9235().execute(
                "player " + bot.method_5477().getString() + " use once", 
                server.method_3739()
            );
            
            System.out.println("[Crystal PVP] " + bot.method_5477().getString() + " placed obsidian at " + obsidianPos + " (attempt " + state.obsidianPlaceAttempts + "/3)");
            

            state.lastObsidianPos = obsidianPos;
            state.step = 1;
            state.cooldownTicks = 5;
            state.stuckCounter = 0;
            state.obsidianPlaceAttempts = 0;
            state.swordAttackDone = false;
            
        } catch (Exception e) {
            System.out.println("[Crystal PVP] " + bot.method_5477().getString() + " error placing obsidian: " + e.getMessage());

        }
        

        return true;
    }
    
    
    private static boolean stepPlaceCrystal(class_3222 bot, class_1297 target, CrystalState state,
                                           net.minecraft.server.MinecraftServer server, class_1937 world, BotSettings settings) {
        class_1661 inventory = bot.method_31548();
        

        if (!state.swordAttackDone && state.swordAttackCooldown <= 0) {
            double distance = bot.method_5739(target);
            if (distance <= settings.getMeleeRange() + 1.0) {
                System.out.println("[Crystal PVP] " + bot.method_5477().getString() + " Step 1: Attacking player with sword first");
                boolean attacked = attackPlayerWithSword(bot, target, state, server, settings);
                if (attacked) {
                    state.swordAttackDone = true;
                    state.swordAttackCooldown = 10;
                    return true;
                }
            }
        }
        

        if (state.lastObsidianPos == null) {
            System.out.println("[Crystal PVP] " + bot.method_5477().getString() + " no obsidian position!");
            state.step = 0;
            return true;
        }
        

        net.minecraft.class_2680 blockAtPos = world.method_8320(state.lastObsidianPos);
        
        if (!blockAtPos.method_26204().toString().contains("obsidian")) {
            System.out.println("[Crystal PVP] " + bot.method_5477().getString() + " NO obsidian at position! Returning to step 0");

            if (state.lastObsidianPos != null) {
                state.triedPositions.add(state.lastObsidianPos);
            }
            state.step = 0;
            state.lastObsidianPos = null;
            state.obsidianPlaceAttempts = 0;
            return true;
        }
        

        net.minecraft.class_2680 blockAbove = world.method_8320(state.lastObsidianPos.method_10084());
        
        if (!blockAbove.method_26215() && !blockAbove.method_45474()) {
            System.out.println("[Crystal PVP] " + bot.method_5477().getString() + " NO space above obsidian! Returning to step 0");
            state.step = 0;
            state.lastObsidianPos = null;
            return true;
        }
        

        int crystalSlot = findEndCrystal(inventory);
        if (crystalSlot < 0) {
            System.out.println("[Crystal PVP] " + bot.method_5477().getString() + " no crystals in inventory! Exiting Crystal PVP.");
            state.step = 0;
            state.lastObsidianPos = null;
            state.stuckCounter = 0;
            return false;
        }
        

        bot.method_18800(0, bot.method_18798().field_1351, 0);
        

        if (!selectItem(bot, crystalSlot)) {
            return true;
        }
        


        lookAt(bot, state.lastObsidianPos);
        

        if (state.crystalPlaceFailCounter >= 5) {
            System.out.println("[Crystal PVP] " + bot.method_5477().getString() + " STUCK - crystal placement failed 5 times! Resetting state.");
            state.step = 0;
            state.lastObsidianPos = null;
            state.cooldownTicks = 5;
            state.crystalPlaceFailCounter = 0;
            return true;
        }
        

        try {
            server.method_3734().method_9235().execute(
                "player " + bot.method_5477().getString() + " use once", 
                server.method_3739()
            );
            
            System.out.println("[Crystal PVP] " + bot.method_5477().getString() + " placed crystal (clicked obsidian at " + state.lastObsidianPos + ") - attempt " + (state.crystalPlaceFailCounter + 1) + "/5");
            

            state.crystalPlaceFailCounter++;
            

            state.step = 2;
            state.cooldownTicks = 5;
            state.stuckCounter = 0;
            state.swordAttackDone = false;
            
        } catch (Exception e) {
            System.out.println("[Crystal PVP] " + bot.method_5477().getString() + " error placing crystal: " + e.getMessage());

            state.step = 0;
            state.lastObsidianPos = null;
            state.crystalPlaceFailCounter = 0;
        }
        

        return true;
    }
    
    
    private static boolean stepAttackCrystal(class_3222 bot, class_1297 target, CrystalState state,
                                            net.minecraft.server.MinecraftServer server, class_1937 world, 
                                            BotSettings settings, double distance) {
        class_1661 inventory = bot.method_31548();
        

        bot.method_18800(0, bot.method_18798().field_1351, 0);
        

        int weaponSlot = findMeleeWeapon(inventory);
        if (weaponSlot >= 0) {
            selectItem(bot, weaponSlot);
        }
        

        class_1297 crystal = findNearestEndCrystal(bot, target, 6.0);
        
        if (crystal != null) {
            System.out.println("[Crystal PVP] " + bot.method_5477().getString() + " found crystal at distance " + bot.method_5739(crystal));
            

            lookAtEntity(bot, crystal);
            

            state.crystalNotFoundCounter = 0;

            state.crystalPlaceFailCounter = 0;
            

            bot.method_7324(crystal);
            bot.method_6104(class_1268.field_5808);
            System.out.println("[Crystal PVP] " + bot.method_5477().getString() + " hit crystal!");
            

            if (distance <= 4.5 && state.lastObsidianPos != null) {

                state.step = 1;
                state.cooldownTicks = 2;
                state.swordAttackDone = false;
                System.out.println("[Crystal PVP] " + bot.method_5477().getString() + " moving to step 1 (new crystal)");
            } else {

                state.step = 0;
                state.lastObsidianPos = null;
                state.cooldownTicks = 5;
                state.swordAttackDone = false;
                System.out.println("[Crystal PVP] " + bot.method_5477().getString() + " moving to step 0 (new obsidian)");
            }
            
        } else {
            state.crystalNotFoundCounter++;
            System.out.println("[Crystal PVP] " + bot.method_5477().getString() + " DID NOT FIND crystal! (" + state.crystalNotFoundCounter + "/3)");
            

            if (state.crystalNotFoundCounter >= 3) {
                System.out.println("[Crystal PVP] " + bot.method_5477().getString() + " STUCK - crystal not found 3 times! Resetting state.");
                state.step = 0;
                state.lastObsidianPos = null;
                state.cooldownTicks = 5;
                state.crystalNotFoundCounter = 0;
                return true;
            }
            

            if (state.lastObsidianPos != null) {
                state.step = 1;
                state.swordAttackDone = false;
                System.out.println("[Crystal PVP] " + bot.method_5477().getString() + " returning to step 1 (place crystal)");
            } else {
                state.step = 0;
                state.swordAttackDone = false;
                System.out.println("[Crystal PVP] " + bot.method_5477().getString() + " returning to step 0 (place obsidian)");
            }
            state.cooldownTicks = 3;
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
    
    
    private static void strafe(class_3222 bot, class_1297 target, double speed) {
        class_243 targetPos = new class_243(target.method_23317(), target.method_23318(), target.method_23321());
        class_243 botPos = new class_243(bot.method_23317(), bot.method_23318(), bot.method_23321());
        class_243 toTarget = targetPos.method_1020(botPos).method_1029();
        

        class_243 strafeDir = new class_243(-toTarget.field_1350, 0, toTarget.field_1352);
        
        bot.method_18800(strafeDir.field_1352 * speed, bot.method_18798().field_1351, strafeDir.field_1350 * speed);
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
    
    
    private static class_2338 findExistingObsidian(class_3222 bot, class_1297 target, class_1937 world, double maxDistance) {
        class_2338 targetPos = target.method_24515();
        

        int radius = (int) Math.ceil(maxDistance);
        
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    class_2338 pos = targetPos.method_10069(dx, dy, dz);
                    

                    double distFromTarget = Math.sqrt(target.method_5649(pos.method_10263() + 0.5, pos.method_10264() + 0.5, pos.method_10260() + 0.5));
                    if (distFromTarget > maxDistance) continue;
                    

                    net.minecraft.class_2680 blockState = world.method_8320(pos);
                    if (!blockState.method_26204().toString().contains("obsidian")) continue;
                    

                    net.minecraft.class_2680 blockAbove = world.method_8320(pos.method_10084());
                    if (!blockAbove.method_26215() && !blockAbove.method_45474()) continue;
                    

                    double distFromBot = Math.sqrt(bot.method_5649(pos.method_10263() + 0.5, pos.method_10264() + 0.5, pos.method_10260() + 0.5));
                    if (distFromBot > 4.0) {
                        continue;
                    }
                    
                    System.out.println("[Crystal PVP] Found suitable obsidian at " + pos + ", distance from enemy: " + String.format("%.2f", distFromTarget) + ", from bot: " + String.format("%.2f", distFromBot));
                    return pos;
                }
            }
        }
        
        return null;
    }
    
    
    private static class_2338 findBestObsidianPosition(class_3222 bot, class_1297 target, class_1937 world, java.util.Set<class_2338> triedPositions) {
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

            if (triedPositions.contains(pos)) {
                continue;
            }
            

            if (!world.method_8320(pos).method_26215() && !world.method_8320(pos).method_45474()) {
                continue;
            }
            

            if (!world.method_8320(pos.method_10084()).method_26215() && !world.method_8320(pos.method_10084()).method_45474()) {
                continue;
            }
            

            double dist = Math.sqrt(bot.method_5649(pos.method_10263() + 0.5, pos.method_10264() + 0.5, pos.method_10260() + 0.5));
            
            if (dist <= 4.0) {
                System.out.println("[Crystal PVP] Found suitable position: " + pos);
                return pos;
            }
        }
        
        System.out.println("[Crystal PVP] No suitable positions found!");
        return null;
    }
    
    
    private static class_1297 findNearestEndCrystal(class_3222 bot, class_1297 target, double maxDistance) {
        class_1937 world = bot.method_51469();
        net.minecraft.class_238 searchBox = target.method_5829().method_1014(maxDistance);
        
        class_1297 nearestCrystal = null;
        double nearestDist = maxDistance + 1;
        int crystalCount = 0;
        
        
        for (class_1297 entity : world.method_8335(bot, searchBox)) {
            
            if (entity instanceof net.minecraft.class_1511) {
                crystalCount++;
                double dist = target.method_5739(entity);
                System.out.println("[Crystal PVP] Р­С‚Рѕ РєСЂРёСЃС‚Р°Р»Р»! Р”РёСЃС‚Р°РЅС†РёСЏ from target: " + String.format("%.2f", dist));
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearestCrystal = entity;
                }
            }
        }
        
        if (crystalCount > 0) {
            System.out.println("[Crystal PVP] " + bot.method_5477().getString() + " found " + crystalCount + " crystals in radius " + maxDistance);
        } else {
            System.out.println("[Crystal PVP] " + bot.method_5477().getString() + " РќР• found РЅРё РѕРґРЅРѕРіРѕ РєСЂРёСЃС‚Р°Р»Р»Р°!");
        }
        
        return nearestCrystal;
    }
    
    
    private static int findObsidian(class_1661 inventory) {
        for (int i = 0; i < 36; i++) {
            class_1799 stack = inventory.method_5438(i);
            if (stack.method_7909() == class_1802.field_8281) return i;
        }
        return -1;
    }
    
    
    private static int findEndCrystal(class_1661 inventory) {
        for (int i = 0; i < 36; i++) {
            class_1799 stack = inventory.method_5438(i);
            if (stack.method_7909() == class_1802.field_8301) return i;
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
    
    
    private static boolean hasObsidian(class_1661 inventory) {
        return findObsidian(inventory) >= 0;
    }
    
    
    private static boolean hasEndCrystal(class_1661 inventory) {
        return findEndCrystal(inventory) >= 0;
    }
    
    
    private static boolean attackPlayerWithSword(class_3222 bot, class_1297 target, CrystalState state, 
                                                net.minecraft.server.MinecraftServer server, BotSettings settings) {
        class_1661 inventory = bot.method_31548();
        double distance = bot.method_5739(target);
        

        if (distance > settings.getMeleeRange()) {

            moveToward(bot, target, settings.getMoveSpeed());
            return true;
        }
        

        int weaponSlot = findMeleeWeapon(inventory);
        if (weaponSlot >= 0) {
            selectItem(bot, weaponSlot);
        }
        

        lookAtEntity(bot, target);
        

        if (bot.method_7261(0.5f) < 1.0f) {
            return true;
        }
        

        try {
            server.method_3734().method_9235().execute(
                "player " + bot.method_5477().getString() + " attack once", 
                server.method_3739()
            );
            System.out.println("[Crystal PVP] " + bot.method_5477().getString() + " attacked player with sword!");
            return true;
        } catch (Exception e) {
            System.out.println("[Crystal PVP] " + bot.method_5477().getString() + " error attacking with sword: " + e.getMessage());
            bot.method_6104(class_1268.field_5808);
            return true;
        }
    }
    
    
    public static void reset(String botName) {
        CrystalState state = states.get(botName);
        if (state != null) {
            state.step = 0;
            state.lastObsidianPos = null;
            state.cooldownTicks = 0;
            state.stuckCounter = 0;
            state.lastStep = -1;
            state.crystalNotFoundCounter = 0;
            state.crystalPlaceFailCounter = 0;
            state.triedPositions.clear();
            state.obsidianPlaceAttempts = 0;
            state.swordAttackDone = false;
            state.swordAttackCooldown = 0;
        }
    }
}

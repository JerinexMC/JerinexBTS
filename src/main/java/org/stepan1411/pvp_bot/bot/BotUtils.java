package org.stepan1411.pvp_bot.bot;

import net.minecraft.class_1268;
import net.minecraft.class_1294;
import net.minecraft.class_1297;
import net.minecraft.class_1657;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_1803;
import net.minecraft.class_1812;
import net.minecraft.class_1828;
import net.minecraft.class_3222;
import net.minecraft.class_9334;
import net.minecraft.item.*;
import net.minecraft.server.MinecraftServer;
import java.util.HashMap;
import java.util.Map;

public class BotUtils {
    
    private static final Map<String, BotState> botStates = new HashMap<>();
    
    public static class BotState {
        public int shieldCooldown = 0;
        public int eatCooldown = 0;
        public boolean isBlocking = false;
        public boolean isEating = false;
        public int eatingTicks = 0;
        public int windChargeCooldown = 0;
        public int eatingSlot = -1;
        public int potionCooldown = 0;
        public int buffPotionCooldown = 0;
        public boolean isThrowingPotion = false;
        public int throwingPotionTicks = 0;
        public boolean isMending = false;
        public int mendingCooldown = 0;
        public int xpBottlesThrown = 0;
        public int xpBottlesNeeded = 0;
        public java.util.List<Integer> potionsToThrow = new java.util.ArrayList<>();
        public class_1799 savedOffhandItem = class_1799.field_8037;
        public int savedMainHandSlot = -1;
        public int blockHoldTicks = 0;
        
        public boolean isInCobweb = false;
        public boolean isEscapingCobweb = false;
        public int cobwebEscapeTicks = 0;
        public int cobwebEscapeSlot = -1;
        public net.minecraft.class_2338 waterPosition = null;
        public boolean needsToCollectWater = false;
    }
    
    public static BotState getState(String botName) {
        return botStates.computeIfAbsent(botName, k -> new BotState());
    }
    
    public static void removeState(String botName) {
        botStates.remove(botName);
    }
    
    
    public static void update(class_3222 bot, MinecraftServer server) {
        BotSettings settings = BotSettings.get();
        BotState state = getState(bot.method_5477().getString());
        

        if (state.shieldCooldown > 0) state.shieldCooldown--;
        if (state.eatCooldown > 0) state.eatCooldown--;
        if (state.windChargeCooldown > 0) state.windChargeCooldown--;
        if (state.potionCooldown > 0) state.potionCooldown--;
        if (state.buffPotionCooldown > 0) state.buffPotionCooldown--;
        if (state.mendingCooldown > 0) state.mendingCooldown--;
        

        if (settings.isAutoMendEnabled()) {
            boolean needsMending = handleAutoMend(bot, state, settings, server);
            if (needsMending) {
                return;
            }
        }
        

        if (state.isThrowingPotion) {
            state.throwingPotionTicks++;

            bot.method_36457(90);
            
            if (state.throwingPotionTicks == 2) {

                executeCommand(server, bot, "player " + bot.method_5477().getString() + " use once");
            }
            if (state.throwingPotionTicks >= 5) {

                if (!state.potionsToThrow.isEmpty()) {
                    int nextSlot = state.potionsToThrow.remove(0);
                    var inventory = bot.method_31548();
                    

                    if (nextSlot >= 9) {
                        class_1799 potion = inventory.method_5438(nextSlot);
                        class_1799 current = inventory.method_5438(8);
                        inventory.method_5447(nextSlot, current);
                        inventory.method_5447(8, potion);
                        nextSlot = 8;
                    }
                    
                    org.stepan1411.pvp_bot.utils.InventoryHelper.setSelectedSlot(inventory, nextSlot);
                    state.throwingPotionTicks = 0;
                } else {

                    state.isThrowingPotion = false;
                    state.throwingPotionTicks = 0;
                }
            }
            return;
        }
        

        handleSwimming(bot);
        

        if (settings.isAutoTotemEnabled()) {
            handleAutoTotem(bot);
        }
        

        if (settings.isAutoPotionEnabled() && !state.isEating) {
            handleAutoBuffPotions(bot, state, server);
        }
        

        if (settings.isAutoEatEnabled()) {
            boolean criticalHunger = bot.method_7344().method_7586() <= 6;
            if (state.isEating || !state.isBlocking || criticalHunger) {
                if (state.isBlocking && criticalHunger) {
                    if (shouldUseMainHandShield(bot)) {
                        stopMainHandBlocking(bot, state, server);
                    } else {
                        stopBlocking(bot, state, server);
                    }
                }
                handleAutoEat(bot, state, settings, server);
            }
        }
        
        handleCobwebEscape(bot, state, server);

        if (settings.isAutoShieldEnabled() && !state.isEating) {
            handleAutoShield(bot, state, settings, server);
        }
    }
    
    
    private static void handleSwimming(class_3222 bot) {
        if (bot.method_5799() || bot.method_5869()) {
            bot.method_5796(true);
            

            if (bot.method_5869()) {
                bot.method_5762(0, 0.08, 0);
                bot.method_5728(true);
            } else if (bot.method_5799()) {
                bot.method_5762(0, 0.04, 0);
            }
            

            if (bot.method_24828() && bot.method_5799()) {
                bot.method_6043();
            }
        }
    }
    
    
    private static void handleAutoTotem(class_3222 bot) {
        BotState state = getState(bot.method_5477().getString());
        

        if (state.isBlocking) {
            return;
        }
        
        var inventory = bot.method_31548();
        class_1799 offhand = inventory.method_5438(40);
        
        if (offhand.method_7909() == class_1802.field_8288) return;
        
        for (int i = 0; i < 36; i++) {
            class_1799 stack = inventory.method_5438(i);
            if (stack.method_7909() == class_1802.field_8288) {
                inventory.method_5447(i, offhand.method_7972());
                inventory.method_5447(40, stack.method_7972());
                return;
            }
        }
    }
    
    
    private static void handleAutoBuffPotions(class_3222 bot, BotState state, MinecraftServer server) {

        var combatState = BotCombat.getState(bot.method_5477().getString());
        if (combatState.target == null) return;
        
        if (state.buffPotionCooldown > 0) return;
        if (state.isThrowingPotion) return;
        
        var inventory = bot.method_31548();
        

        java.util.List<Integer> potionsToUse = new java.util.ArrayList<>();
        

        boolean needStrength = !hasEffect(bot, class_1294.field_5910, 100);
        boolean needSpeed = !hasEffect(bot, class_1294.field_5904, 100);
        boolean needFireResist = !hasEffect(bot, class_1294.field_5918, 100);
        
        if (needStrength) {
            int slot = findSplashBuffPotion(inventory, "strength");
            if (slot >= 0) potionsToUse.add(slot);
        }
        
        if (needSpeed) {
            int slot = findSplashBuffPotion(inventory, "swiftness");
            if (slot < 0) slot = findSplashBuffPotion(inventory, "speed");
            if (slot >= 0) potionsToUse.add(slot);
        }
        
        if (needFireResist) {
            int slot = findSplashBuffPotion(inventory, "fire_resistance");
            if (slot >= 0) potionsToUse.add(slot);
        }
        

        if (!potionsToUse.isEmpty()) {

            int firstSlot = potionsToUse.remove(0);
            

            if (firstSlot >= 9) {
                class_1799 potion = inventory.method_5438(firstSlot);
                class_1799 current = inventory.method_5438(8);
                inventory.method_5447(firstSlot, current);
                inventory.method_5447(8, potion);
                firstSlot = 8;
            }
            
            org.stepan1411.pvp_bot.utils.InventoryHelper.setSelectedSlot(inventory, firstSlot);
            

            state.potionsToThrow.clear();
            state.potionsToThrow.addAll(potionsToUse);
            

            state.isThrowingPotion = true;
            state.throwingPotionTicks = 0;
            state.buffPotionCooldown = 100;
        }
    }
    
    
    private static int findSplashBuffPotion(net.minecraft.class_1661 inventory, String effectName) {
        for (int i = 0; i < 36; i++) {
            class_1799 stack = inventory.method_5438(i);
            if (stack.method_7960()) continue;
            
            class_1792 item = stack.method_7909();

            if (!(item instanceof class_1828) && !(item instanceof class_1803)) {
                continue;
            }
            
            var potionContents = stack.method_58694(class_9334.field_49651);
            if (potionContents == null) continue;
            

            var potion = potionContents.comp_2378();
            if (potion.isPresent()) {
                String potionName = potion.get().method_55840().toLowerCase();
                if (potionName.contains(effectName)) {
                    return i;
                }
            }
        }
        return -1;
    }
    
    
    private static boolean hasEffect(class_3222 bot, net.minecraft.class_6880<net.minecraft.class_1291> effect, int minDuration) {
        var instance = bot.method_6112(effect);
        if (instance == null) return false;
        return instance.method_5584() > minDuration;
    }
    
    
    private static int findBuffPotion(net.minecraft.class_1661 inventory, String effectName) {
        for (int i = 0; i < 36; i++) {
            class_1799 stack = inventory.method_5438(i);
            if (stack.method_7960()) continue;
            
            class_1792 item = stack.method_7909();
            if (!(item instanceof class_1812) && !(item instanceof class_1828) && !(item instanceof class_1803)) {
                continue;
            }
            
            var potionContents = stack.method_58694(class_9334.field_49651);
            if (potionContents == null) continue;
            

            var potion = potionContents.comp_2378();
            if (potion.isPresent()) {
                String potionName = potion.get().method_55840().toLowerCase();
                if (potionName.contains(effectName)) {
                    return i;
                }
            }
        }
        return -1;
    }
    
    
    private static boolean useBuffPotion(class_3222 bot, BotState state, int slot, MinecraftServer server) {
        var inventory = bot.method_31548();
        class_1799 potionStack = inventory.method_5438(slot);
        class_1792 potionItem = potionStack.method_7909();
        

        if (slot >= 9) {
            class_1799 current = inventory.method_5438(8);
            inventory.method_5447(slot, current);
            inventory.method_5447(8, potionStack);
            slot = 8;
        }
        

        org.stepan1411.pvp_bot.utils.InventoryHelper.setSelectedSlot(inventory, slot);
        
        if (potionItem instanceof class_1828 || potionItem instanceof class_1803) {

            state.isThrowingPotion = true;
            state.throwingPotionTicks = 0;
            state.buffPotionCooldown = 15;
            return true;
        } else if (potionItem instanceof class_1812) {

            state.isEating = true;
            state.eatingTicks = 0;
            state.eatingSlot = slot;
            state.buffPotionCooldown = 10;
            bot.method_6019(class_1268.field_5808);
            return true;
        }
        
        return false;
    }
    
    
    private static void handleAutoEat(class_3222 bot, BotState state, BotSettings settings, MinecraftServer server) {

        if (!settings.isAutoEatEnabled()) {
            if (state.isEating) {
                executeCommand(server, bot, "player " + bot.method_5477().getString() + " stop");
                state.isEating = false;
                state.eatingTicks = 0;
                state.eatingSlot = -1;
            }
            return;
        }
        
        int hunger = bot.method_7344().method_7586();
        float health = bot.method_6032();
        float maxHealth = bot.method_6063();
        
        boolean needFood = hunger <= settings.getMinHungerToEat();
        boolean needHealth = health <= maxHealth * 0.5f;
        boolean criticalHealth = health <= maxHealth * 0.3f;
        

        var combatState = BotCombat.getState(bot.method_5477().getString());
        boolean isRetreating = combatState.isRetreating;
        
        if (state.isEating) {
            state.eatingTicks++;
            

            if (state.eatingSlot >= 0 && state.eatingSlot < 9) {
                org.stepan1411.pvp_bot.utils.InventoryHelper.setSelectedSlot(bot.method_31548(), state.eatingSlot);
            }
            

            class_1799 foodStack = bot.method_6047();
            if (foodStack.method_7909().method_57347().method_58694(class_9334.field_50075) != null) {

                bot.method_6019(class_1268.field_5808);
            }
            

            if (state.eatingTicks >= 80) {
                executeCommand(server, bot, "player " + bot.method_5477().getString() + " stop");
                state.isEating = false;
                state.eatingTicks = 0;
                state.eatingSlot = -1;
                state.eatCooldown = 10;
                

                hunger = bot.method_7344().method_7586();
                health = bot.method_6032();
                if (health <= maxHealth * 0.5f || hunger < 18) {
                    state.eatCooldown = 0;
                }
            }
            return;
        }
        



        boolean shouldEat = criticalHealth || needHealth || needFood;
        

        boolean shouldEatGoldenApple = needHealth && hasGoldenApple(bot.method_31548());
        
        if ((shouldEat || shouldEatGoldenApple) && state.eatCooldown <= 0 && !state.isBlocking) {
 
            if (combatState.isUsingShield) {
                try {
                    server.method_3734().method_9235().execute(
                        "player " + bot.method_5477().getString() + " stop",
                        server.method_3739()
                    );
                } catch (Exception e) {}
                combatState.isUsingShield = false;
                combatState.shieldToggleCooldown = 20;
            }
 
            int foodSlot = findBestFood(bot.method_31548(), needHealth || criticalHealth);
            if (foodSlot >= 0) {
                var inventory = bot.method_31548();
                
 
                class_1799 foodStack = inventory.method_5438(foodSlot);
                if (!foodStack.method_7960() && foodStack.method_7909().method_57347().method_58694(class_9334.field_50075) != null) {
 
                    if (foodSlot >= 9) {
                        class_1799 food = inventory.method_5438(foodSlot);
                        class_1799 current = inventory.method_5438(8);
                        inventory.method_5447(foodSlot, current);
                        inventory.method_5447(8, food);
                        foodSlot = 8;
                    }
                    
                    state.eatingSlot = foodSlot;
                    
 
                    org.stepan1411.pvp_bot.utils.InventoryHelper.setSelectedSlot(inventory, foodSlot);
                    
 
                    executeCommand(server, bot, "player " + bot.method_5477().getString() + " use continuous");
                    state.isEating = true;
                    state.eatingTicks = 0;
                }
            }
        }
    }
    
    
    private static boolean hasGoldenApple(net.minecraft.class_1661 inventory) {
        for (int i = 0; i < 36; i++) {
            class_1792 item = inventory.method_5438(i).method_7909();
            if (item == class_1802.field_8463 || item == class_1802.field_8367) {
                return true;
            }
        }
        return false;
    }

    
    private static int findBestFood(net.minecraft.class_1661 inventory, boolean preferGoldenApple) {
        int bestSlot = -1;
        int bestValue = 0;
        
        for (int i = 0; i < 36; i++) {
            class_1799 stack = inventory.method_5438(i);
            if (stack.method_7960()) continue;
            
            int value = getFoodValue(stack.method_7909(), preferGoldenApple);
            if (value > bestValue) {
                bestValue = value;
                bestSlot = i;
            }
        }
        return bestSlot;
    }
    
    private static int getFoodValue(class_1792 item, boolean preferGoldenApple) {
        if (preferGoldenApple) {
            if (item == class_1802.field_8367) return 100;
            if (item == class_1802.field_8463) return 90;
        }
        if (item == class_1802.field_8071) return 80;
        if (item == class_1802.field_8176) return 70;
        if (item == class_1802.field_8261) return 70;
        if (item == class_1802.field_8347) return 65;
        if (item == class_1802.field_8509) return 60;
        if (item == class_1802.field_8373) return 55;
        if (item == class_1802.field_8544) return 50;
        if (item == class_1802.field_8229) return 45;
        if (item == class_1802.field_8512) return 45;
        if (item == class_1802.field_8279) return 30;
        if (item == class_1802.field_8179) return 25;
        
        var foodComponent = item.method_57347().method_58694(class_9334.field_50075);
        if (foodComponent != null) {
            return foodComponent.comp_2491() * 5;
        }
        return 0;
    }
    
    
    private static void handleAutoShield(class_3222 bot, BotState state, BotSettings settings, MinecraftServer server) {
        var inventory = bot.method_31548();
        int shieldSlot = findShield(inventory);
        if (shieldSlot < 0) {
            state.isBlocking = false;
            return;
        }
        

        var combatState = BotCombat.getState(bot.method_5477().getString());

        if (combatState.isMaceDefending) {
            return;
        }

        var target = combatState.target;

        if (target == null) {
            if (state.isBlocking) {
                if (shouldUseMainHandShield(bot)) {
                    stopMainHandBlocking(bot, state, server);
                } else {
                    stopBlocking(bot, state, server);
                }
            }
            return;
        }

        if (state.shieldCooldown > 0 && !state.isBlocking) {
            return;
        }

        double distance = bot.method_5739(target);
        boolean isRetreating = combatState.isRetreating;
        float health = bot.method_6032();
        float maxHealth = bot.method_6063();
        boolean lowHealth = health <= maxHealth * 0.3f;

        boolean shouldBlock = false;

        if (distance <= 4.0) {
            if (target instanceof class_1657 player && player.field_6252) {
                shouldBlock = true;
            }
            if (isRetreating && lowHealth) {
                shouldBlock = true;
            }
        }

        if (state.isEating) {
            shouldBlock = false;
        }

        if (shouldBlock && !state.isBlocking) {
            if (shouldUseMainHandShield(bot)) {
                startMainHandBlocking(bot, state, server);
            } else {
                startBlocking(bot, state, shieldSlot, server);
            }
            state.blockHoldTicks = settings.getShieldHoldTicks();
            state.shieldCooldown = 30;
        }

        if (shouldBlock) {
            state.blockHoldTicks = settings.getShieldHoldTicks();
        } else if (state.isBlocking) {
            state.blockHoldTicks--;
        }

        if (!shouldBlock && state.isBlocking && state.blockHoldTicks <= 0) {
            if (shouldUseMainHandShield(bot)) {
                stopMainHandBlocking(bot, state, server);
            } else {
                stopBlocking(bot, state, server);
            }
        }
    }
    
    private static void startBlocking(class_3222 bot, BotState state, int shieldSlot, MinecraftServer server) {
        var inventory = bot.method_31548();
        
        if (shieldSlot != 40) {
            class_1799 shield = inventory.method_5438(shieldSlot);
            class_1799 offhand = inventory.method_5438(40);
            

            state.savedOffhandItem = offhand.method_7972();
            

            inventory.method_5447(shieldSlot, offhand);
            inventory.method_5447(40, shield);
        }
        

        executeCommand(server, bot, "player " + bot.method_5477().getString() + " use continuous");
        state.isBlocking = true;
    }
    
    private static void stopBlocking(class_3222 bot, BotState state, MinecraftServer server) {
        executeCommand(server, bot, "player " + bot.method_5477().getString() + " stop");
        state.isBlocking = false;
        
        var inventory = bot.method_31548();
        class_1799 currentOffhand = inventory.method_5438(40);
        

        if (currentOffhand.method_7909() == class_1802.field_8255 && !state.savedOffhandItem.method_7960()) {

            int emptySlot = -1;
            for (int i = 0; i < 36; i++) {
                if (inventory.method_5438(i).method_7960()) {
                    emptySlot = i;
                    break;
                }
            }
            
            if (emptySlot >= 0) {

                inventory.method_5447(emptySlot, currentOffhand.method_7972());

                inventory.method_5447(40, state.savedOffhandItem.method_7972());
            }
            

            state.savedOffhandItem = class_1799.field_8037;
        }
    }
    
    private static int findShield(net.minecraft.class_1661 inventory) {
        if (inventory.method_5438(40).method_7909() == class_1802.field_8255) return 40;
        for (int i = 0; i < 36; i++) {
            if (inventory.method_5438(i).method_7909() == class_1802.field_8255) return i;
        }
        return -1;
    }

    public static boolean shouldUseMainHandShield(class_3222 bot) {
        if (!BotSettings.get().isTotemPriority()) return false;
        return bot.method_31548().method_5438(40).method_7909() == class_1802.field_8288;
    }

    private static final int SHIELD_HOTBAR_SLOT = 1;

    public static int equipShieldToMainHand(class_3222 bot) {
        var inventory = bot.method_31548();
        int shieldSlot = findShield(inventory);
        if (shieldSlot < 0 || shieldSlot == 40) return -1;

        class_1799 shield = inventory.method_5438(shieldSlot);
        class_1799 slot2 = inventory.method_5438(SHIELD_HOTBAR_SLOT);
        inventory.method_5447(SHIELD_HOTBAR_SLOT, shield);
        inventory.method_5447(shieldSlot, slot2);
        org.stepan1411.pvp_bot.utils.InventoryHelper.setSelectedSlot(inventory, SHIELD_HOTBAR_SLOT);
        return SHIELD_HOTBAR_SLOT;
    }

    public static void unequipShieldFromMainHand(class_3222 bot) {
        var inventory = bot.method_31548();
        class_1799 shieldStack = inventory.method_5438(SHIELD_HOTBAR_SLOT);
        if (shieldStack.method_7909() != class_1802.field_8255) return;

        for (int i = 9; i < 36; i++) {
            if (inventory.method_5438(i).method_7960()) {
                inventory.method_5447(i, shieldStack);
                inventory.method_5447(SHIELD_HOTBAR_SLOT, class_1799.field_8037);
                return;
            }
        }
    }

    private static void startMainHandBlocking(class_3222 bot, BotState state, MinecraftServer server) {
        state.savedMainHandSlot = org.stepan1411.pvp_bot.utils.InventoryHelper.getSelectedSlot(bot.method_31548());
        equipShieldToMainHand(bot);
        executeCommand(server, bot, "player " + bot.method_5477().getString() + " use continuous");
        state.isBlocking = true;
    }

    private static void stopMainHandBlocking(class_3222 bot, BotState state, MinecraftServer server) {
        executeCommand(server, bot, "player " + bot.method_5477().getString() + " stop");
        state.isBlocking = false;
        unequipShieldFromMainHand(bot);
        if (state.savedMainHandSlot >= 0) {
            org.stepan1411.pvp_bot.utils.InventoryHelper.setSelectedSlot(bot.method_31548(), state.savedMainHandSlot);
            state.savedMainHandSlot = -1;
        }
    }

    public static void useWindCharge(class_3222 bot, MinecraftServer server) {
        BotState state = getState(bot.method_5477().getString());
        if (state.windChargeCooldown > 0) return;
        if (state.isEating) return;
        
        var inventory = bot.method_31548();
        int slot = findWindCharge(inventory);
        if (slot < 0) return;
        

        if (slot >= 9) {
            class_1799 wc = inventory.method_5438(slot);
            class_1799 current = inventory.method_5438(0);
            inventory.method_5447(slot, current);
            inventory.method_5447(0, wc);
            slot = 0;
        }
        
        org.stepan1411.pvp_bot.utils.InventoryHelper.setSelectedSlot(inventory, slot);
        

        bot.method_36457(90);
        

        executeCommand(server, bot, "player " + bot.method_5477().getString() + " use once");
        
        state.windChargeCooldown = 20;
    }
    
    private static int findWindCharge(net.minecraft.class_1661 inventory) {
        for (int i = 0; i < 36; i++) {
            if (inventory.method_5438(i).method_7909() == class_1802.field_49098) return i;
        }
        return -1;
    }
    
    
    public static boolean tryDisableShield(class_3222 bot, class_1297 target) {

        BotState state = getState(bot.method_5477().getString());
        if (state.isEating) return false;
        
        if (!(target instanceof class_1657 player)) return false;
        if (!player.method_6039()) return false;
        
        var inventory = bot.method_31548();
        int axeSlot = findAxe(inventory);
        if (axeSlot < 0) return false;
        
        if (axeSlot >= 9) {
            class_1799 axe = inventory.method_5438(axeSlot);
            class_1799 current = inventory.method_5438(0);
            inventory.method_5447(axeSlot, current);
            inventory.method_5447(0, axe);
            org.stepan1411.pvp_bot.utils.InventoryHelper.setSelectedSlot(inventory, 0);
        } else {
            org.stepan1411.pvp_bot.utils.InventoryHelper.setSelectedSlot(inventory, axeSlot);
        }
        return true;
    }
    
    private static int findAxe(net.minecraft.class_1661 inventory) {
        int[] priorities = {-1, -1, -1, -1, -1, -1};
        for (int i = 0; i < 36; i++) {
            class_1792 item = inventory.method_5438(i).method_7909();
            if (item == class_1802.field_22025) priorities[0] = i;
            else if (item == class_1802.field_8556) priorities[1] = i;
            else if (item == class_1802.field_8475) priorities[2] = i;
            else if (item == class_1802.field_8062) priorities[3] = i;
            else if (item == class_1802.field_8825) priorities[4] = i;
            else if (item == class_1802.field_8406) priorities[5] = i;
        }
        for (int slot : priorities) {
            if (slot >= 0) return slot;
        }
        return -1;
    }
    
    
    private static void executeCommand(MinecraftServer server, class_3222 bot, String command) {
        try {
            server.method_3734().method_9235().execute(command, server.method_3739());
        } catch (Exception e) {

        }
    }
    
    
    public static boolean hasFood(class_3222 bot) {
        var inventory = bot.method_31548();
        for (int i = 0; i < 36; i++) {
            class_1799 stack = inventory.method_5438(i);
            if (!stack.method_7960()) {
                var foodComponent = stack.method_7909().method_57347().method_58694(class_9334.field_50075);
                if (foodComponent != null) {
                    return true;
                }
            }
        }
        return false;
    }
    
    
    public static boolean tryUseHealingPotion(class_3222 bot, MinecraftServer server) {
        BotState state = getState(bot.method_5477().getString());
        if (state.isEating) return false;
        if (state.potionCooldown > 0) return false;
        
        var inventory = bot.method_31548();
        

        int potionSlot = findHealingPotion(inventory);
        if (potionSlot < 0) return false;
        
        class_1799 potionStack = inventory.method_5438(potionSlot);
        class_1792 potionItem = potionStack.method_7909();
        

        if (potionSlot >= 9) {
            class_1799 current = inventory.method_5438(8);
            inventory.method_5447(potionSlot, current);
            inventory.method_5447(8, potionStack);
            potionSlot = 8;
        }
        

        org.stepan1411.pvp_bot.utils.InventoryHelper.setSelectedSlot(inventory, potionSlot);
        
        if (potionItem instanceof class_1828 || potionItem instanceof class_1803) {

            state.isThrowingPotion = true;
            state.throwingPotionTicks = 0;
            state.potionCooldown = 10;
            return true;
        } else if (potionItem instanceof class_1812) {

            state.isEating = true;
            state.eatingTicks = 0;
            state.eatingSlot = potionSlot;
            state.potionCooldown = 5;
            bot.method_6019(class_1268.field_5808);
            return true;
        }
        
        return false;
    }
    
    
    private static int findHealingPotion(net.minecraft.class_1661 inventory) {

        int splashSlot = -1;
        int normalSlot = -1;
        
        for (int i = 0; i < 36; i++) {
            class_1799 stack = inventory.method_5438(i);
            if (stack.method_7960()) continue;
            
            class_1792 item = stack.method_7909();
            

            if (isHealingPotion(stack)) {
                if (item instanceof class_1828 || item instanceof class_1803) {
                    if (splashSlot < 0) splashSlot = i;
                } else if (item instanceof class_1812) {
                    if (normalSlot < 0) normalSlot = i;
                }
            }
        }
        

        return splashSlot >= 0 ? splashSlot : normalSlot;
    }
    
    
    private static boolean isHealingPotion(class_1799 stack) {
        var potionContents = stack.method_58694(class_9334.field_49651);
        if (potionContents == null) return false;
        

        for (var effect : potionContents.method_57397()) {
            var effectType = effect.method_5579().comp_349();
            String effectName = effectType.toString().toLowerCase();
            if (effectName.contains("healing") || effectName.contains("instant_health")) {
                return true;
            }
        }
        

        var potion = potionContents.comp_2378();
        if (potion.isPresent()) {
            String potionName = potion.get().method_55840().toLowerCase();
            if (potionName.contains("healing") || potionName.contains("health")) {
                return true;
            }
        }
        
        return false;
    }
    
    
    private static boolean handleAutoMend(class_3222 bot, BotState state, BotSettings settings, MinecraftServer server) {
        var inventory = bot.method_31548();
        

        int xpBottleSlot = findXpBottle(inventory);
        if (xpBottleSlot < 0) {
            state.isMending = false;
            state.xpBottlesThrown = 0;
            state.xpBottlesNeeded = 0;
            return false;
        }
        

        int totalDamageToRepair = 0;
        int itemsNeedingRepair = 0;
        
        for (int armorSlot = 36; armorSlot < 40; armorSlot++) {
            class_1799 armorPiece = inventory.method_5438(armorSlot);
            if (armorPiece.method_7960()) continue;
            

            if (!hasMendingEnchantment(armorPiece)) continue;
            

            int maxDamage = armorPiece.method_7936();
            int currentDamage = armorPiece.method_7919();
            double durabilityPercent = 1.0 - ((double) currentDamage / maxDamage);
            

            if (durabilityPercent < settings.getMendDurabilityThreshold()) {

                int targetDamage = (int) (maxDamage * 0.1);
                int damageToRepair = currentDamage - targetDamage;
                if (damageToRepair > 0) {
                    totalDamageToRepair += damageToRepair;
                    itemsNeedingRepair++;
                }
            }
        }
        
        if (totalDamageToRepair <= 0) {

            state.isMending = false;
            state.xpBottlesThrown = 0;
            state.xpBottlesNeeded = 0;
            return false;
        }
        

        if (!state.isMending) {



            state.xpBottlesNeeded = (totalDamageToRepair / 28) + 2;
            if (state.xpBottlesNeeded < 5) state.xpBottlesNeeded = 5;
            state.xpBottlesThrown = 0;
        }
        

        state.isMending = true;
        

        var combatState = BotCombat.getState(bot.method_5477().getString());
        class_1297 target = combatState.target;
        

        if (target != null) {
            BotNavigation.lookAway(bot, target);
            BotNavigation.moveAway(bot, target, 1.3);
        }
        

        if (state.xpBottlesThrown >= state.xpBottlesNeeded) {

            state.isMending = false;
            state.xpBottlesThrown = 0;
            state.xpBottlesNeeded = 0;
            return false;
        }
        

        if (xpBottleSlot >= 9) {
            class_1799 xpBottle = inventory.method_5438(xpBottleSlot);
            class_1799 current = inventory.method_5438(8);
            inventory.method_5447(xpBottleSlot, current);
            inventory.method_5447(8, xpBottle);
            xpBottleSlot = 8;
        }
        

        org.stepan1411.pvp_bot.utils.InventoryHelper.setSelectedSlot(inventory, xpBottleSlot);
        

        bot.method_36457(90);
        

        executeCommand(server, bot, "player " + bot.method_5477().getString() + " use once");
        
        state.xpBottlesThrown++;
        
        return true;
    }
    
    
    private static boolean hasMendingEnchantment(class_1799 stack) {
        var enchantments = stack.method_58694(class_9334.field_49633);
        if (enchantments == null) return false;
        

        for (var entry : enchantments.method_57534()) {
            String enchantName = entry.method_55840().toLowerCase();
            if (enchantName.contains("mending")) {
                return true;
            }
        }
        return false;
    }
    
    
    private static int findXpBottle(net.minecraft.class_1661 inventory) {
        for (int i = 0; i < 36; i++) {
            if (inventory.method_5438(i).method_7909() == class_1802.field_8287) {
                return i;
            }
        }
        return -1;
    }
    
    
    public static void handleCobwebEscape(class_3222 bot, BotState state, MinecraftServer server) {
        boolean inCobweb = bot.method_51469().method_8320(bot.method_24515()).method_26204() == net.minecraft.class_2246.field_10343;
        
        if (state.needsToCollectWater && state.waterPosition != null) {
            net.minecraft.class_243 botPos = new net.minecraft.class_243(bot.method_23317(), bot.method_23318(), bot.method_23321());
            double distToWater = botPos.method_1022(net.minecraft.class_243.method_24953(state.waterPosition));
            
            System.out.println("[COBWEB] Returning for water. Distance: " + distToWater + ", Position: " + state.waterPosition);
            
            if (distToWater < 1.5) {
                net.minecraft.class_2680 blockStateAtWater = bot.method_51469().method_8320(state.waterPosition);
                net.minecraft.class_2248 blockAtWater = blockStateAtWater.method_26204();
                
                System.out.println("[COBWEB] Close to water! Block: " + blockAtWater + ", isWater: " + blockStateAtWater.method_27852(net.minecraft.class_2246.field_10382));
                
                if (blockStateAtWater.method_27852(net.minecraft.class_2246.field_10382)) {
                    System.out.println("[COBWEB] Collecting water at saved position...");
                    
                    if (state.cobwebEscapeSlot >= 0 && state.cobwebEscapeSlot < 9) {
                        org.stepan1411.pvp_bot.utils.InventoryHelper.setSelectedSlot(bot.method_31548(), state.cobwebEscapeSlot);
                    }
                    
                    bot.method_36457(90.0f);
                    executeCommand(server, bot, "player " + bot.method_5477().getString() + " use once");
                    
                    state.needsToCollectWater = false;
                    state.waterPosition = null;
                    state.cobwebEscapeSlot = -1;
                    System.out.println("[COBWEB] Water collected successfully!");
                    return;
                } else {
                    System.out.println("[COBWEB] Water not found at position, cancelling...");
                    state.needsToCollectWater = false;
                    state.waterPosition = null;
                    return;
                }
            }
            
            net.minecraft.class_243 waterPos = net.minecraft.class_243.method_24953(state.waterPosition);
            BotNavigation.lookAtPosition(bot, waterPos);
            BotNavigation.moveTowardPosition(bot, waterPos, 0.8);
            return;
        }
        
        if (state.isEscapingCobweb) {
            state.cobwebEscapeTicks++;
            

            bot.method_36457(90.0f);
            bot.method_18800(0, bot.method_18798().field_1351, 0);
            

            if (state.cobwebEscapeSlot >= 0 && state.cobwebEscapeSlot < 9) {
                org.stepan1411.pvp_bot.utils.InventoryHelper.setSelectedSlot(bot.method_31548(), state.cobwebEscapeSlot);
            }
            
            if (state.cobwebEscapeTicks == 5) {

                net.minecraft.class_2338 waterPos = bot.method_24515().method_10074();
                state.waterPosition = waterPos;
                
                System.out.println("[COBWEB] Tick 5 - Collecting water at position: " + waterPos);
                executeCommand(server, bot, "player " + bot.method_5477().getString() + " use once");
                

                class_1799 currentItem = bot.method_31548().method_5438(state.cobwebEscapeSlot);
                if (currentItem.method_7909() != class_1802.field_8705) {
                    System.out.println("[COBWEB] Water not collected, will return later. Current item: " + currentItem.method_7909());
                    state.needsToCollectWater = true;
                } else {
                    System.out.println("[COBWEB] Water collected successfully!");
                    state.needsToCollectWater = false;
                    state.waterPosition = null;
                }
            }
            
            if (state.cobwebEscapeTicks >= 10) {
                System.out.println("[COBWEB] Tick 10 - Finishing escape process...");
                executeCommand(server, bot, "player " + bot.method_5477().getString() + " stop");
                state.isEscapingCobweb = false;
                state.cobwebEscapeTicks = 0;
                state.isInCobweb = false;
                

                if (!state.needsToCollectWater) {
                    state.cobwebEscapeSlot = -1;
                }
            }
            return;
        }
        
        state.isInCobweb = inCobweb;
        
        if (!inCobweb) {
            return;
        }
        
        int waterSlot = findWaterBucket(bot.method_31548());
        int pearlSlot = findEnderPearl(bot.method_31548());
        
        if (waterSlot < 0 && pearlSlot < 0) {
            System.out.println("[COBWEB] No water bucket or ender pearl found! Bot can attack normally.");
            return;
        }
        

        if (waterSlot >= 0) {

            if (waterSlot >= 9) {
                class_1799 water = bot.method_31548().method_5438(waterSlot);
                class_1799 current = bot.method_31548().method_5438(8);
                bot.method_31548().method_5447(waterSlot, current);
                bot.method_31548().method_5447(8, water);
                waterSlot = 8;
            }
            
            System.out.println("[COBWEB] Starting escape process - placing water...");
            
            state.cobwebEscapeSlot = waterSlot;
            state.isEscapingCobweb = true;
            state.cobwebEscapeTicks = 0;
            state.waterPosition = bot.method_24515().method_10074();
            state.needsToCollectWater = false;
            

            executeCommand(server, bot, "player " + bot.method_5477().getString() + " stop");
            bot.method_18800(0, bot.method_18798().field_1351, 0);
            

            org.stepan1411.pvp_bot.utils.InventoryHelper.setSelectedSlot(bot.method_31548(), waterSlot);
            bot.method_36457(90.0f);
            

            System.out.println("[COBWEB] Placing water at position: " + state.waterPosition);
            executeCommand(server, bot, "player " + bot.method_5477().getString() + " use once");
        } else if (pearlSlot >= 0) {

            if (pearlSlot >= 9) {
                class_1799 pearl = bot.method_31548().method_5438(pearlSlot);
                class_1799 current = bot.method_31548().method_5438(8);
                bot.method_31548().method_5447(pearlSlot, current);
                bot.method_31548().method_5447(8, pearl);
                pearlSlot = 8;
            }
            
            System.out.println("[COBWEB] Using ender pearl to escape...");
            

            org.stepan1411.pvp_bot.utils.InventoryHelper.setSelectedSlot(bot.method_31548(), pearlSlot);
            

            bot.method_36457(0.0f);
            
            executeCommand(server, bot, "player " + bot.method_5477().getString() + " use once");
            

            state.isInCobweb = false;
        }
    }
    
    
    private static int findWaterBucket(net.minecraft.class_1661 inventory) {
        for (int i = 0; i < 36; i++) {
            class_1799 stack = inventory.method_5438(i);
            if (stack.method_7909() == class_1802.field_8705) {
                return i;
            }
        }
        return -1;
    }
    
    private static int findEnderPearl(net.minecraft.class_1661 inventory) {
        for (int i = 0; i < 36; i++) {
            class_1799 stack = inventory.method_5438(i);
            if (stack.method_7909() == class_1802.field_8634) {
                return i;
            }
        }
        return -1;
    }
    
    
    public static boolean canAttack(class_3222 bot, BotState state) {

        if (state.isEscapingCobweb || state.needsToCollectWater) {
            return false;
        }
        

        if (state.isInCobweb) {
            int waterSlot = findWaterBucket(bot.method_31548());
            int pearlSlot = findEnderPearl(bot.method_31548());
            
            if (waterSlot < 0 && pearlSlot < 0) {
                System.out.println("[COBWEB] " + bot.method_5477().getString() + " in cobweb but no water/pearl - can attack");
                return true;
            }
            System.out.println("[COBWEB] " + bot.method_5477().getString() + " in cobweb with escape items - cannot attack");
            return false;
        }
        
        return true;
    }
}

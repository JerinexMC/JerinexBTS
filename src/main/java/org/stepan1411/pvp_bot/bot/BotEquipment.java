package org.stepan1411.pvp_bot.bot;

import net.minecraft.class_1304;
import net.minecraft.class_1542;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_3222;
import net.minecraft.item.*;
import java.util.ArrayList;
import java.util.List;

public class BotEquipment {

    public static void autoEquip(class_3222 bot) {

        var utilsState = BotUtils.getState(bot.method_5477().getString());
        if (utilsState.isEating) {
            return;
        }
        
        BotSettings settings = BotSettings.get();
        
        if (settings.isAutoEquipArmor()) {
            equipBestArmor(bot);
        }
        if (settings.isAutoEquipWeapon()) {
            equipBestWeapon(bot);
        }
    }

    private static void equipBestArmor(class_3222 bot) {
        equipBestForSlot(bot, class_1304.field_6169);
        equipBestForSlot(bot, class_1304.field_6174);
        equipBestForSlot(bot, class_1304.field_6172);
        equipBestForSlot(bot, class_1304.field_6166);
    }

    private static void equipBestForSlot(class_3222 bot, class_1304 slot) {
        BotSettings settings = BotSettings.get();
        var inventory = bot.method_31548();
        

        class_1799 currentArmor = bot.method_6118(slot);
        double currentValue = getArmorValue(currentArmor, slot);
        
        int bestInvSlot = -1;
        double bestValue = currentValue;
        

        for (int i = 0; i < inventory.method_5439(); i++) {
            class_1799 stack = inventory.method_5438(i);
            if (stack.method_7960()) continue;
            if (!isArmorForSlot(stack.method_7909(), slot)) continue;
            
            double value = getArmorValue(stack, slot);
            if (value > bestValue) {
                bestValue = value;
                bestInvSlot = i;
            }
        }
        

        if (bestInvSlot >= 0) {
            class_1799 newArmor = inventory.method_5438(bestInvSlot).method_7972();
            class_1799 oldArmor = currentArmor.method_7972();
            

            bot.method_5673(slot, newArmor);
            inventory.method_5447(bestInvSlot, class_1799.field_8037);
            

            if (!oldArmor.method_7960()) {
                inventory.method_5447(bestInvSlot, oldArmor);
            }
            
            currentArmor = newArmor;
            currentValue = bestValue;
        }

        

        if (settings.isDropWorseArmor()) {
            for (int i = 0; i < inventory.method_5439(); i++) {
                class_1799 stack = inventory.method_5438(i);
                if (stack.method_7960()) continue;
                if (!isArmorForSlot(stack.method_7909(), slot)) continue;
                
                double value = getArmorValue(stack, slot);
                

                if (value < currentValue) {
                    dropItemBackward(bot, stack);
                    inventory.method_5447(i, class_1799.field_8037);
                }
            }
        }
    }

    
    private static void dropItemBackward(class_3222 bot, class_1799 stack) {
        if (stack.method_7960()) return;
        
        BotSettings settings = BotSettings.get();
        double distance = settings.getDropDistance();
        

        float oldYaw = bot.method_36454();
        float oldHeadYaw = bot.method_5791();
        

        float turnAngle = 90 + (float)(Math.random() * 180);
        float newYaw = oldYaw + turnAngle;
        
        bot.method_36456(newYaw);
        bot.method_5847(newYaw);
        

        class_1542 dropped = bot.method_7329(stack.method_7972(), false, true);
        
        if (dropped != null) {

            double yawRad = Math.toRadians(newYaw);
            double speed = 0.3 * distance;
            dropped.method_18800(
                -Math.sin(yawRad) * speed,
                0.2,
                Math.cos(yawRad) * speed
            );
            dropped.method_6982(60);
        }
        

        bot.method_36456(oldYaw);
        bot.method_5847(oldHeadYaw);
    }

    private static boolean isArmorForSlot(class_1792 item, class_1304 slot) {
        return switch (slot) {
            case field_6169 -> item == class_1802.field_22027 || item == class_1802.field_8805 ||
                         item == class_1802.field_8743 || item == class_1802.field_8283 ||
                         item == class_1802.field_8862 || item == class_1802.field_8267 ||
                         item == class_1802.field_8090;
            case field_6174 -> item == class_1802.field_22028 || item == class_1802.field_8058 ||
                          item == class_1802.field_8523 || item == class_1802.field_8873 ||
                          item == class_1802.field_8678 || item == class_1802.field_8577 ||
                          item == class_1802.field_8833;
            case field_6172 -> item == class_1802.field_22029 || item == class_1802.field_8348 ||
                         item == class_1802.field_8396 || item == class_1802.field_8218 ||
                         item == class_1802.field_8416 || item == class_1802.field_8570;
            case field_6166 -> item == class_1802.field_22030 || item == class_1802.field_8285 ||
                         item == class_1802.field_8660 || item == class_1802.field_8313 ||
                         item == class_1802.field_8753 || item == class_1802.field_8370;
            default -> false;
        };
    }

    private static double getArmorValue(class_1799 stack, class_1304 slot) {
        if (stack.method_7960()) return 0;
        class_1792 item = stack.method_7909();
        
        if (item == class_1802.field_22027 || item == class_1802.field_22028 ||
            item == class_1802.field_22029 || item == class_1802.field_22030) return 100;
        if (item == class_1802.field_8805 || item == class_1802.field_8058 ||
            item == class_1802.field_8348 || item == class_1802.field_8285) return 80;
        if (item == class_1802.field_8743 || item == class_1802.field_8523 ||
            item == class_1802.field_8396 || item == class_1802.field_8660) return 60;
        if (item == class_1802.field_8283 || item == class_1802.field_8873 ||
            item == class_1802.field_8218 || item == class_1802.field_8313) return 50;
        if (item == class_1802.field_8862 || item == class_1802.field_8678 ||
            item == class_1802.field_8416 || item == class_1802.field_8753) return 40;
        if (item == class_1802.field_8267 || item == class_1802.field_8577 ||
            item == class_1802.field_8570 || item == class_1802.field_8370) return 20;
        if (item == class_1802.field_8090) return 55;
        if (item == class_1802.field_8833) return 10;
        
        return 0;
    }


    private static void equipBestWeapon(class_3222 bot) {
        BotSettings settings = BotSettings.get();
        var inventory = bot.method_31548();
        

        int bestSlotIndex = -1;
        double bestScore = 0;
        
        for (int i = 0; i < 36; i++) {
            class_1799 stack = inventory.method_5438(i);
            if (stack.method_7960()) continue;
            
            double score = getWeaponScore(stack, settings.isPreferSword());
            if (score > bestScore) {
                bestScore = score;
                bestSlotIndex = i;
            }
        }
        

        if (bestSlotIndex >= 0) {
            if (bestSlotIndex >= 9) {

                class_1799 weapon = inventory.method_5438(bestSlotIndex);
                class_1799 slot0 = inventory.method_5438(0);
                inventory.method_5447(bestSlotIndex, slot0);
                inventory.method_5447(0, weapon);
                bestSlotIndex = 0;
            }
            org.stepan1411.pvp_bot.utils.InventoryHelper.setSelectedSlot(inventory, bestSlotIndex);
        }
        

        if (settings.isDropWorseWeapons() && bestScore > 0) {
            for (int i = 0; i < 36; i++) {
                if (i == bestSlotIndex) continue;
                
                class_1799 stack = inventory.method_5438(i);
                if (stack.method_7960()) continue;
                
                double score = getWeaponScore(stack, settings.isPreferSword());
                if (score > 0 && score < bestScore) {
                    dropItemBackward(bot, stack);
                    inventory.method_5447(i, class_1799.field_8037);
                }
            }
        }
    }

    
    private static double getWeaponScore(class_1799 stack, boolean preferSword) {
        if (stack.method_7960()) return 0;
        class_1792 item = stack.method_7909();
        
        double baseDamage = getWeaponDamage(stack);
        if (baseDamage == 0) return 0;
        

        if (preferSword && isSword(item)) {
            return baseDamage + 5;
        }
        
        return baseDamage;
    }
    
    
    private static boolean isSword(class_1792 item) {
        return item == class_1802.field_22022 || 
               item == class_1802.field_8802 || 
               item == class_1802.field_8371 || 
               item == class_1802.field_8845 || 
               item == class_1802.field_8528 || 
               item == class_1802.field_8091;
    }

    private static double getWeaponDamage(class_1799 stack) {
        if (stack.method_7960()) return 0;
        class_1792 item = stack.method_7909();
        
        if (item == class_1802.field_22022) return 8;
        if (item == class_1802.field_22025) return 10;
        if (item == class_1802.field_8802) return 7;
        if (item == class_1802.field_8556) return 9;
        if (item == class_1802.field_8371) return 6;
        if (item == class_1802.field_8475) return 9;
        if (item == class_1802.field_8528) return 5;
        if (item == class_1802.field_8062) return 9;
        if (item == class_1802.field_8845) return 4;
        if (item == class_1802.field_8825) return 7;
        if (item == class_1802.field_8091) return 4;
        if (item == class_1802.field_8406) return 7;
        if (item == class_1802.field_8547) return 9;
        if (item == class_1802.field_49814) return 6;
        
        return 0;
    }
}

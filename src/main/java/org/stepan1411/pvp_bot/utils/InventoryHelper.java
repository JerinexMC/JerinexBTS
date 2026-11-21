package org.stepan1411.pvp_bot.utils;
import net.minecraft.entity.player.PlayerInventory;
public class InventoryHelper { public static void setSelectedSlot(PlayerInventory inv, int slot) { inv.selectedSlot = slot; } public static int getSelectedSlot(PlayerInventory inv) { return inv.selectedSlot; } }

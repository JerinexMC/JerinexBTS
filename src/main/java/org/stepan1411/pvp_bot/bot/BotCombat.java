package org.stepan1411.pvp_bot.bot;

import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.*;

public class BotCombat {

    private static final Map<String, CombatState> combatStates = new HashMap<>();

    public static class CombatState {
        public Entity target = null;
        public String forcedTargetName = null;
        public int attackCooldown = 0;
        public int bowDrawTicks = 0;
        public boolean isDrawingBow = false;
        public Entity lastAttacker = null;
        public long lastAttackTime = 0;
        public WeaponMode currentMode = WeaponMode.MELEE;
        public float lastHealth = 20.0f;
        public boolean isRetreating = false;

        public boolean isChargingSpear = false;
        public int spearChargeTicks = 0;

        public int cobwebCooldown = 0;
        public int cobwebSavedSlot = -1;
        public ItemStack cobwebSavedSlotItem = ItemStack.EMPTY;

        public boolean isUsingShield = false;
        public int shieldToggleCooldown = 0;
        public int airTimeTicks = 0;
        public boolean shieldBroken = false;
        public long shieldBrokenTime = 0;
        public boolean isPlacingCobweb = false;
        public int cobwebPlaceTicks = 0;
        public BlockPos cobwebTargetPos = null;

        public int stunSwingDelayTicks = 0;
        public Entity stunTarget = null;

        public boolean pendingSwordShieldCounter = false;
        public Entity swordCounterTarget = null;

        public boolean isCrystalPvping = false;
        public int crystalCooldown = 0;
        public BlockPos lastObsidianPos = null;
        public int crystalPvpStep = 0;
        public int crystalPvpTicks = 0;

        public boolean isMaceDefending = false;
        public int maceDefenseCooldown = 0;

        public int enemyCooldown = 0;
        public double lastEnemyDist = 99;
        public int shieldFlickerTicks = 0;
        public int shieldPredictTicks = 0;
        public int shieldHoldTicks = 0;
        public int shieldHitTicks = 0;
        public int critFallTicks = 0;
        public int strafeCooldown = 0;
        public int strafeDir = 1;

        public enum WeaponMode {
            MELEE,
            RANGED,
            MACE,
            SPEAR,
            CRYSTAL,
            ANCHOR
        }
    }

    public static class ElytraMaceState {
        public String targetName;
        public int phase = 0;
        public int tickCounter = 0;
        public int totalTicks = 0;
        public double phaseStartY = 0;
        public ItemStack savedElytra = ItemStack.EMPTY;
        public int windBurstDelay = 0;
    }

    private static final Map<String, ElytraMaceState> elytraMaceStates = new HashMap<>();
    private static final Map<String, ItemStack> pendingElytraRestore = new HashMap<>();

    public static boolean isElytraMaceActive(String botName) {
        return elytraMaceStates.containsKey(botName);
    }

    public static void startElytraMace(String botName, String targetName) {
        ElytraMaceState state = new ElytraMaceState();
        state.targetName = targetName;
        state.phase = 0;
        state.tickCounter = 0;
        elytraMaceStates.put(botName, state);
    }

    public static void stopElytraMace(String botName) {
        elytraMaceStates.remove(botName);
    }

    public static class WindBurstState {
        public String targetName;
        public int phase = 0;
        public int tickCounter = 0;
    }

    private static final Map<String, WindBurstState> windBurstStates = new HashMap<>();

    public static boolean isWindBurstActive(String botName) {
        return windBurstStates.containsKey(botName);
    }

    public static void startWindBurst(String botName, String targetName) {
        WindBurstState state = new WindBurstState();
        state.targetName = targetName;
        state.phase = 0;
        state.tickCounter = 0;
        windBurstStates.put(botName, state);
    }

    private static boolean hasWindBurstMace(ServerPlayerEntity bot) {
        for (int i = 0; i < 36; i++) {
            ItemStack stack = bot.getInventory().getStack(i);
            if (stack.getItem() == Items.MACE) {
                var enchantments = stack.get(DataComponentTypes.ENCHANTMENTS);
                if (enchantments != null) {
                    for (var entry : enchantments.getEnchantments()) {
                        String id = entry.getIdAsString().toLowerCase();
                        if (id.contains("wind_burst")) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public static CombatState getState(String botName) {
        return combatStates.computeIfAbsent(botName, k -> new CombatState());
    }

    public static void removeState(String botName) {
        combatStates.remove(botName);
    }

    public static void update(ServerPlayerEntity bot, net.minecraft.server.MinecraftServer server) {
        String botName = bot.getName().getString();
        CombatState state = getState(botName);
        BotSettings settings = BotSettings.get();

        if (state.stunSwingDelayTicks > 0) {
            state.stunSwingDelayTicks--;
            if (state.stunSwingDelayTicks == 0 && state.stunTarget != null && state.stunTarget.isAlive()) {
                lookAtTarget(bot, state.stunTarget);
                attackWithCarpet(bot, state.stunTarget, server);
                state.stunTarget = null;
            }
        }

        if (state.pendingSwordShieldCounter) {
            Entity counterTarget = state.swordCounterTarget;
            state.pendingSwordShieldCounter = false;
            state.swordCounterTarget = null;
            if (counterTarget != null && counterTarget.isAlive() && bot.distanceTo(counterTarget) <= settings.getMeleeRange() + 1.5) {
                dropShieldImmediately(bot, state, server);
                equipBestMeleeHotbarSlot(bot, state, settings);
                lookAtTarget(bot, counterTarget);
                attackWithCarpet(bot, counterTarget, server);
            }
        }

        if (pendingElytraRestore.containsKey(botName)) {
            if (bot.isOnGround() || bot.getHealth() <= 0) {
                ItemStack elytra = pendingElytraRestore.remove(botName);
                if (!elytra.isEmpty()) {
                    var inv = bot.getInventory();
                    boolean placed = false;
                    if (inv.getStack(38).isEmpty()) {
                        inv.setStack(38, elytra.copy());
                        placed = true;
                    }
                    if (!placed) {
                        for (int i = 0; i < 41; i++) {
                            if (inv.getStack(i).isEmpty()) {
                                inv.setStack(i, elytra.copy());
                                placed = true;
                                break;
                            }
                        }
                    }
                    if (!placed) {
                        bot.dropItem(elytra.copy(), true, true);
                    }
                }
                if (bot.isAlive() && !windBurstStates.containsKey(botName)) {
                    CombatState st = getState(botName);
                    if (st.forcedTargetName != null) {
                        Entity t = server.getPlayerManager().getPlayer(st.forcedTargetName);
                        if (t != null && t.isAlive() && hasElytraMaceItems(bot)) {
                            startElytraMace(botName, st.forcedTargetName);
                        }
                    }
                }
            }
        }

        if (windBurstStates.containsKey(botName)) {
            handleWindBurst(bot, server);
            return;
        }

        if (!elytraMaceStates.containsKey(botName) && !pendingElytraRestore.containsKey(botName) && !windBurstStates.containsKey(botName)) {
            CombatState st = getState(botName);
            String targetName = null;
            if (st.lastAttacker instanceof PlayerEntity && st.lastAttacker.isAlive() && System.currentTimeMillis() - st.lastAttackTime < 2000) {
                targetName = st.lastAttacker.getName().getString();
            } else if (st.forcedTargetName != null) {
                Entity forced = server.getPlayerManager().getPlayer(st.forcedTargetName);
                if (forced != null && forced.isAlive()) {
                    targetName = st.forcedTargetName;
                }
            }
            if (targetName != null && hasElytraMaceItems(bot)) {
                startElytraMace(botName, targetName);
            }
        }

        if (elytraMaceStates.containsKey(botName)) {
            handleElytraMace(bot, server);
            return;
        }

        if (!settings.isCombatEnabled()) return;

        float currentHealth = bot.getHealth();
        if (currentHealth < state.lastHealth && settings.isRevengeEnabled()) {
            Entity attacker = bot.getAttacker();
            if (attacker != null && attacker != bot && attacker instanceof LivingEntity) {
                state.lastAttacker = attacker;
                state.lastAttackTime = System.currentTimeMillis();
            }
        }
        state.lastHealth = currentHealth;

        if (state.attackCooldown > 0) state.attackCooldown--;
        if (state.shieldToggleCooldown > 0) state.shieldToggleCooldown--;
        if (state.cobwebCooldown > 0) state.cobwebCooldown--;
        if (state.crystalCooldown > 0) state.crystalCooldown--;

        Entity target = findTarget(bot, state, settings, server);
        state.target = target;

        if (state.isPlacingCobweb) {
            if (target != null && target.isAlive()) {
                handleCobwebPlacement(bot, target, state, server);
                return;
            } else {
                finishCobwebPlacement(bot, state, false);
            }
        }

        if (target == null) {
            if (state.isDrawingBow) {
                stopUsingBow(bot, state);
            }
            BotNavigation.idleWander(bot);
            return;
        }

        BotNavigation.resetIdle(bot.getName().getString());

        double distance = bot.distanceTo(target);
        float health = bot.getHealth();
        float maxHealth = bot.getMaxHealth();
        float healthPercent = health / maxHealth;
        boolean lowHealth = healthPercent <= settings.getRetreatHealthPercent();
        boolean criticalHealth = healthPercent <= settings.getCriticalHealthPercent();
        boolean hasFood = BotUtils.hasFood(bot);

        if (state.shieldBroken && System.currentTimeMillis() - state.shieldBrokenTime > 5000) {
            state.shieldBroken = false;
        }

        var utilsState = BotUtils.getState(bot.getName().getString());

        if (utilsState.isEscapingCobweb || utilsState.needsToCollectWater) {
            return;
        }

        boolean isEating = utilsState.isEating;

        if (isEating && settings.isRetreatEnabled()) {
            state.isRetreating = true;
            if (state.isDrawingBow) {
                stopUsingBow(bot, state);
            }
            BotNavigation.lookAway(bot, target);
            BotNavigation.moveAway(bot, target, 1.2);
            return;
        } else if (isEating && state.isDrawingBow) {
            stopUsingBow(bot, state);
        }

        if (lowHealth && settings.isAutoPotionEnabled()) {
            if (BotUtils.tryUseHealingPotion(bot, server)) {
                return;
            }
        }

        boolean shouldRetreat = settings.isRetreatEnabled() && hasFood &&
                               (criticalHealth || (lowHealth && !state.shieldBroken));

        if (shouldRetreat) {
            state.isRetreating = true;

            if (settings.isAutoShieldEnabled()) {
                var inventory = bot.getInventory();

                if (!utilsState.isEating && !utilsState.isBlocking) {
                    if (BotUtils.shouldUseMainHandShield(bot)) {
                        BotUtils.equipShieldToMainHand(bot);
                    } else {
                        ItemStack offhandItem = bot.getOffHandStack();
                        if (offhandItem.isEmpty() || !offhandItem.getItem().toString().contains("shield")) {
                            int shieldSlot = findShield(inventory);
                            if (shieldSlot >= 0) {
                                ItemStack shield = inventory.getStack(shieldSlot);
                                inventory.setStack(40, shield);
                                inventory.setStack(shieldSlot, ItemStack.EMPTY);
                            }
                        }
                    }
                }

                if (!state.isUsingShield && state.shieldToggleCooldown <= 0) {
                    if (!utilsState.isEating && !utilsState.isBlocking) {
                        startUsingShield(bot, server);
                        state.isUsingShield = true;
                    }
                }
            }

            if (distance < 25.0) {
                if (settings.isCobwebEnabled() && distance < 7.5 && state.cobwebCooldown <= 0 && !state.isPlacingCobweb) {
                    tryPlaceCobweb(bot, target, state, server);
                }
                BotNavigation.lookAway(bot, target);
                BotNavigation.moveAway(bot, target, 1.5);
            }
            return;
        }

        boolean shouldRangedRetreat = settings.isRetreatEnabled() && !hasFood &&
                                      healthPercent < 0.5f && hasRangedWeaponInInventory(bot) &&
                                      settings.isRangedEnabled();

        if (shouldRangedRetreat) {
            var inv = bot.getInventory();
            int rangedSlot = findRangedWeapon(inv);
            if (rangedSlot >= 0 && hasArrows(inv)) {
                state.isRetreating = true;
                double safeDist = settings.getRangedOptimalRange();
                if (distance < safeDist - 5) {
                    if (state.isDrawingBow) {
                        stopUsingBow(bot, state);
                    }
                    BotNavigation.lookAway(bot, target);
                    BotNavigation.moveAway(bot, target, 1.3);
                    return;
                }
                state.currentMode = CombatState.WeaponMode.RANGED;
            } else {
                state.isRetreating = false;
            }
        } else {
            state.isRetreating = false;
        }

        if (utilsState.isMending) {
            return;
        }

        selectWeaponMode(bot, state, distance, settings);

        if (!utilsState.isThrowingPotion) {
            BotNavigation.lookAt(bot, target);
        }

        double maxRange = switch (state.currentMode) {
            case MELEE -> settings.getMeleeRange() * 2;
            case RANGED -> settings.getRangedMaxRange();
            case MACE -> settings.getMaceRange() * 2;
            case SPEAR -> settings.getSpearChargeRange();
            case CRYSTAL, ANCHOR -> 10.0;
        };

        if (distance > maxRange) {
            BotNavigation.moveToward(bot, target, settings.getMoveSpeed());
            return;
        }

        handleMaceDefense(bot, target, settings, server);

        switch (state.currentMode) {
            case MELEE -> handleMeleeCombat(bot, target, state, distance, settings, server);
            case RANGED -> handleRangedCombat(bot, target, state, distance, settings, server);
            case MACE -> handleMaceCombat(bot, target, state, distance, settings, server);
            case SPEAR -> handleSpearCombat(bot, target, state, distance, settings, server);
            case CRYSTAL -> {
                boolean handled = BotCrystalPvp.doCrystalPvp(bot, target, settings, server);
                if (!handled) {
                    state.currentMode = CombatState.WeaponMode.MELEE;
                    handleMeleeCombat(bot, target, state, distance, settings, server);
                }
            }
            case ANCHOR -> {
                boolean handled = BotAnchorPvp.doAnchorPvp(bot, target, settings, server);
                if (!handled) {
                    state.currentMode = CombatState.WeaponMode.MELEE;
                    handleMeleeCombat(bot, target, state, distance, settings, server);
                }
            }
        }
    }

    private static void dropShieldImmediately(ServerPlayerEntity bot, CombatState state, net.minecraft.server.MinecraftServer server) {
        stopUsingShield(bot, server);
        bot.clearActiveItem();
        state.isUsingShield = false;
        state.shieldPredictTicks = 0;
        state.shieldHoldTicks = 0;
        state.shieldFlickerTicks = 0;
        state.isMaceDefending = false;
        state.maceDefenseCooldown = 0;
        state.shieldToggleCooldown = 4;
        if (BotUtils.shouldUseMainHandShield(bot)) {
            BotUtils.unequipShieldFromMainHand(bot);
        }
    }

    private static void equipBestMeleeHotbarSlot(ServerPlayerEntity bot, CombatState state, BotSettings settings) {
        var inventory = bot.getInventory();
        int weaponSlot = findMeleeWeapon(inventory);
        if (weaponSlot >= 0) {
            if (weaponSlot >= 9) {
                ItemStack weapon = inventory.getStack(weaponSlot);
                ItemStack slot0 = inventory.getStack(0);
                inventory.setStack(weaponSlot, slot0);
                inventory.setStack(0, weapon);
                weaponSlot = 0;
            }
            org.stepan1411.pvp_bot.utils.InventoryHelper.setSelectedSlot(inventory, weaponSlot);
        }
    }

    private static boolean isSwordStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Item item = stack.getItem();
        return isSword(item) || item.toString().toLowerCase(Locale.ROOT).contains("sword");
    }

    private static Entity findTarget(ServerPlayerEntity bot, CombatState state, BotSettings settings, net.minecraft.server.MinecraftServer server) {
        if (state.forcedTargetName != null) {
            Entity forced = findEntityByName(bot, state.forcedTargetName, server);
            if (forced != null && forced.isAlive()) {
                if (forced instanceof PlayerEntity player && !settings.isAttackInvincible() && (player.isSpectator() || player.isCreative() || player.isInvulnerable())) {
                    state.forcedTargetName = null;
                } else if (bot.distanceTo(forced) <= settings.getMaxTargetDistance()) {
                    return forced;
                }
            }
        }

        if (settings.isRevengeEnabled() && state.lastAttacker != null) {
            if (!state.lastAttacker.isRemoved() && state.lastAttacker.isAlive()) {
                if (state.lastAttacker instanceof PlayerEntity lastPlayer) {
                    if (!settings.isAttackInvincible() && (lastPlayer.isSpectator() || lastPlayer.isCreative() || lastPlayer.isInvulnerable())) {
                        state.lastAttacker = null;
                    } else if (!settings.isFriendlyFireEnabled()) {
                        String attackerName = lastPlayer.getName().getString();
                        if (BotFaction.areAllies(bot.getName().getString(), attackerName)) {
                            state.lastAttacker = null;
                        }
                    }
                }

                if (state.lastAttacker != null) {
                    double dist = bot.distanceTo(state.lastAttacker);
                    if (dist <= settings.getMaxTargetDistance()) {
                        if (dist <= 10.0) {
                            state.lastAttackTime = System.currentTimeMillis();
                        }
                        return state.lastAttacker;
                    }
                }
            }

            if (state.lastAttacker == null || state.lastAttacker.isRemoved() || !state.lastAttacker.isAlive() ||
                System.currentTimeMillis() - state.lastAttackTime >= 30000) {
                state.lastAttacker = null;
            }
        }

        if (settings.isFactionsEnabled()) {
            Entity factionEnemy = findFactionEnemy(bot, settings, server);
            if (factionEnemy != null) {
                return factionEnemy;
            }
        }

        if (settings.isAutoTargetEnabled()) {
            return findNearestEnemy(bot, settings, server);
        }

        return null;
    }

    private static Entity findFactionEnemy(ServerPlayerEntity bot, BotSettings settings, net.minecraft.server.MinecraftServer server) {
        String botName = bot.getName().getString();
        String botFaction = BotFaction.getFaction(botName);
        if (botFaction == null) return null;

        double maxDist = settings.getMaxTargetDistance();
        Entity nearest = null;
        double nearestDist = maxDist + 1;

        if (server != null) {
            for (var player : server.getPlayerManager().getPlayerList()) {
                if (player == bot || !player.isAlive()) continue;
                if (!settings.isAttackInvincible() && (player.isSpectator() || player.isCreative() || player.isInvulnerable())) continue;

                String targetName = player.getName().getString();
                if (BotFaction.areEnemies(botName, targetName)) {
                    double dist = bot.distanceTo(player);
                    if (dist < nearestDist && dist <= maxDist) {
                        nearestDist = dist;
                        nearest = player;
                    }
                }
            }
        }
        return nearest;
    }

    private static Entity findEntityByName(ServerPlayerEntity bot, String name, net.minecraft.server.MinecraftServer server) {
        if (server != null) {
            var player = server.getPlayerManager().getPlayer(name);
            if (player != null && player != bot) return player;
            for (var world : server.getWorlds()) {
                for (Entity entity : world.iterateEntities()) {
                    if (entity.getName().getString().equalsIgnoreCase(name) && entity != bot) {
                        return entity;
                    }
                }
            }
        }
        return null;
    }

    private static Entity findNearestEnemy(ServerPlayerEntity bot, BotSettings settings, net.minecraft.server.MinecraftServer server) {
        double maxDist = settings.getMaxTargetDistance();
        Box searchBox = bot.getBoundingBox().expand(maxDist);
        Entity nearest = null;
        double nearestDist = maxDist + 1;

        if (server != null) {
            for (var world : server.getWorlds()) {
                for (Entity entity : world.getOtherEntities(bot, searchBox)) {
                    if (!isValidTarget(bot, entity, settings)) continue;
                    double dist = bot.distanceTo(entity);
                    if (dist < nearestDist) {
                        nearestDist = dist;
                        nearest = entity;
                    }
                }
            }
        }
        return nearest;
    }

    private static boolean isValidTarget(ServerPlayerEntity bot, Entity entity, BotSettings settings) {
        if (entity == bot || !entity.isAlive() || !(entity instanceof LivingEntity living)) return false;
        String botName = bot.getName().getString();

        if (entity instanceof PlayerEntity player) {
            if (!settings.isAttackInvincible() && (player.isSpectator() || player.isCreative() || player.isInvulnerable())) return false;
            String targetName = player.getName().getString();
            if (settings.isFactionsEnabled()) {
                if (!settings.isFriendlyFireEnabled() && BotFaction.areAllies(botName, targetName)) return false;
                if (BotFaction.areEnemies(botName, targetName)) return true;
            }
            if (BotManager.getAllBots().contains(targetName)) {
                return settings.isTargetOtherBots();
            }
            return settings.isTargetPlayers();
        }

        return entity instanceof HostileEntity || living instanceof net.minecraft.entity.mob.MobEntity
            ? settings.isTargetHostileMobs() : false;
    }

    private static void selectWeaponMode(ServerPlayerEntity bot, CombatState state, double distance, BotSettings settings) {
        if (state.isRetreating) return;
        if (state.isDrawingBow && settings.isRangedRetreatOnClose()) return;
        var inventory = bot.getInventory();
        Entity target = state.target;

        boolean hasMelee = findMeleeWeapon(inventory) >= 0;
        boolean hasRanged = findRangedWeapon(inventory) >= 0;
        boolean hasMace = findMace(inventory) >= 0;
        boolean hasSpear = findSpear(inventory) >= 0;

        double meleeRange = settings.getMeleeRange();
        double rangedMinRange = settings.getRangedMinRange();
        double maceRange = settings.getMaceRange();
        double spearRange = settings.getSpearRange();

        if (target != null && BotCrystalPvp.canUseCrystalPvp(bot, target, settings)) {
            state.currentMode = CombatState.WeaponMode.CRYSTAL;
        } else if (target != null && BotAnchorPvp.canUseAnchorPvp(bot, target, settings)) {
            state.currentMode = CombatState.WeaponMode.ANCHOR;
        } else if (hasMace && distance <= maceRange && settings.isMaceEnabled()) {
            state.currentMode = CombatState.WeaponMode.MACE;
        } else if (hasSpear && distance <= spearRange && settings.isSpearEnabled()) {
            state.currentMode = CombatState.WeaponMode.SPEAR;
        } else if (!settings.isRangedRetreatOnClose() && hasMelee && distance <= meleeRange * 2) {
            state.currentMode = CombatState.WeaponMode.MELEE;
        } else if (hasRanged && distance > rangedMinRange && settings.isRangedEnabled()) {
            state.currentMode = CombatState.WeaponMode.RANGED;
        } else if (hasSpear && settings.isSpearEnabled()) {
            state.currentMode = CombatState.WeaponMode.SPEAR;
        } else if (hasRanged && settings.isRangedEnabled()) {
            state.currentMode = CombatState.WeaponMode.RANGED;
        } else {
            state.currentMode = CombatState.WeaponMode.MELEE;
        }
    }

    private static boolean isTargetMaceAttacking(Entity target) {
        if (!(target instanceof PlayerEntity player)) return false;
        if (player.getMainHandStack().getItem() != Items.MACE) return false;
        if (player.isOnGround()) return false;
        return player.getVelocity().y < -0.08;
    }

    private static void handleMaceDefense(ServerPlayerEntity bot, Entity target, BotSettings settings, net.minecraft.server.MinecraftServer server) {
        if (!settings.isShieldMace()) return;

        var inventory = bot.getInventory();
        var utilsState = BotUtils.getState(bot.getName().getString());
        var combatState = getState(bot.getName().getString());

        if (combatState.maceDefenseCooldown > 0) combatState.maceDefenseCooldown--;

        if (isTargetMaceAttacking(target)) {
            combatState.maceDefenseCooldown = 20;
            combatState.isMaceDefending = true;
        }

        boolean shouldDefend = combatState.isMaceDefending && combatState.maceDefenseCooldown > 0;
        if (!shouldDefend) {
            if (combatState.isUsingShield && combatState.isMaceDefending) {
                try {
                    server.getCommandManager().getDispatcher().execute("player " + bot.getName().getString() + " stop", server.getCommandSource());
                } catch (Exception e) {
                    bot.clearActiveItem();
                }
                if (BotUtils.shouldUseMainHandShield(bot)) {
                    BotUtils.unequipShieldFromMainHand(bot);
                }
                combatState.isUsingShield = false;
                combatState.isMaceDefending = false;
            }
            return;
        }

        if (!utilsState.isEating) {
            if (BotUtils.shouldUseMainHandShield(bot)) {
                BotUtils.equipShieldToMainHand(bot);
            } else {
                int shieldSlot = findShield(inventory);
                if (shieldSlot < 0) return;
                if (shieldSlot != 40) {
                    ItemStack shield = inventory.getStack(shieldSlot);
                    ItemStack current = inventory.getStack(40);
                    inventory.setStack(shieldSlot, current);
                    inventory.setStack(40, shield);
                }
            }
        }

        if (!combatState.isUsingShield) {
            try {
                server.getCommandManager().getDispatcher().execute("player " + bot.getName().getString() + " use continuous", server.getCommandSource());
            } catch (Exception e) {
                bot.setCurrentHand(Hand.OFF_HAND);
            }
            combatState.isUsingShield = true;
        }
    }

    private static void handleMeleeCombat(ServerPlayerEntity bot, Entity target, CombatState state, double distance, BotSettings settings, net.minecraft.server.MinecraftServer server) {
        var utilsState = BotUtils.getState(bot.getName().getString());

        if (state.isUsingShield && !bot.isBlocking()) {
            state.isUsingShield = false;
        }

        if (state.isMaceDefending && state.maceDefenseCooldown > 0) {
            lookAtTarget(bot, target);
            return;
        }

        if (utilsState.isEating) return;

        var inventory = bot.getInventory();
        if (state.isDrawingBow) stopUsingBow(bot, state);

        double meleeRange = settings.getMeleeRange();

        if (settings.isCobwebEnabled() && distance <= 5.5 && state.cobwebCooldown <= 0 && !state.isPlacingCobweb) {
            if (target instanceof LivingEntity living) {
                Vec3d targetVel = living.getVelocity();
                double targetSpeed = Math.sqrt(targetVel.x * targetVel.x + targetVel.z * targetVel.z);
                if (targetSpeed > 0.05 || distance <= 3.5) {
                    tryPlaceCobweb(bot, target, state, server);
                }
            }
        }

        if (distance > meleeRange) {
            BotNavigation.moveToward(bot, target, settings.getMoveSpeed());
        } else if (distance < 1.5) {
            BotNavigation.moveAway(bot, target, 0.3);
        }

        if (target instanceof ServerPlayerEntity enemy) {
            state.enemyCooldown--;
            if (state.enemyCooldown < 0) state.enemyCooldown = 0;
            double enemyDist = bot.distanceTo(enemy);
            boolean enemyClosing = enemyDist < state.lastEnemyDist;
            state.lastEnemyDist = enemyDist;
            if (distance <= meleeRange + 1 && enemy.isSprinting() && enemyClosing && state.enemyCooldown <= 0) {
                state.shieldPredictTicks = settings.getShieldRaiseTicks();
                state.shieldHoldTicks = Math.max(6, settings.getShieldRaiseTicks() / 2);
                state.enemyCooldown = 15 + random.nextInt(10);
            }
        }
        if (state.shieldPredictTicks > 0) state.shieldPredictTicks--;
        if (state.shieldFlickerTicks > 0) state.shieldFlickerTicks--;
        if (state.shieldHoldTicks > 0) state.shieldHoldTicks--;

        float healthPercent = bot.getHealth() / bot.getMaxHealth();
        boolean lowHealth = healthPercent < settings.getShieldHealthThreshold();
        boolean willAttack = distance <= meleeRange && state.attackCooldown == 1;

        boolean holdShield = false;
        if (state.shieldPredictTicks > 0 || state.shieldHoldTicks > 0) {
            holdShield = true;
        } else if (!willAttack && lowHealth) {
            holdShield = true;
        } else if (!willAttack && healthPercent < 0.8f && random.nextFloat() < 0.004f && state.shieldToggleCooldown <= 0) {
            holdShield = true;
            state.shieldFlickerTicks = 3 + random.nextInt(3);
        }
        if (state.shieldFlickerTicks > 0) holdShield = true;

        boolean hasShieldInOffhand = bot.getOffHandStack().getItem() == Items.SHIELD;
        if (holdShield && !hasShieldInOffhand && !utilsState.isEating) {
            if (BotUtils.shouldUseMainHandShield(bot)) {
                BotUtils.equipShieldToMainHand(bot);
            } else {
                int shieldSlot = findShield(inventory);
                if (shieldSlot >= 0) {
                    ItemStack shield = inventory.getStack(shieldSlot);
                    inventory.setStack(40, shield);
                    inventory.setStack(shieldSlot, ItemStack.EMPTY);
                }
            }
        }

        boolean autoShieldActive = settings.isAutoShieldEnabled() && utilsState.isBlocking;
        if (!autoShieldActive) {
            if (holdShield && !state.isUsingShield && state.shieldToggleCooldown <= 0 && !utilsState.isEating) {
                startUsingShield(bot, server);
                state.isUsingShield = true;
            } else if (!holdShield && state.isUsingShield && state.shieldToggleCooldown <= 0) {
                stopUsingShield(bot, server);
                state.isUsingShield = false;
                state.shieldToggleCooldown = 5;
            }
        }

        if (!holdShield && BotUtils.shouldUseMainHandShield(bot) && !utilsState.isEating && !autoShieldActive) {
            BotUtils.unequipShieldFromMainHand(bot);
        }

        boolean enemyBlocking = target instanceof PlayerEntity player && player.isBlocking();
        if (settings.isShieldBreakEnabled() && enemyBlocking && distance <= meleeRange + 0.5) {
            int axeSlot = findAxe(inventory);
            if (axeSlot >= 0 && random.nextInt(100) < settings.getShieldBreakChance()) {
                if (state.isUsingShield || bot.isBlocking()) {
                    stopUsingShield(bot, server);
                    state.isUsingShield = false;
                }
                if (axeSlot >= 9) {
                    ItemStack axe = inventory.getStack(axeSlot);
                    ItemStack slot0 = inventory.getStack(0);
                    inventory.setStack(axeSlot, slot0);
                    inventory.setStack(0, axe);
                    axeSlot = 0;
                }
                org.stepan1411.pvp_bot.utils.InventoryHelper.setSelectedSlot(inventory, axeSlot);
                lookAtTarget(bot, target);
                attackWithCarpet(bot, target, server);

                state.shieldBroken = true;
                state.shieldBrokenTime = System.currentTimeMillis();
                state.shieldHitTicks = 0;
                state.attackCooldown = lowHealth ? (int) (settings.getAttackCooldown() * 1.5) : settings.getAttackCooldown();

                if (settings.isStunEnabled() && random.nextInt(100) < settings.getStunChance()) {
                    state.stunSwingDelayTicks = 2;
                    state.stunTarget = target;
                }
                return;
            }
        } else if (!enemyBlocking) {
            state.shieldHitTicks = 0;
        }

        if (distance <= meleeRange && state.attackCooldown <= 0) {
            if (bot.isBlocking() && (state.shieldPredictTicks > 0 || state.shieldHoldTicks > 0 || utilsState.blockHoldTicks > 0)) {
                return;
            }
            if (bot.getAttackCooldownProgress(0.5f) < 1.0f) {
                return;
            }

            int currentSlot = org.stepan1411.pvp_bot.utils.InventoryHelper.getSelectedSlot(inventory);
            double currentScore = getMeleeScore(inventory.getStack(currentSlot).getItem(), settings.isPreferSword());
            int weaponSlot = findMeleeWeapon(inventory);
            if (weaponSlot >= 0 && weaponSlot < 9) {
                double newScore = getMeleeScore(inventory.getStack(weaponSlot).getItem(), settings.isPreferSword());
                if (newScore > currentScore || currentScore == 0) {
                    org.stepan1411.pvp_bot.utils.InventoryHelper.setSelectedSlot(inventory, weaponSlot);
                }
            }

            if (settings.isCriticalsEnabled()) {
                if (bot.isOnGround()) {
                    bot.jump();
                    state.critFallTicks = 0;
                } else if (bot.getVelocity().y < 0) {
                    state.critFallTicks++;
                    if (state.critFallTicks >= settings.getCriticalFallTicks()) {
                        bot.fallDistance = 1.0f;
                        attack(bot, target);
                        state.critFallTicks = 0;
                        state.attackCooldown = lowHealth ? (int) (settings.getAttackCooldown() * 1.5) : settings.getAttackCooldown();
                    }
                }
            } else {
                attackWithCarpet(bot, target, server);
                state.attackCooldown = lowHealth ? (int) (settings.getAttackCooldown() * 1.5) : settings.getAttackCooldown();
            }
        } else {
            int currentSlot = org.stepan1411.pvp_bot.utils.InventoryHelper.getSelectedSlot(inventory);
            double currentScore = getMeleeScore(inventory.getStack(currentSlot).getItem(), settings.isPreferSword());
            int weaponSlot = findMeleeWeapon(inventory);
            if (weaponSlot >= 0 && weaponSlot < 9) {
                double newScore = getMeleeScore(inventory.getStack(weaponSlot).getItem(), settings.isPreferSword());
                if (newScore > currentScore || currentScore == 0) {
                    org.stepan1411.pvp_bot.utils.InventoryHelper.setSelectedSlot(inventory, weaponSlot);
                }
            }
        }
    }

    private static void handleRangedCombat(ServerPlayerEntity bot, Entity target, CombatState state, double distance, BotSettings settings, net.minecraft.server.MinecraftServer server) {
        if (state.isMaceDefending && state.maceDefenseCooldown > 0) {
            lookAtTarget(bot, target);
            return;
        }
        var utilsState = BotUtils.getState(bot.getName().getString());
        if (utilsState.isEating) return;

        if (distance <= settings.getMeleeRange()) {
            if (settings.isRangedRetreatOnClose()) {
                double dx = bot.getX() - target.getX();
                double dz = bot.getZ() - target.getZ();
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > 0) {
                    bot.addVelocity((dx / dist) * settings.getMoveSpeed() * 0.15, 0, (dz / dist) * settings.getMoveSpeed() * 0.15);
                }
            } else if (findMeleeWeapon(bot.getInventory()) >= 0) {
                if (state.isDrawingBow) stopUsingBow(bot, state);
                state.currentMode = CombatState.WeaponMode.MELEE;
                return;
            }
        }

        var inventory = bot.getInventory();
        int bowSlot = findRangedWeapon(inventory);
        if (bowSlot >= 0 && bowSlot < 9) {
            org.stepan1411.pvp_bot.utils.InventoryHelper.setSelectedSlot(inventory, bowSlot);
        }
        if (!hasArrows(inventory)) {
            state.currentMode = CombatState.WeaponMode.MELEE;
            return;
        }

        if (bot.getMainHandStack().getItem() instanceof CrossbowItem) {
            handleCrossbowCombat(bot, target, state, distance, settings);
        } else {
            handleBowCombat(bot, target, state, distance, settings);
        }

        if (settings.isRangedStrafeEnabled()) {
            state.strafeCooldown--;
            if (state.strafeCooldown <= 0) {
                state.strafeDir = -state.strafeDir;
                state.strafeCooldown = 20 + random.nextInt(20);
            }
            bot.sidewaysSpeed = (float) (settings.getMoveSpeed() * 0.6 * state.strafeDir);
        }

        double minRange = settings.getRangedMinRange();
        if (state.isRetreating) {
            double safeDist = settings.getRangedOptimalRange();
            if (distance < safeDist) {
                double dx = bot.getX() - target.getX();
                double dz = bot.getZ() - target.getZ();
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > 0) {
                    bot.addVelocity((dx / dist) * settings.getMoveSpeed() * 0.05, 0, (dz / dist) * settings.getMoveSpeed() * 0.05);
                }
            }
        } else if (distance < minRange) {
            double dx = bot.getX() - target.getX();
            double dz = bot.getZ() - target.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > 0) {
                bot.addVelocity((dx / dist) * settings.getMoveSpeed() * 0.05, 0, (dz / dist) * settings.getMoveSpeed() * 0.05);
            }
        } else if (distance > minRange + 2) {
            BotNavigation.moveToward(bot, target, settings.getMoveSpeed());
        }
    }

    private static void handleBowCombat(ServerPlayerEntity bot, Entity target, CombatState state, double distance, BotSettings settings) {
        if (!state.isDrawingBow) {
            bot.setCurrentHand(Hand.MAIN_HAND);
            state.isDrawingBow = true;
            state.bowDrawTicks = 0;
        } else {
            state.bowDrawTicks++;
            float drawProgress = Math.min(1.0f, state.bowDrawTicks / 20.0f);
            lookAtTargetWithPrediction(bot, target, drawProgress, settings);
            if (state.bowDrawTicks >= settings.getBowMinDrawTime()) {
                lookAtTargetWithPrediction(bot, target, drawProgress, settings);
                bot.stopUsingItem();
                state.isDrawingBow = false;
                state.bowDrawTicks = 0;
                state.attackCooldown = 5;
            }
        }
    }

    private static void handleCrossbowCombat(ServerPlayerEntity bot, Entity target, CombatState state, double distance, BotSettings settings) {
        ItemStack crossbow = bot.getMainHandStack();
        if (CrossbowItem.isCharged(crossbow)) {
            lookAtTargetWithPrediction(bot, target, 1.0f, settings);
            bot.stopUsingItem();
            state.attackCooldown = 5;
            state.isDrawingBow = false;
        } else if (!state.isDrawingBow) {
            bot.setCurrentHand(Hand.MAIN_HAND);
            state.isDrawingBow = true;
            state.bowDrawTicks = 0;
        } else {
            state.bowDrawTicks++;
            if (state.bowDrawTicks >= 25) {
                lookAtTargetWithPrediction(bot, target, 1.0f, settings);
                bot.stopUsingItem();
                state.isDrawingBow = false;
            }
        }
    }

    private static void stopUsingBow(ServerPlayerEntity bot, CombatState state) {
        if (state.isDrawingBow) {
            bot.stopUsingItem();
            state.isDrawingBow = false;
            state.bowDrawTicks = 0;
        }
    }

    private static void handleMaceCombat(ServerPlayerEntity bot, Entity target, CombatState state, double distance, BotSettings settings, net.minecraft.server.MinecraftServer server) {
        if (state.isMaceDefending && state.maceDefenseCooldown > 0) {
            lookAtTarget(bot, target);
            return;
        }
        var utilsState = BotUtils.getState(bot.getName().getString());
        if (utilsState.isEating) return;

        var inventory = bot.getInventory();
        if (state.isDrawingBow) stopUsingBow(bot, state);

        if (!bot.isOnGround()) {
            int maceSlot = findMace(inventory);
            if (maceSlot >= 0 && maceSlot < 9) {
                org.stepan1411.pvp_bot.utils.InventoryHelper.setSelectedSlot(inventory, maceSlot);
            }
            if (bot.getVelocity().y < 0 && distance <= 5.0 && state.attackCooldown <= 0) {
                attackWithCarpet(bot, target, server);
                state.attackCooldown = 1;
            }
            return;
        }

        if (distance <= settings.getMaceRange()) {
            int windChargeSlot = findWindCharge(inventory);
            if (windChargeSlot >= 0) {
                BotUtils.useWindCharge(bot, server);
                bot.jump();
            } else {
                int maceSlot = findMace(inventory);
                if (maceSlot >= 0 && maceSlot < 9) {
                    org.stepan1411.pvp_bot.utils.InventoryHelper.setSelectedSlot(inventory, maceSlot);
                }
                bot.jump();
                double dx = target.getX() - bot.getX();
                double dz = target.getZ() - bot.getZ();
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > 0) {
                    bot.addVelocity((dx / dist) * 0.3, 0.3, (dz / dist) * 0.3);
                }
            }
        }

        if (distance <= 3.5 && state.attackCooldown <= 0) {
            int maceSlot = findMace(inventory);
            if (maceSlot >= 0 && maceSlot < 9) {
                org.stepan1411.pvp_bot.utils.InventoryHelper.setSelectedSlot(inventory, maceSlot);
            }
            attackWithCarpet(bot, target, server);
            state.attackCooldown = settings.getAttackCooldown();
        }

        if (distance > settings.getMaceRange()) {
            BotNavigation.moveToward(bot, target, settings.getMoveSpeed());
        }
    }

    private static void handleSpearCombat(ServerPlayerEntity bot, Entity target, CombatState state, double distance, BotSettings settings, net.minecraft.server.MinecraftServer server) {
        if (state.isMaceDefending && state.maceDefenseCooldown > 0) {
            lookAtTarget(bot, target);
            return;
        }
        var utilsState = BotUtils.getState(bot.getName().getString());
        if (utilsState.isEating) return;

        var inventory = bot.getInventory();
        if (state.isDrawingBow) stopUsingBow(bot, state);

        int spearSlot = findSpear(inventory);
        if (spearSlot >= 0 && spearSlot < 9) {
            org.stepan1411.pvp_bot.utils.InventoryHelper.setSelectedSlot(inventory, spearSlot);
        }

        double chargeStartDistance = 10.0;
        double chargeHitDistance = 0.1;

        if (distance > chargeStartDistance) {
            if (state.isChargingSpear) {
                bot.stopUsingItem();
                state.isChargingSpear = false;
                state.spearChargeTicks = 0;
            }
            BotNavigation.moveToward(bot, target, settings.getMoveSpeed());
        } else if (distance > chargeHitDistance) {
            if (!state.isChargingSpear) {
                bot.setCurrentHand(Hand.MAIN_HAND);
                state.isChargingSpear = true;
                state.spearChargeTicks = 0;
            }
            state.spearChargeTicks++;
            BotNavigation.moveToward(bot, target, settings.getMoveSpeed() * 1.3);
            if (state.spearChargeTicks > 60) {
                bot.stopUsingItem();
                state.isChargingSpear = false;
                state.spearChargeTicks = 0;
            }
        } else {
            if (state.isChargingSpear) {
                bot.stopUsingItem();
                state.isChargingSpear = false;
                state.spearChargeTicks = 0;
                state.attackCooldown = 0;
            }
            BotNavigation.moveAway(bot, target, settings.getMoveSpeed());
        }
    }

    private static final java.util.Random random = new java.util.Random();

    private static void attack(ServerPlayerEntity bot, Entity target) {
        BotSettings settings = BotSettings.get();
        if (random.nextInt(100) < settings.getMissChance()) {
            bot.swingHand(Hand.MAIN_HAND);
            return;
        }
        bot.attack(target);
        bot.swingHand(Hand.MAIN_HAND);
        BotNavigation.startWtap(bot.getName().getString());
    }

    private static void attackWithCarpet(ServerPlayerEntity bot, Entity target, net.minecraft.server.MinecraftServer server) {
        BotSettings settings = BotSettings.get();
        var utilsState = BotUtils.getState(bot.getName().getString());
        if (!BotUtils.canAttack(bot, utilsState)) {
            bot.swingHand(Hand.MAIN_HAND);
            return;
        }
        if (!settings.isFriendlyFireEnabled() && target instanceof PlayerEntity) {
            if (BotFaction.areAllies(bot.getName().getString(), target.getName().getString())) {
                bot.swingHand(Hand.MAIN_HAND);
                return;
            }
        }
        if (random.nextInt(100) < settings.getMissChance()) {
            try {
                server.getCommandManager().getDispatcher().execute("player " + bot.getName().getString() + " swinghand", server.getCommandSource());
            } catch (Exception e) {
                bot.swingHand(Hand.MAIN_HAND);
            }
            return;
        }
        if (random.nextInt(100) < settings.getMistakeChance()) {
            bot.setYaw(bot.getYaw() + (random.nextFloat() - 0.5f) * 60);
        }
        try {
            server.getCommandManager().getDispatcher().execute("player " + bot.getName().getString() + " attack once", server.getCommandSource());
        } catch (Exception e) {
            bot.swingHand(Hand.MAIN_HAND);
        }
    }

    private static void startUsingShield(ServerPlayerEntity bot, net.minecraft.server.MinecraftServer server) {
        try {
            server.getCommandManager().getDispatcher().execute("player " + bot.getName().getString() + " use continuous", server.getCommandSource());
        } catch (Exception e) {
            bot.setCurrentHand(Hand.OFF_HAND);
        }
    }

    private static void stopUsingShield(ServerPlayerEntity bot, net.minecraft.server.MinecraftServer server) {
        try {
            server.getCommandManager().getDispatcher().execute("player " + bot.getName().getString() + " stop", server.getCommandSource());
        } catch (Exception e) {
            bot.clearActiveItem();
        }
    }

    private static void lookAtTarget(ServerPlayerEntity bot, Entity target) {
        lookAtPosition(bot, target.getEyePos());
    }

    private static void lookAtPosition(ServerPlayerEntity bot, Vec3d targetPos) {
        Vec3d botPos = bot.getEyePos();
        double dx = targetPos.x - botPos.x;
        double dy = targetPos.y - botPos.y;
        double dz = targetPos.z - botPos.z;
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (MathHelper.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
        float pitch = (float) -(MathHelper.atan2(dy, horizontalDist) * (180.0 / Math.PI));
        bot.setYaw(yaw);
        bot.setPitch(pitch);
        bot.setHeadYaw(yaw);
    }

    private static void lookAtTargetWithPrediction(ServerPlayerEntity bot, Entity target, float drawProgress, BotSettings settings) {
        if (!settings.isArrowPredictionEnabled()) {
            lookAtTarget(bot, target);
            return;
        }
        double arrowSpeed = Math.max(0.5, drawProgress * 3.0);
        Vec3d botPos = bot.getEyePos();
        Vec3d targetPos = target.getEyePos();
        Vec3d targetVel = target.getVelocity();
        double dx = targetPos.x - botPos.x;
        double dy = targetPos.y - botPos.y;
        double dz = targetPos.z - botPos.z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double effectiveSpeed = arrowSpeed * 0.95;
        if (effectiveSpeed <= 0) effectiveSpeed = 1.0;
        double flightTime = Math.min(distance / effectiveSpeed, 60);
        double px = targetPos.x + targetVel.x * flightTime;
        double py = targetPos.y + targetVel.y * flightTime + 0.5 * 0.05 * flightTime * flightTime;
        double pz = targetPos.z + targetVel.z * flightTime;
        lookAtPosition(bot, new Vec3d(px, py, pz));
    }

    private static int findMeleeWeapon(net.minecraft.entity.player.PlayerInventory inventory) {
        BotSettings settings = BotSettings.get();
        boolean preferSword = settings.isPreferSword();
        int bestSlot = -1;
        double bestScore = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.isEmpty()) continue;
            double score = getMeleeScore(stack.getItem(), preferSword);
            if (score > bestScore) {
                bestScore = score;
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    private static int findAxe(net.minecraft.entity.player.PlayerInventory inventory) {
        int bestSlot = -1;
        double bestDamage = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.isEmpty()) continue;
            Item item = stack.getItem();
            if (item instanceof AxeItem) {
                double damage = getAxeDamage(item);
                if (damage > bestDamage) {
                    bestDamage = damage;
                    bestSlot = i;
                }
            }
        }
        return bestSlot;
    }

    private static int findShield(net.minecraft.entity.player.PlayerInventory inventory) {
        if (inventory.getStack(40).getItem() == Items.SHIELD) return 40;
        for (int i = 0; i < 36; i++) {
            if (inventory.getStack(i).getItem() == Items.SHIELD) return i;
        }
        return -1;
    }

    private static double getAxeDamage(Item item) {
        if (item == Items.NETHERITE_AXE) return 10;
        if (item == Items.DIAMOND_AXE || item == Items.IRON_AXE || item == Items.STONE_AXE) return 9;
        if (item == Items.GOLDEN_AXE || item == Items.WOODEN_AXE) return 7;
        return 0;
    }

    private static double getMeleeScore(Item item, boolean preferSword) {
        double baseDamage = getMeleeDamage(item);
        if (baseDamage == 0) return 0;
        return (preferSword && isSword(item)) ? baseDamage + 5 : baseDamage;
    }

    private static boolean isSword(Item item) {
        return item == Items.NETHERITE_SWORD || item == Items.DIAMOND_SWORD || item == Items.IRON_SWORD ||
               item == Items.GOLDEN_SWORD || item == Items.STONE_SWORD || item == Items.WOODEN_SWORD;
    }

    private static double getMeleeDamage(Item item) {
        if (item == Items.NETHERITE_AXE || item == Items.TRIDENT) return 10;
        if (item == Items.NETHERITE_SWORD) return 8;
        if (item == Items.DIAMOND_SWORD) return 7;
        if (item == Items.DIAMOND_AXE || item == Items.IRON_AXE || item == Items.STONE_AXE) return 9;
        if (item == Items.IRON_SWORD) return 6;
        if (item == Items.STONE_SWORD) return 5;
        if (item == Items.GOLDEN_SWORD || item == Items.WOODEN_SWORD) return 4;
        if (item == Items.GOLDEN_AXE || item == Items.WOODEN_AXE) return 7;
        return 0;
    }

    private static int findRangedWeapon(net.minecraft.entity.player.PlayerInventory inventory) {
        for (int i = 0; i < 36; i++) {
            if (inventory.getStack(i).getItem() instanceof CrossbowItem) return i;
        }
        for (int i = 0; i < 36; i++) {
            if (inventory.getStack(i).getItem() instanceof BowItem) return i;
        }
        return -1;
    }

    private static int findMace(net.minecraft.entity.player.PlayerInventory inventory) {
        for (int i = 0; i < 36; i++) {
            if (inventory.getStack(i).getItem() == Items.MACE) return i;
        }
        return -1;
    }

    private static int findWindCharge(net.minecraft.entity.player.PlayerInventory inventory) {
        for (int i = 0; i < 36; i++) {
            if (inventory.getStack(i).getItem() == Items.WIND_CHARGE) return i;
        }
        return -1;
    }

    private static int findSpear(net.minecraft.entity.player.PlayerInventory inventory) {
        for (int i = 0; i < 36; i++) {
            if (inventory.getStack(i).getItem().toString().toLowerCase(Locale.ROOT).contains("spear")) return i;
        }
        return -1;
    }

    private static int findCobweb(net.minecraft.entity.player.PlayerInventory inventory) {
        for (int i = 0; i < 36; i++) {
            if (inventory.getStack(i).getItem() == Items.COBWEB) return i;
        }
        return -1;
    }

    private static BlockPos findCobwebPlacementPos(ServerPlayerEntity bot, Entity target) {
        var world = bot.getEntityWorld();
        BlockPos feet = target.getBlockPos();
        BlockPos[] candidates = {
            feet,
            feet.up(),
            BlockPos.ofFloored(target.getX(), target.getY(), target.getZ())
        };
        for (BlockPos pos : candidates) {
            if (bot.getEyePos().squaredDistanceTo(Vec3d.ofCenter(pos)) <= 36.0) {
                var state = world.getBlockState(pos);
                if (state.isReplaceable() && state.getBlock() != Blocks.COBWEB) {
                    return pos;
                }
            }
        }
        return null;
    }

    private static boolean tryPlaceCobweb(ServerPlayerEntity bot, Entity target, CombatState state, net.minecraft.server.MinecraftServer server) {
        if (state.isPlacingCobweb || state.cobwebCooldown > 0) return false;
        var inventory = bot.getInventory();
        int cobwebSlot = findCobweb(inventory);
        if (cobwebSlot < 0) return false;

        BlockPos placePos = findCobwebPlacementPos(bot, target);
        if (placePos == null) return false;

        if (state.isUsingShield || bot.isBlocking()) {
            stopUsingShield(bot, server);
            state.isUsingShield = false;
        }

        int prevSelected = org.stepan1411.pvp_bot.utils.InventoryHelper.getSelectedSlot(inventory);
        state.cobwebSavedSlot = prevSelected;
        state.cobwebSavedSlotItem = ItemStack.EMPTY;

        int targetHotbarSlot = cobwebSlot;
        if (cobwebSlot >= 9) {
            targetHotbarSlot = (prevSelected >= 0 && prevSelected < 9) ? prevSelected : 8;
            ItemStack cobwebStack = inventory.getStack(cobwebSlot);
            ItemStack displaced = inventory.getStack(targetHotbarSlot).copy();
            state.cobwebSavedSlot = cobwebSlot;
            state.cobwebSavedSlotItem = displaced;
            inventory.setStack(targetHotbarSlot, cobwebStack);
            inventory.setStack(cobwebSlot, displaced);
        }

        org.stepan1411.pvp_bot.utils.InventoryHelper.setSelectedSlot(inventory, targetHotbarSlot);
        state.cobwebTargetPos = placePos;
        state.isPlacingCobweb = true;
        state.cobwebPlaceTicks = 0;

        lookAtPosition(bot, new Vec3d(placePos.getX() + 0.5, placePos.getY() + 0.25, placePos.getZ() + 0.5));
        try {
            server.getCommandManager().getDispatcher().execute("player " + bot.getName().getString() + " use once", server.getCommandSource());
        } catch (Exception ignored) {
        }
        return true;
    }

    private static void handleCobwebPlacement(ServerPlayerEntity bot, Entity target, CombatState state, net.minecraft.server.MinecraftServer server) {
        state.cobwebPlaceTicks++;
        var world = bot.getEntityWorld();
        BlockPos pos = state.cobwebTargetPos != null ? state.cobwebTargetPos : findCobwebPlacementPos(bot, target);
        if (pos == null || world.getBlockState(pos).getBlock() == Blocks.COBWEB) {
            finishCobwebPlacement(bot, state, true);
            return;
        }

        lookAtPosition(bot, new Vec3d(pos.getX() + 0.5, pos.getY() + 0.25, pos.getZ() + 0.5));
        if ((state.cobwebPlaceTicks == 2 || state.cobwebPlaceTicks == 4) && findCobweb(bot.getInventory()) >= 0) {
            try {
                server.getCommandManager().getDispatcher().execute("player " + bot.getName().getString() + " use once", server.getCommandSource());
            } catch (Exception ignored) {
            }
        }

        if (world.getBlockState(pos).getBlock() == Blocks.COBWEB || state.cobwebPlaceTicks >= 5) {
            finishCobwebPlacement(bot, state, true);
        }
    }

    private static void finishCobwebPlacement(ServerPlayerEntity bot, CombatState state, boolean triggerCooldown) {
        var inventory = bot.getInventory();
        if (state.cobwebSavedSlot >= 0) {
            if (state.cobwebSavedSlot >= 9 && !state.cobwebSavedSlotItem.isEmpty()) {
                int cobwebNow = findCobweb(inventory);
                if (cobwebNow >= 0 && cobwebNow < 9) {
                    ItemStack cobwebStack = inventory.getStack(cobwebNow);
                    inventory.setStack(cobwebNow, state.cobwebSavedSlotItem);
                    inventory.setStack(state.cobwebSavedSlot, cobwebStack);
                }
            } else if (state.cobwebSavedSlot < 9) {
                org.stepan1411.pvp_bot.utils.InventoryHelper.setSelectedSlot(inventory, state.cobwebSavedSlot);
            }
        }

        state.cobwebSavedSlot = -1;
        state.cobwebSavedSlotItem = ItemStack.EMPTY;
        state.cobwebTargetPos = null;
        state.isPlacingCobweb = false;
        state.cobwebPlaceTicks = 0;
        if (triggerCooldown) {
            state.cobwebCooldown = 12;
        }
    }

    private static boolean hasArrows(net.minecraft.entity.player.PlayerInventory inventory) {
        for (int i = 0; i < 36; i++) {
            if (inventory.getStack(i).getItem() instanceof ArrowItem) return true;
        }
        return false;
    }

    private static boolean hasRangedWeaponInInventory(ServerPlayerEntity bot) {
        return findRangedWeapon(bot.getInventory()) >= 0;
    }

    public static void setTarget(String botName, String targetName) {
        getState(botName).forcedTargetName = targetName;
    }

    public static void clearTarget(String botName) {
        CombatState state = getState(botName);
        state.forcedTargetName = null;
        state.target = null;
        state.lastAttacker = null;
        state.lastAttackTime = 0;
        state.isRetreating = false;
        state.stunSwingDelayTicks = 0;
        state.stunTarget = null;
        state.pendingSwordShieldCounter = false;
        state.swordCounterTarget = null;
        elytraMaceStates.remove(botName);
        pendingElytraRestore.remove(botName);
        windBurstStates.remove(botName);
    }

    public static void onBotDamaged(ServerPlayerEntity bot, DamageSource source) {
        Entity attacker = source.getAttacker();
        Entity sourceEntity = source.getSource();
        if (attacker == null) attacker = sourceEntity;
        if (attacker == null || attacker == bot || !(attacker instanceof LivingEntity livingAttacker)) return;

        String botName = bot.getName().getString();
        CombatState state = getState(botName);
        state.lastAttacker = attacker;
        state.lastAttackTime = System.currentTimeMillis();

        boolean wasBlocking = bot.isBlocking() || state.isUsingShield;
        boolean hitBySword = isSwordStack(livingAttacker.getMainHandStack()) ||
                             (sourceEntity instanceof LivingEntity srcLiving && isSwordStack(srcLiving.getMainHandStack()));
        if (wasBlocking && hitBySword) {
            state.pendingSwordShieldCounter = true;
            state.swordCounterTarget = attacker;
            if (bot.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld serverWorld) {
                dropShieldImmediately(bot, state, serverWorld.getServer());
                equipBestMeleeHotbarSlot(bot, state, BotSettings.get());
                lookAtTarget(bot, attacker);
                attackWithCarpet(bot, attacker, serverWorld.getServer());
                state.pendingSwordShieldCounter = false;
                state.swordCounterTarget = null;
            }
        }

        if (!elytraMaceStates.containsKey(botName) && !pendingElytraRestore.containsKey(botName) && !windBurstStates.containsKey(botName)) {
            if (attacker instanceof PlayerEntity && attacker.isAlive() && hasElytraMaceItems(bot)) {
                startElytraMace(botName, attacker.getName().getString());
            }
        }
    }

    public static Entity getTarget(String botName) {
        return getState(botName).target;
    }

    private static boolean hasElytraMaceItems(ServerPlayerEntity bot) {
        var inv = bot.getInventory();
        boolean hasElytra = false, hasFireworks = false, hasMace = false;
        for (int i = 0; i < 41; i++) {
            Item item = inv.getStack(i).getItem();
            if (item == Items.ELYTRA) hasElytra = true;
            if (item == Items.FIREWORK_ROCKET) hasFireworks = true;
            if (item == Items.MACE) hasMace = true;
        }
        return hasElytra && hasFireworks && hasMace;
    }

    private static void handleElytraMace(ServerPlayerEntity bot, net.minecraft.server.MinecraftServer server) {
        String botName = bot.getName().getString();
        ElytraMaceState state = elytraMaceStates.get(botName);
        if (state == null) return;

        var inventory = bot.getInventory();
        state.tickCounter++;
        state.totalTicks++;
        if (state.totalTicks > 300) {
            elytraMaceStates.remove(botName);
            return;
        }
        if (state.phase < 6) {
            boolean hasFireworks = false;
            for (int i = 0; i < 41; i++) {
                if (inventory.getStack(i).getItem() == Items.FIREWORK_ROCKET) {
                    hasFireworks = true;
                    break;
                }
            }
            if (!hasFireworks) {
                elytraMaceStates.remove(botName);
                return;
            }
        }
        Entity currentTarget = server.getPlayerManager().getPlayer(state.targetName);
        if (currentTarget == null || !currentTarget.isAlive()) {
            elytraMaceStates.remove(botName);
            return;
        }

        switch (state.phase) {
            case 0 -> {
                try { server.getCommandManager().getDispatcher().execute("player " + botName + " stop", server.getCommandSource()); } catch (Exception ignored) {}
                for (int i = 0; i < 41; i++) {
                    if (inventory.getStack(i).getItem() == Items.ELYTRA) {
                        if (i != 38) {
                            ItemStack elytra = inventory.getStack(i);
                            inventory.setStack(i, inventory.getStack(38));
                            inventory.setStack(38, elytra);
                        }
                        break;
                    }
                }
                state.phase = 1;
                state.tickCounter = 0;
            }
            case 1 -> {
                for (int i = 0; i < 41; i++) {
                    if (inventory.getStack(i).getItem() == Items.FIREWORK_ROCKET) {
                        if (i != 0) {
                            ItemStack rocket = inventory.getStack(i);
                            inventory.setStack(i, inventory.getStack(0));
                            inventory.setStack(0, rocket);
                        }
                        org.stepan1411.pvp_bot.utils.InventoryHelper.setSelectedSlot(inventory, 0);
                        break;
                    }
                }
                state.phase = 2;
                state.tickCounter = 0;
            }
            case 2 -> {
                org.stepan1411.pvp_bot.utils.InventoryHelper.setSelectedSlot(inventory, 0);
                bot.setPitch(-90);
                try { server.getCommandManager().getDispatcher().execute("player " + botName + " jump once", server.getCommandSource()); } catch (Exception ignored) {}
                state.phaseStartY = bot.getY();
                state.phase = 3;
                state.tickCounter = 0;
            }
            case 3 -> {
                org.stepan1411.pvp_bot.utils.InventoryHelper.setSelectedSlot(inventory, 0);
                if (state.tickCounter == 5 && bot.getY() - state.phaseStartY < 0.3) {
                    elytraMaceStates.remove(botName);
                    return;
                }
                if (state.tickCounter >= 6) {
                    state.phase = 4;
                    state.tickCounter = 0;
                }
            }
            case 4 -> {
                org.stepan1411.pvp_bot.utils.InventoryHelper.setSelectedSlot(inventory, 0);
                try { server.getCommandManager().getDispatcher().execute("player " + botName + " jump once", server.getCommandSource()); } catch (Exception ignored) {}
                state.phase = 5;
                state.tickCounter = 0;
            }
            case 5 -> {
                org.stepan1411.pvp_bot.utils.InventoryHelper.setSelectedSlot(inventory, 0);
                if (state.tickCounter >= 1) {
                    try { server.getCommandManager().getDispatcher().execute("player " + botName + " use once", server.getCommandSource()); } catch (Exception ignored) {}
                    state.phase = 6;
                    state.tickCounter = 0;
                }
            }
            case 6 -> {
                if (state.tickCounter >= 10) {
                    state.phase = 7;
                    state.tickCounter = 0;
                }
            }
            case 7 -> {
                state.savedElytra = inventory.getStack(38).copy();
                if (inventory.getStack(38).getItem() == Items.ELYTRA) inventory.setStack(38, ItemStack.EMPTY);
                for (int i = 0; i < 41; i++) {
                    ItemStack stack = inventory.getStack(i);
                    if (stack.isEmpty()) continue;
                    Item item = stack.getItem();
                    boolean isChestplate = item == Items.NETHERITE_CHESTPLATE || item == Items.DIAMOND_CHESTPLATE ||
                        item == Items.IRON_CHESTPLATE || item == Items.CHAINMAIL_CHESTPLATE ||
                        item == Items.GOLDEN_CHESTPLATE || item == Items.LEATHER_CHESTPLATE;
                    if (isChestplate) {
                        inventory.setStack(38, stack.copy());
                        inventory.setStack(i, ItemStack.EMPTY);
                        break;
                    }
                }
                state.phase = 8;
                state.tickCounter = 0;
            }
            case 8 -> {
                if (state.tickCounter >= 1) {
                    state.phase = 9;
                    state.tickCounter = 0;
                }
            }
            case 9 -> {
                if (!state.savedElytra.isEmpty()) {
                    ItemStack current38 = inventory.getStack(38);
                    Item item38 = current38.getItem();
                    boolean isChestplate = item38 == Items.NETHERITE_CHESTPLATE || item38 == Items.DIAMOND_CHESTPLATE ||
                        item38 == Items.IRON_CHESTPLATE || item38 == Items.CHAINMAIL_CHESTPLATE ||
                        item38 == Items.GOLDEN_CHESTPLATE || item38 == Items.LEATHER_CHESTPLATE;
                    if (isChestplate) {
                        boolean placed = false;
                        for (int i = 0; i < 38; i++) {
                            if (inventory.getStack(i).isEmpty()) {
                                inventory.setStack(i, current38.copy());
                                placed = true;
                                break;
                            }
                        }
                        if (!placed) bot.dropItem(current38.copy(), true, true);
                    }
                    inventory.setStack(38, state.savedElytra.copy());
                }
                state.phase = 10;
                state.tickCounter = 0;
            }
            case 10 -> {
                org.stepan1411.pvp_bot.utils.InventoryHelper.setSelectedSlot(inventory, 0);
                if (state.tickCounter >= 2) {
                    try { server.getCommandManager().getDispatcher().execute("player " + botName + " jump once", server.getCommandSource()); } catch (Exception ignored) {}
                    state.phase = 11;
                    state.tickCounter = 0;
                }
            }
            case 11 -> {
                Entity targetEntity = server.getPlayerManager().getPlayer(state.targetName);
                if (targetEntity != null && targetEntity.isAlive()) {
                    lookAtTarget(bot, targetEntity);
                    BotNavigation.moveToward(bot, targetEntity, 1.0);
                    if (bot.distanceTo(targetEntity) <= 8.0) {
                        ItemStack chestSlot = inventory.getStack(38);
                        if (chestSlot.getItem() == Items.ELYTRA) {
                            inventory.setStack(38, ItemStack.EMPTY);
                            pendingElytraRestore.put(botName, chestSlot.copy());
                            int maceSlot = findMace(inventory);
                            if (maceSlot >= 0) {
                                if (maceSlot >= 9) {
                                    ItemStack mace = inventory.getStack(maceSlot);
                                    inventory.setStack(maceSlot, inventory.getStack(0));
                                    inventory.setStack(0, mace);
                                    maceSlot = 0;
                                }
                                org.stepan1411.pvp_bot.utils.InventoryHelper.setSelectedSlot(inventory, maceSlot);
                            }
                            state.windBurstDelay = 0;
                        } else {
                            state.windBurstDelay++;
                            if (state.windBurstDelay >= 3) {
                                lookAtTarget(bot, targetEntity);
                                attackWithCarpet(bot, targetEntity, server);
                                getState(botName).forcedTargetName = state.targetName;
                                if (hasWindBurstMace(bot)) startWindBurst(botName, state.targetName);
                                elytraMaceStates.remove(botName);
                            }
                        }
                    }
                } else {
                    elytraMaceStates.remove(botName);
                }
            }
        }
    }

    private static void handleWindBurst(ServerPlayerEntity bot, net.minecraft.server.MinecraftServer server) {
        String botName = bot.getName().getString();
        WindBurstState state = windBurstStates.get(botName);
        if (state == null) return;

        var inventory = bot.getInventory();
        state.tickCounter++;
        if (state.tickCounter > 200) {
            windBurstStates.remove(botName);
            pendingElytraRestore.remove(botName);
            return;
        }
        Entity target = server.getPlayerManager().getPlayer(state.targetName);
        if (target == null || !target.isAlive()) {
            windBurstStates.remove(botName);
            return;
        }

        switch (state.phase) {
            case 0 -> {
                if (state.tickCounter >= 10) {
                    state.phase = 1;
                    state.tickCounter = 0;
                }
            }
            case 1 -> {
                ItemStack savedElytra = pendingElytraRestore.remove(botName);
                if (savedElytra != null && !savedElytra.isEmpty()) {
                    inventory.setStack(38, savedElytra.copy());
                } else {
                    for (int i = 0; i < 41; i++) {
                        if (inventory.getStack(i).getItem() == Items.ELYTRA) {
                            if (i != 38) {
                                ItemStack elytra = inventory.getStack(i);
                                inventory.setStack(i, inventory.getStack(38));
                                inventory.setStack(38, elytra);
                            }
                            break;
                        }
                    }
                }
                try { server.getCommandManager().getDispatcher().execute("player " + botName + " jump once", server.getCommandSource()); } catch (Exception ignored) {}
                state.phase = 2;
                state.tickCounter = 0;
            }
            case 2 -> {
                if (state.tickCounter >= 1) {
                    boolean foundFirework = false;
                    for (int i = 0; i < 41; i++) {
                        if (inventory.getStack(i).getItem() == Items.FIREWORK_ROCKET) {
                            if (i != 0) {
                                ItemStack rocket = inventory.getStack(i);
                                inventory.setStack(i, inventory.getStack(0));
                                inventory.setStack(0, rocket);
                            }
                            org.stepan1411.pvp_bot.utils.InventoryHelper.setSelectedSlot(inventory, 0);
                            foundFirework = true;
                            break;
                        }
                    }
                    if (!foundFirework) {
                        windBurstStates.remove(botName);
                        break;
                    }
                    lookAtTarget(bot, target);
                    try { server.getCommandManager().getDispatcher().execute("player " + botName + " use once", server.getCommandSource()); } catch (Exception ignored) {}
                    state.phase = 3;
                    state.tickCounter = 0;
                }
            }
            case 3 -> {
                double dist = bot.distanceTo(target);
                BotNavigation.moveToward(bot, target, 1.0);
                if (dist <= 6.0) {
                    lookAtTarget(bot, target);
                    ItemStack chestSlot = inventory.getStack(38);
                    if (chestSlot.getItem() == Items.ELYTRA) {
                        inventory.setStack(38, ItemStack.EMPTY);
                        pendingElytraRestore.put(botName, chestSlot.copy());
                        int maceSlot = findMace(inventory);
                        if (maceSlot >= 0) {
                            if (maceSlot >= 9) {
                                ItemStack mace = inventory.getStack(maceSlot);
                                inventory.setStack(maceSlot, inventory.getStack(0));
                                inventory.setStack(0, mace);
                                maceSlot = 0;
                            }
                            org.stepan1411.pvp_bot.utils.InventoryHelper.setSelectedSlot(inventory, maceSlot);
                        }
                        if (bot.fallDistance < 1.5f) {
                            try { server.getCommandManager().getDispatcher().execute("player " + botName + " attack once", server.getCommandSource()); } catch (Exception ignored) {}
                            windBurstStates.remove(botName);
                            break;
                        }
                    }
                    try { server.getCommandManager().getDispatcher().execute("player " + botName + " attack once", server.getCommandSource()); } catch (Exception ignored) {}
                } else {
                    lookAtPosition(bot, new Vec3d(target.getX(), target.getY() + 3, target.getZ()));
                    if (inventory.getStack(38).getItem() != Items.ELYTRA) {
                        state.phase = 0;
                        state.tickCounter = 0;
                    }
                }
            }
        }
    }
}

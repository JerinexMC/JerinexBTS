package org.stepan1411.pvp_bot.bot;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.class_1297;
import net.minecraft.class_1937;
import net.minecraft.class_2246;
import net.minecraft.class_2338;
import net.minecraft.class_2399;
import net.minecraft.class_243;
import net.minecraft.class_2541;
import net.minecraft.class_2680;
import net.minecraft.class_3222;

public class BotNavigation {

    private static final Map<String, NavigationState> navStates = new HashMap<>();

    public static class NavigationState {
        public int stuckTicks = 0;
        public class_243 lastPosition = null;
        public int avoidDirection = 0;
        public int avoidTicks = 0;
        public int jumpCooldown = 0;

        public class_243 spawnPosition = null;
        public class_243 wanderTarget = null;
        public int wanderCooldown = 0;
        public int idleTicks = 0;

        public java.util.LinkedList<class_243> pathHistory = new java.util.LinkedList<>();
        public static final int MAX_PATH_HISTORY = 15;

        // Strafe
        public int strafeDir = 1;
        public int strafeTicks = 0;

        // W-tap
        public boolean wtapActive = false;

        // Knockback
        public int knockbackTicks = 0;
    }

    public static NavigationState getState(String botName) {
        return navStates.computeIfAbsent(botName, k -> new NavigationState());
    }

    public static void removeState(String botName) {
        navStates.remove(botName);
    }

    public static void startWtap(String botName) {
        NavigationState state = navStates.get(botName);
        if (state != null) state.wtapActive = true;
    }

    public static void moveToward(class_3222 bot, class_1297 target, double speed) {
        NavigationState state = getState(bot.method_5477().getString());
        if (state.jumpCooldown > 0) state.jumpCooldown--;
        if (state.avoidTicks > 0) state.avoidTicks--;
        class_243 targetPos = new class_243(target.method_23317(), target.method_23318(), target.method_23321());
        moveTowardPos(bot, targetPos, speed, state, false);
    }

    public static void moveAway(class_3222 bot, class_1297 target, double speed) {
        NavigationState state = getState(bot.method_5477().getString());
        if (state.jumpCooldown > 0) state.jumpCooldown--;
        if (state.avoidTicks > 0) state.avoidTicks--;
        double dx = bot.method_23317() - target.method_23317();
        double dz = bot.method_23321() - target.method_23321();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist > 0) { dx /= dist; dz /= dist; }
        class_243 awayPos = new class_243(bot.method_23317() + dx * 10, bot.method_23318(), bot.method_23321() + dz * 10);
        moveTowardPos(bot, awayPos, speed, state, false);
    }

    public static void moveTowardPosition(class_3222 bot, class_243 targetPos, double speed) {
        NavigationState state = getState(bot.method_5477().getString());
        if (state.jumpCooldown > 0) state.jumpCooldown--;
        if (state.avoidTicks > 0) state.avoidTicks--;
        moveTowardPos(bot, targetPos, speed, state, false);
    }

    public static void moveTowardCombat(class_3222 bot, class_243 targetPos, double speed, float strafeAngle) {
        NavigationState state = getState(bot.method_5477().getString());
        if (state.jumpCooldown > 0) state.jumpCooldown--;
        if (state.avoidTicks > 0) state.avoidTicks--;
        moveTowardPos(bot, targetPos, speed, state, true);
    }

    private static void updateStrafe(NavigationState state) {
        if (state.strafeTicks <= 0) {
            state.strafeDir = -state.strafeDir;
            state.strafeTicks = 8 + (int)(Math.random() * 11);
        }
        state.strafeTicks--;
    }

    private static boolean isKnockedBack(class_3222 bot) {
        class_243 vel = bot.method_18798();
        double horiz = Math.sqrt(vel.field_1352 * vel.field_1352 + vel.field_1350 * vel.field_1350);
        return horiz > 0.35;
    }

    private static void moveTowardPos(class_3222 bot, class_243 targetPos, double speed, NavigationState state, boolean combat) {
        var world = bot.method_51469();
        class_243 botPos = new class_243(bot.method_23317(), bot.method_23318(), bot.method_23321());

        if (state.pathHistory.isEmpty() || botPos.method_1022(state.pathHistory.getLast()) > 0.5) {
            state.pathHistory.add(botPos);
            if (state.pathHistory.size() > NavigationState.MAX_PATH_HISTORY)
                state.pathHistory.removeFirst();
        }

        // === KNOCKBACK ===
        if (isKnockedBack(bot) && state.knockbackTicks < 10) {
            state.knockbackTicks++;
            bot.method_5728(false);
            bot.field_6250 = 0;
            bot.field_6212 = 0;
            state.lastPosition = botPos;
            return;
        }
        state.knockbackTicks = 0;

        checkIfStuck(bot, state);

        double dx = targetPos.field_1352 - botPos.field_1352;
        double dz = targetPos.field_1350 - botPos.field_1350;
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);

        if (horizontalDist > 0.1) { dx /= horizontalDist; dz /= horizontalDist; }

        // === WATER ===
        if (bot.method_5799() || bot.method_5869()) {
            handleWaterMovement(bot, targetPos, speed, state, dx, dz, horizontalDist, botPos);
            return;
        }

        // === AVOID (stuck) ===
        if (state.avoidTicks > 0) {
            double tempDx = dx;
            if (state.avoidDirection > 0) { dx = -dz; dz = tempDx; }
            else { dx = dz; dz = -tempDx; }
        }

        // === COMBAT STRAFE ===
        if (combat) {
            updateStrafe(state);
            double tempDx = dx;
            dx = dx + dz * state.strafeDir * 0.4;
            dz = dz - tempDx * state.strafeDir * 0.4;
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len > 0) { dx /= len; dz /= len; }
        }

        // === OBSTACLE DETECTION ===
        class_2338 feetPos = new class_2338((int) Math.floor(botPos.field_1352 + dx * 0.5), (int) Math.floor(botPos.field_1351), (int) Math.floor(botPos.field_1350 + dz * 0.5));
        class_2338 headPos = feetPos.method_10084();
        class_2338 aboveHeadPos = feetPos.method_10086(2);
        boolean blockAtFeet = isBlockSolid(world, feetPos);
        boolean blockAtHead = isBlockSolid(world, headPos);
        boolean canJumpUp = blockAtFeet && !blockAtHead && !isBlockSolid(world, aboveHeadPos);
        boolean isWall = blockAtFeet && blockAtHead;
        boolean onLadder = isClimbable(world, bot.method_24515()) || isClimbable(world, bot.method_24515().method_10084());
        class_2338 groundFront = new class_2338((int) Math.floor(botPos.field_1352 + dx * 1.2), (int) Math.floor(botPos.field_1351 - 1), (int) Math.floor(botPos.field_1350 + dz * 1.2));
        boolean holeAhead = !isBlockSolid(world, groundFront) && !isBlockSolid(world, groundFront.method_10074());

        BotSettings settings = BotSettings.get();

        // === PATH WALK TYPE ===
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

        // === JUMPING ===
        if (bot.method_24828() && state.jumpCooldown <= 0) {
            if (canJumpUp) { bot.method_6043(); bot.method_5762(dx * 0.2, 0, dz * 0.2); state.jumpCooldown = 8; }
            else if (onLadder) { bot.method_6043(); state.jumpCooldown = 5; }
            else if (holeAhead) { bot.method_6043(); bot.method_5762(dx * 0.35, 0.05, dz * 0.35); state.jumpCooldown = 12; }
            else if (isWall && state.avoidTicks <= 0) {
                state.avoidDirection = (Math.random() > 0.5) ? 1 : -1;
                state.avoidTicks = 25;
                bot.method_6043();
                state.jumpCooldown = 10;
            }
        }

        // === LADDER ===
        if (onLadder) {
            bot.method_5762(0, 0.12, 0);
            bot.method_5728(false);
            bot.field_6250 = 1.0f;
            state.lastPosition = botPos;
            return;
        }

        // === BHOP ===
        boolean useBhop = (pathWalkType != null) ? pathWalkType.equals("bhop") : settings.isBhopEnabled();
        boolean shouldBhop = useBhop && speed >= 1.0 && !canJumpUp && !isWall && !holeAhead && state.jumpCooldown <= 0 && bot.method_24828();
        if (shouldBhop) { bot.method_6043(); state.jumpCooldown = 10; }

        // === W-TAP ===
        boolean stopSprint = false;
        if (state.wtapActive && bot.method_24828()) {
            stopSprint = true;
            state.wtapActive = false;
        }

        // === APPLY MOVEMENT ===
        boolean shouldSprint = pathWalkType == null || !pathWalkType.equals("walk");
        bot.method_5728(shouldSprint && !stopSprint);
        bot.field_6250 = stopSprint ? 0.5f : 1.0f;
        bot.field_6212 = combat ? state.strafeDir * 0.3f : 0;

        double moveForce = bot.method_24828() ? 0.1 : 0.02;
        bot.method_5762(dx * speed * moveForce, 0, dz * speed * moveForce);

        state.lastPosition = botPos;
    }

    private static void handleWaterMovement(class_3222 bot, class_243 targetPos, double speed, NavigationState state, double dx, double dz, double horizontalDist, class_243 botPos) {
        boolean targetFar = horizontalDist > 8.0;
        double waterLevel = bot.method_23318();
        double targetLevel = targetPos.field_1351;
        if (targetLevel > waterLevel + 0.5) bot.method_5762(0, 0.08, 0);
        else if (targetLevel < waterLevel - 0.5) bot.method_5762(0, -0.04, 0);
        if (targetFar) {
            bot.method_5728(false);
            bot.field_6250 = 0.8f;
            bot.field_6212 = 0;
            bot.method_5762(dx * speed * 0.02, 0, dz * speed * 0.02);
            if (state.jumpCooldown <= 0) { bot.method_6043(); state.jumpCooldown = 8; }
        } else {
            bot.method_5728(false);
            bot.field_6250 = 0.6f;
            bot.field_6212 = 0;
            bot.method_5762(dx * speed * 0.015, 0, dz * speed * 0.015);
            if (state.jumpCooldown <= 0) { bot.method_6043(); state.jumpCooldown = 10; }
        }
        state.lastPosition = botPos;
    }

    private static void checkIfStuck(class_3222 bot, NavigationState state) {
        class_243 currentPos = new class_243(bot.method_23317(), bot.method_23318(), bot.method_23321());
        if (state.lastPosition == null) { state.lastPosition = currentPos; return; }
        double moved = currentPos.method_1022(state.lastPosition);
        if (moved < 0.05 && bot.method_24828()) {
            state.stuckTicks++;
            if (state.stuckTicks > 10) {
                if (state.avoidTicks <= 0) {
                    state.avoidDirection = (state.avoidDirection == 0) ? 1 : -state.avoidDirection;
                    state.avoidTicks = 30;
                }
                if (state.jumpCooldown <= 0) { bot.method_6043(); state.jumpCooldown = 10; }
                state.stuckTicks = 0;
            }
        } else {
            state.stuckTicks = 0;
        }
    }

    private static boolean isBlockSolid(class_1937 world, class_2338 pos) {
        class_2680 bs = world.method_8320(pos);
        return !bs.method_26215() && bs.method_26212(world, pos);
    }

    private static boolean isClimbable(class_1937 world, class_2338 pos) {
        class_2680 bs = world.method_8320(pos);
        return bs.method_26204() instanceof class_2399 || bs.method_26204() instanceof class_2541 ||
               bs.method_27852(class_2246.field_16492) || bs.method_27852(class_2246.field_23078) ||
               bs.method_27852(class_2246.field_23079) || bs.method_27852(class_2246.field_22123) ||
               bs.method_27852(class_2246.field_22124);
    }

    // === SMOOTH LOOKING ===

    private static float wrapDegrees(float angle) {
        angle %= 360.0F;
        if (angle >= 180.0F) angle -= 360.0F;
        if (angle < -180.0F) angle += 360.0F;
        return angle;
    }

    private static float smoothAngle(float current, float desired) {
        float maxRot = (float) BotSettings.get().getAimSpeed();
        float delta = wrapDegrees(desired - current);
        if (delta > maxRot) delta = maxRot;
        if (delta < -maxRot) delta = -maxRot;
        return current + delta;
    }

    public static void lookAt(class_3222 bot, class_1297 target) {
        class_243 targetPos = target.method_33571();
        class_243 botPos = bot.method_33571();
        double dx = targetPos.field_1352 - botPos.field_1352;
        double dy = targetPos.field_1351 - botPos.field_1351;
        double dz = targetPos.field_1350 - botPos.field_1350;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
        float pitch = (float) -(Math.atan2(dy, horiz) * (180.0 / Math.PI));
        bot.method_36456(smoothAngle(bot.method_36454(), yaw));
        bot.method_36457(smoothAngle(bot.method_36455(), pitch));
        bot.method_5847(smoothAngle(bot.method_5791(), yaw));
    }

    public static void lookAtPosition(class_3222 bot, class_243 targetPos) {
        class_243 botPos = bot.method_33571();
        double dx = targetPos.field_1352 - botPos.field_1352;
        double dy = targetPos.field_1351 - botPos.field_1351;
        double dz = targetPos.field_1350 - botPos.field_1350;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
        float pitch = (float) -(Math.atan2(dy, horiz) * (180.0 / Math.PI));
        bot.method_36456(smoothAngle(bot.method_36454(), yaw));
        bot.method_36457(smoothAngle(bot.method_36455(), pitch));
        bot.method_5847(smoothAngle(bot.method_5791(), yaw));
    }

    public static void lookAway(class_3222 bot, class_1297 target) {
        class_243 targetPos = target.method_33571();
        class_243 botPos = bot.method_33571();
        double dx = botPos.field_1352 - targetPos.field_1352;
        double dz = botPos.field_1350 - targetPos.field_1350;
        float yaw = (float) (Math.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
        bot.method_36456(smoothAngle(bot.method_36454(), yaw));
        bot.method_36457(smoothAngle(bot.method_36455(), 0));
        bot.method_5847(smoothAngle(bot.method_5791(), yaw));
    }

    public static void idleWander(class_3222 bot) {
        BotSettings settings = BotSettings.get();
        if (!settings.isIdleWanderEnabled()) return;
        NavigationState state = getState(bot.method_5477().getString());
        class_243 botPos = new class_243(bot.method_23317(), bot.method_23318(), bot.method_23321());
        if (state.spawnPosition == null) state.spawnPosition = botPos;
        if (state.jumpCooldown > 0) state.jumpCooldown--;
        if (state.avoidTicks > 0) state.avoidTicks--;
        if (state.wanderCooldown > 0) state.wanderCooldown--;
        double radius = settings.getIdleWanderRadius();
        if (state.wanderTarget == null || state.wanderCooldown <= 0 || botPos.method_1022(state.wanderTarget) < 1.5) {
            double angle = Math.random() * Math.PI * 2;
            double dist = Math.random() * radius;
            state.wanderTarget = new class_243(
                state.spawnPosition.field_1352 + Math.cos(angle) * dist,
                state.spawnPosition.field_1351,
                state.spawnPosition.field_1350 + Math.sin(angle) * dist
            );
            state.wanderCooldown = 60 + (int)(Math.random() * 100);
        }
        double dx = state.wanderTarget.field_1352 - botPos.field_1352;
        double dz = state.wanderTarget.field_1350 - botPos.field_1350;
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist > 0.5) {
            dx /= dist; dz /= dist;
            float yaw = (float) (Math.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
            bot.method_36456(smoothAngle(bot.method_36454(), yaw));
            bot.method_5847(smoothAngle(bot.method_5791(), yaw));
            bot.method_36457(0);
            bot.method_5728(false);
            bot.field_6250 = 0.5f;
            bot.method_5762(dx * 0.03, 0, dz * 0.03);
            var world = bot.method_51469();
            class_2338 feetPos = new class_2338((int) Math.floor(botPos.field_1352 + dx * 0.5), (int) Math.floor(botPos.field_1351), (int) Math.floor(botPos.field_1350 + dz * 0.5));
            if (isBlockSolid(world, feetPos) && !isBlockSolid(world, feetPos.method_10084()) && bot.method_24828() && state.jumpCooldown <= 0) {
                bot.method_6043();
                state.jumpCooldown = 10;
            }
        } else {
            bot.field_6250 = 0;
            bot.field_6212 = 0;
        }
        state.lastPosition = botPos;
    }

    public static void resetIdle(String botName) {
        NavigationState state = navStates.get(botName);
        if (state != null) {
            state.wanderTarget = null;
            state.wanderCooldown = 0;
            state.idleTicks = 0;
        }
    }
}

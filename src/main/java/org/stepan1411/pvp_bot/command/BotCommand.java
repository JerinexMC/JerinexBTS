package org.stepan1411.pvp_bot.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.Vec3d;
import org.stepan1411.pvp_bot.bot.*;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class BotCommand {

    private static final SuggestionProvider<ServerCommandSource> BOT_SUGGESTIONS =
        (ctx, builder) -> {
            String remaining = builder.getRemaining().toLowerCase(java.util.Locale.ROOT);
            for (String bot : BotManager.getAllBots()) {
                if (bot.toLowerCase(java.util.Locale.ROOT).contains(remaining)) {
                    builder.suggest(bot);
                }
            }
            return builder.buildFuture();
        };

    private static final SuggestionProvider<ServerCommandSource> KIT_SUGGESTIONS =
        (ctx, builder) -> {
            String remaining = builder.getRemaining().toLowerCase(java.util.Locale.ROOT);
            for (String kit : BotKits.getKitNames()) {
                if (kit.toLowerCase(java.util.Locale.ROOT).contains(remaining)) {
                    builder.suggest(kit);
                }
            }
            return builder.buildFuture();
        };

    private static final SuggestionProvider<ServerCommandSource> PLAYER_SUGGESTIONS =
        (ctx, builder) -> {
            String remaining = builder.getRemaining().toLowerCase(java.util.Locale.ROOT);
            for (var player : ctx.getSource().getServer().getPlayerManager().getPlayerList()) {
                String name = player.getName().getString();
                if (name.toLowerCase(java.util.Locale.ROOT).contains(remaining)) {
                    builder.suggest(name);
                }
            }
            return builder.buildFuture();
        };

    private static final SuggestionProvider<ServerCommandSource> PATH_SUGGESTIONS =
        (ctx, builder) -> {
            String remaining = builder.getRemaining().toLowerCase(java.util.Locale.ROOT);
            for (String pathName : BotPath.getAllPaths().keySet()) {
                if (pathName.toLowerCase(java.util.Locale.ROOT).contains(remaining)) {
                    builder.suggest(pathName);
                }
            }
            return builder.buildFuture();
        };

    private static final SuggestionProvider<ServerCommandSource> FACTION_SUGGESTIONS =
        (ctx, builder) -> {
            String remaining = builder.getRemaining().toLowerCase(java.util.Locale.ROOT);
            for (String f : BotFaction.getAllFactions()) {
                if (f.toLowerCase(java.util.Locale.ROOT).contains(remaining)) {
                    builder.suggest(f);
                }
            }
            return builder.buildFuture();
        };

    private static final SuggestionProvider<ServerCommandSource> RANDOM_KIT_SUGGESTIONS =
        (ctx, builder) -> {
            String remaining = builder.getRemaining();
            int tokenCount;
            String partial;
            if (remaining.isEmpty()) {
                tokenCount = 0;
                partial = "";
            } else {
                int lastSpace = remaining.lastIndexOf(' ');
                if (lastSpace >= 0) {
                    partial = remaining.substring(lastSpace + 1);
                    tokenCount = remaining.substring(0, lastSpace).split(" ", -1).length;
                    if (tokenCount == 1 && remaining.substring(0, lastSpace).isEmpty()) tokenCount = 0;
                } else {
                    partial = remaining;
                    tokenCount = 0;
                }
            }
            var wordBuilder = builder.createOffset(builder.getStart() + remaining.length() - partial.length());
            if (tokenCount % 2 == 1) {
                for (int i = 1; i <= 100; i++) {
                    wordBuilder.suggest(i + "%");
                }
            } else {
                for (String kit : BotKits.getKitNames()) {
                    if (kit.regionMatches(true, 0, partial, 0, partial.length())) {
                        wordBuilder.suggest(kit);
                    }
                }
            }
            return wordBuilder.buildFuture();
        };

    private static LiteralArgumentBuilder<ServerCommandSource> cmd(String literal, String description) {
        return CommandManager.literal(literal)
            .executes(ctx -> {
                ctx.getSource().sendFeedback(() -> Text.literal("§7[Tip] §a" + description), false);
                return 1;
            });
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(cmd("pvpbot", "PvP Bot control")
            .requires(s -> true)
            .then(CommandManager.literal("spawn")
                .executes(ctx -> spawn(ctx, null))
                .then(CommandManager.argument("name", StringArgumentType.word())
                    .executes(ctx -> spawn(ctx, StringArgumentType.getString(ctx, "name")))))
            .then(cmd("remove", "Remove a bot")
                .then(CommandManager.argument("name", StringArgumentType.word())
                    .suggests(BOT_SUGGESTIONS)
                    .executes(BotCommand::remove)))
            .then(CommandManager.literal("reload").executes(BotCommand::reload))
            .then(CommandManager.literal("removeall").executes(BotCommand::removeAll))
            .then(buildSettings())
        );
    }

    private static LiteralArgumentBuilder<ServerCommandSource> buildSettings() {
        var settings = CommandManager.literal("settings").executes(BotCommand::settings);
        settings.then(boolSetting("combat", "Enable combat AI", () -> BotSettings.get().isCombatEnabled(), v -> BotSettings.get().setCombatEnabled(v), BotSettings.DEFAULTS::isCombatEnabled));
        settings.then(boolSetting("shield-break", "Auto-axe enemy shield", () -> BotSettings.get().isShieldBreakEnabled(), v -> BotSettings.get().setShieldBreakEnabled(v), BotSettings.DEFAULTS::isShieldBreakEnabled));
        settings.then(intSetting("shield-break-chance", "Shield break attempt chance (%)", () -> BotSettings.get().getShieldBreakChance(), v -> BotSettings.get().setShieldBreakChance(v), 0, 100, BotSettings.DEFAULTS::getShieldBreakChance));
        settings.then(boolSetting("stun", "Follow up axe shield hit with stun swing after 2 ticks", () -> BotSettings.get().isStunEnabled(), v -> BotSettings.get().setStunEnabled(v), BotSettings.DEFAULTS::isStunEnabled));
        settings.then(intSetting("stun-chance", "Stun follow-up chance (1-100%)", () -> BotSettings.get().getStunChance(), v -> BotSettings.get().setStunChance(v), 1, 100, BotSettings.DEFAULTS::getStunChance));
        settings.then(boolSetting("cobweb", "Use cobwebs in combat", () -> BotSettings.get().isCobwebEnabled(), v -> BotSettings.get().setCobwebEnabled(v), BotSettings.DEFAULTS::isCobwebEnabled));
        return settings;
    }

    private static LiteralArgumentBuilder<ServerCommandSource> boolSetting(String name, String desc, BooleanSupplier getter, Consumer<Boolean> setter, BooleanSupplier defaultGetter) {
        return CommandManager.literal(name)
            .executes(ctx -> {
                ctx.getSource().sendFeedback(() -> Text.literal("§7[Tip] §a" + desc + " §7(current: " + getter.getAsBoolean() + ", default: " + defaultGetter.getAsBoolean() + ")"), false);
                return 1;
            })
            .then(CommandManager.argument("value", BoolArgumentType.bool())
                .executes(ctx -> {
                    boolean value = BoolArgumentType.getBool(ctx, "value");
                    setter.accept(value);
                    ctx.getSource().sendFeedback(() -> Text.literal(name + ": " + value), true);
                    return 1;
                }));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> intSetting(String name, String desc, java.util.function.IntSupplier getter, java.util.function.IntConsumer setter, int min, int max, java.util.function.IntSupplier defaultGetter) {
        return CommandManager.literal(name)
            .executes(ctx -> {
                ctx.getSource().sendFeedback(() -> Text.literal("§7[Tip] §a" + desc + " §7(current: " + getter.getAsInt() + ", default: " + defaultGetter.getAsInt() + ")"), false);
                return 1;
            })
            .then(CommandManager.argument("value", IntegerArgumentType.integer(min, max))
                .executes(ctx -> {
                    int value = IntegerArgumentType.getInteger(ctx, "value");
                    setter.accept(value);
                    ctx.getSource().sendFeedback(() -> Text.literal(name + ": " + value), true);
                    return 1;
                }));
    }

    private static int spawn(CommandContext<ServerCommandSource> ctx, String name) {
        String botName = name != null ? name : BotNameGenerator.generateUniqueName();
        if (BotManager.spawnBot(ctx.getSource().getServer(), botName, ctx.getSource())) {
            ctx.getSource().sendFeedback(() -> Text.literal("PvP Bot '" + botName + "' spawned!"), true);
            return 1;
        }
        return 0;
    }

    private static int remove(CommandContext<ServerCommandSource> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        if (BotManager.removeBot(ctx.getSource().getServer(), name, ctx.getSource())) {
            ctx.getSource().sendFeedback(() -> Text.literal("Bot '" + name + "' removed!"), true);
            return 1;
        }
        return 0;
    }

    private static int removeAll(CommandContext<ServerCommandSource> ctx) {
        int count = BotManager.getBotCount();
        BotManager.removeAllBots(ctx.getSource().getServer(), ctx.getSource());
        return count;
    }

    private static int reload(CommandContext<ServerCommandSource> ctx) {
        BotSettings.load();
        BotKits.reload(ctx.getSource().getServer());
        BotPath.init();
        BotManager.reloadBots();
        ctx.getSource().sendFeedback(() -> Text.literal("All configurations reloaded!"), true);
        return 1;
    }

    private static int settings(CommandContext<ServerCommandSource> ctx) {
        BotSettings s = BotSettings.get();
        ctx.getSource().sendFeedback(() -> Text.literal("combat: " + s.isCombatEnabled() + " | shield-break: " + s.isShieldBreakEnabled() + " (" + s.getShieldBreakChance() + "%) | stun: " + s.isStunEnabled() + " (" + s.getStunChance() + "%)"), false);
        return 1;
    }
}

package com.example.homesandbacks.command;

import com.example.homesandbacks.data.HomeLocation;
import com.example.homesandbacks.data.HomeManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class HomeCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        // /sethome [name]  — default name is "home"
        dispatcher.register(CommandManager.literal("sethome")
            .requires(src -> src.isExecutedByPlayer())
            .executes(ctx -> executeSetHome(ctx, "home"))
            .then(CommandManager.argument("name", StringArgumentType.word())
                .executes(ctx -> executeSetHome(ctx, StringArgumentType.getString(ctx, "name"))))
        );

        // /home [name]
        dispatcher.register(CommandManager.literal("home")
            .requires(src -> src.isExecutedByPlayer())
            .executes(ctx -> executeHome(ctx, "home"))
            .then(CommandManager.argument("name", StringArgumentType.word())
                .executes(ctx -> executeHome(ctx, StringArgumentType.getString(ctx, "name"))))
        );

        // /delhome [name]
        dispatcher.register(CommandManager.literal("delhome")
            .requires(src -> src.isExecutedByPlayer())
            .executes(ctx -> executeDelHome(ctx, "home"))
            .then(CommandManager.argument("name", StringArgumentType.word())
                .executes(ctx -> executeDelHome(ctx, StringArgumentType.getString(ctx, "name"))))
        );

        // /homes  — list all homes
        dispatcher.register(CommandManager.literal("homes")
            .requires(src -> src.isExecutedByPlayer())
            .executes(HomeCommand::executeListHomes)
        );

        // /back  — return to last location before a teleport
        dispatcher.register(CommandManager.literal("back")
            .requires(src -> src.isExecutedByPlayer())
            .executes(HomeCommand::executeBack)
        );
    }

    private static int executeSetHome(CommandContext<ServerCommandSource> ctx, String name) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        Vec3d pos = player.getPos();
        String worldKey = player.getWorld().getRegistryKey().getValue().toString();
        HomeManager.INSTANCE.setHome(player.getUuid(), worldKey, name, new HomeLocation(
            worldKey,
            pos.x, pos.y, pos.z,
            player.getYaw(), player.getPitch()
        ));
        ctx.getSource().sendFeedback(() -> Text.literal("§aHome '" + name + "' set!"), false);
        return 1;
    }

    private static int executeHome(CommandContext<ServerCommandSource> ctx, String name) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        String worldKey = player.getWorld().getRegistryKey().getValue().toString();
        Optional<HomeLocation> homeOpt = HomeManager.INSTANCE.getHome(player.getUuid(), worldKey, name);
        if (homeOpt.isEmpty()) {
            ctx.getSource().sendFeedback(() ->
                Text.literal("§cHome '" + name + "' not found. Use §f/sethome " + name + "§c to set it."), false);
            return 0;
        }

        HomeLocation home = homeOpt.get();
        ServerWorld targetWorld = resolveWorld(player, home.world);
        if (targetWorld == null) {
            ctx.getSource().sendFeedback(() -> Text.literal("§cCould not find world: " + home.world), false);
            return 0;
        }

        saveBack(player);
        doTeleport(player, targetWorld, home.x, home.y, home.z, home.yaw, home.pitch);
        ctx.getSource().sendFeedback(() -> Text.literal("§aTeleported to home '" + name + "'!"), false);
        return 1;
    }

    private static int executeDelHome(CommandContext<ServerCommandSource> ctx, String name) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        String worldKey = player.getWorld().getRegistryKey().getValue().toString();
        if (HomeManager.INSTANCE.deleteHome(player.getUuid(), worldKey, name)) {
            ctx.getSource().sendFeedback(() -> Text.literal("§aHome '" + name + "' deleted."), false);
            return 1;
        }
        ctx.getSource().sendFeedback(() -> Text.literal("§cHome '" + name + "' not found."), false);
        return 0;
    }

    private static int executeListHomes(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        String worldKey = player.getWorld().getRegistryKey().getValue().toString();
        Map<String, HomeLocation> playerHomes = HomeManager.INSTANCE.getHomes(player.getUuid(), worldKey);
        if (playerHomes.isEmpty()) {
            ctx.getSource().sendFeedback(() ->
                Text.literal("§eNo homes set. Use §f/sethome§e to set one."), false);
        } else {
            ctx.getSource().sendFeedback(() ->
                Text.literal("§6Homes: §f" + String.join("§7, §f", playerHomes.keySet())), false);
        }
        return 1;
    }

    private static int executeBack(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) return 0;

        Optional<HomeLocation> backOpt = HomeManager.INSTANCE.getBack(player.getUuid());
        if (backOpt.isEmpty()) {
            ctx.getSource().sendFeedback(() -> Text.literal("§cNo previous location to return to."), false);
            return 0;
        }

        HomeLocation back = backOpt.get();
        ServerWorld targetWorld = resolveWorld(player, back.world);
        if (targetWorld == null) {
            ctx.getSource().sendFeedback(() -> Text.literal("§cCould not find world: " + back.world), false);
            return 0;
        }

        // Save current position so /back can toggle between the two spots
        saveBack(player);
        doTeleport(player, targetWorld, back.x, back.y, back.z, back.yaw, back.pitch);
        ctx.getSource().sendFeedback(() -> Text.literal("§aTeleported to previous location!"), false);
        return 1;
    }

    private static void saveBack(ServerPlayerEntity player) {
        Vec3d pos = player.getPos();
        HomeManager.INSTANCE.setBack(player.getUuid(), new HomeLocation(
            player.getWorld().getRegistryKey().getValue().toString(),
            pos.x, pos.y, pos.z,
            player.getYaw(), player.getPitch()
        ));
    }

    private static ServerWorld resolveWorld(ServerPlayerEntity player, String worldKey) {
        try {
            String[] parts = worldKey.split(":", 2);
            Identifier id = parts.length == 2
                ? Identifier.of(parts[0], parts[1])
                : Identifier.of("minecraft", parts[0]);
            RegistryKey<World> key = RegistryKey.of(RegistryKeys.WORLD, id);
            return player.getServer().getWorld(key);
        } catch (Exception e) {
            return null;
        }
    }

    private static void doTeleport(ServerPlayerEntity player, ServerWorld world,
                                   double x, double y, double z, float yaw, float pitch) {
        player.teleport(world, x, y, z, Set.of(), yaw, pitch, true);
    }
}

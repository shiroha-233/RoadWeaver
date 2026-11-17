package net.shiroha233.roadweaver.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.server.players.NameAndId;
import net.shiroha233.roadweaver.map.permission.MapAccessMode;
import net.shiroha233.roadweaver.map.permission.MapAccessPolicy;
import net.shiroha233.roadweaver.map.permission.MapAccessService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class MapAccessCommand {
    private MapAccessCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("roadweaver")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .then(Commands.literal("mapaccess")
                        .then(Commands.literal("info")
                                .executes(context -> info(context.getSource())))
                        .then(Commands.literal("mode")
                                .then(Commands.literal(MapAccessMode.ALL_PLAYERS.serializedName())
                                        .executes(context -> setMode(context.getSource(), MapAccessMode.ALL_PLAYERS)))
                                .then(Commands.literal(MapAccessMode.OPS_ONLY.serializedName())
                                        .executes(context -> setMode(context.getSource(), MapAccessMode.OPS_ONLY))))
                        .then(Commands.literal("allow")
                                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                        .executes(context -> allow(context.getSource(), GameProfileArgument.getGameProfiles(context, "player")))))
                        .then(Commands.literal("deny")
                                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                        .executes(context -> deny(context.getSource(), GameProfileArgument.getGameProfiles(context, "player")))))));
    }

    private static int info(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        MapAccessPolicy policy = MapAccessService.getPolicy(server);
        source.sendSuccess(() -> Component.translatable("command.roadweaver.mapaccess.info.mode", modeComponent(policy.mode())), false);

        List<String> allowedNames = policy.allowedPlayers().stream()
                .map(uuid -> resolvePlayerName(server, uuid))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        if (allowedNames.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.roadweaver.mapaccess.info.allowed.none"), false);
        } else {
            source.sendSuccess(() -> Component.translatable("command.roadweaver.mapaccess.info.allowed.list", String.join(", ", allowedNames)), false);
        }
        return 1;
    }

    private static int setMode(CommandSourceStack source, MapAccessMode mode) {
        boolean changed = MapAccessService.setMode(source.getServer(), mode);
        Component feedback = changed
                ? Component.translatable("command.roadweaver.mapaccess.mode.set", modeComponent(mode))
                : Component.translatable("command.roadweaver.mapaccess.mode.unchanged", modeComponent(mode));
        source.sendSuccess(() -> feedback, false);
        return changed ? 1 : 0;
    }

    private static int allow(CommandSourceStack source, Collection<NameAndId> profiles) {
        MinecraftServer server = source.getServer();
        List<String> names = new ArrayList<>();
        int changed = 0;
        for (NameAndId profile : profiles) {
            UUID playerId = profile.id();
            if (playerId == null) {
                continue;
            }
            names.add(displayName(profile, playerId));
            if (MapAccessService.grant(server, playerId)) {
                changed++;
            }
        }

        String joined = joinNames(names);
        if (changed > 0) {
            source.sendSuccess(() -> Component.translatable("command.roadweaver.mapaccess.allow.added", joined), false);
        } else {
            source.sendFailure(Component.translatable("command.roadweaver.mapaccess.allow.unchanged", joined));
        }
        return changed;
    }

    private static int deny(CommandSourceStack source, Collection<NameAndId> profiles) {
        MinecraftServer server = source.getServer();
        List<String> names = new ArrayList<>();
        int changed = 0;
        for (NameAndId profile : profiles) {
            UUID playerId = profile.id();
            if (playerId == null) {
                continue;
            }
            names.add(displayName(profile, playerId));
            if (MapAccessService.revoke(server, playerId)) {
                changed++;
            }
        }

        String joined = joinNames(names);
        if (changed > 0) {
            source.sendSuccess(() -> Component.translatable("command.roadweaver.mapaccess.deny.removed", joined), false);
        } else {
            source.sendFailure(Component.translatable("command.roadweaver.mapaccess.deny.unchanged", joined));
        }
        return changed;
    }

    private static Component modeComponent(MapAccessMode mode) {
        return Component.translatable("command.roadweaver.mapaccess.mode." + mode.serializedName());
    }

    private static String displayName(NameAndId profile, UUID playerId) {
        if (profile.name() != null && !profile.name().isBlank()) {
            return profile.name();
        }
        return playerId.toString();
    }

    private static String resolvePlayerName(MinecraftServer server, UUID playerId) {
        ServerPlayer online = server.getPlayerList().getPlayer(playerId);
        if (online != null) {
            return online.getGameProfile().name();
        }
        Optional<GameProfile> resolved = server.services().profileResolver().fetchById(playerId);
        if (resolved.isPresent() && resolved.get().name() != null && !resolved.get().name().isBlank()) {
            return resolved.get().name();
        }
        return playerId.toString();
    }

    private static String joinNames(List<String> names) {
        return names.stream()
                .filter(name -> name != null && !name.isBlank())
                .sorted(Comparator.naturalOrder())
                .reduce((left, right) -> left + ", " + right)
                .orElse("-");
    }
}

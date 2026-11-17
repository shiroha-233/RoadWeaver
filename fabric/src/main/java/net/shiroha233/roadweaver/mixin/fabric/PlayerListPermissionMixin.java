package net.shiroha233.roadweaver.mixin.fabric;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;
import net.shiroha233.roadweaver.map.permission.MapAccessPlatformBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(PlayerList.class)
public abstract class PlayerListPermissionMixin {
    @Shadow
    public abstract ServerPlayer getPlayer(UUID playerId);

    @Inject(method = "op", at = @At("TAIL"))
    private void roadweaver$syncMapAccessOnOp(NameAndId profile, CallbackInfo ci) {
        sync(profile);
    }

    @Inject(method = "deop", at = @At("TAIL"))
    private void roadweaver$syncMapAccessOnDeop(NameAndId profile, CallbackInfo ci) {
        sync(profile);
    }

    private void sync(NameAndId profile) {
        if (profile == null || profile.id() == null) {
            return;
        }
        ServerPlayer player = getPlayer(profile.id());
        if (player != null) {
            MapAccessPlatformBridge.syncPlayer(player);
        }
    }
}

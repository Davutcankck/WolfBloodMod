package com.example.mixin;

import com.example.WerewolfScale;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public class PlayerWerewolfSizeMixin {

    @Inject(method = "getDimensions", at = @At("RETURN"), cancellable = true)
    private void wolfblood$boyut(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        Player player = (Player) (Object) this;
        if (!WerewolfScale.aktifMi(player)) return;

        EntityDimensions current = cir.getReturnValue();
        // Sadece yüksekliği değiştirerek, engebeli yerlerde veya kapılarda takılmayı önlüyoruz.
        cir.setReturnValue(EntityDimensions.scalable(current.width, current.height * WerewolfScale.OLCEK));
    }

    @Inject(method = "getStandingEyeHeight", at = @At("RETURN"), cancellable = true)
    private void wolfblood$gozYuksekligi(Pose pose, EntityDimensions dimensions, CallbackInfoReturnable<Float> cir) {
        Player player = (Player) (Object) this;
        if (!WerewolfScale.aktifMi(player)) return;

        cir.setReturnValue(cir.getReturnValue() * WerewolfScale.OLCEK);
    }
}
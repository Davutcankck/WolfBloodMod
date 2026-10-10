package com.example.client.mixin;

import com.example.WerewolfScale;
import com.example.client.WerewolfClawsLayer;
import com.example.client.WerewolfEarsLayer;
import com.example.client.WerewolfEyesLayer;
import com.example.client.WolfClientState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererWerewolfScaleMixin extends LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    protected PlayerRendererWerewolfScaleMixin(EntityRendererProvider.Context context, PlayerModel<AbstractClientPlayer> model, float shadowRadius) {
        super(context, model, shadowRadius);
    }

    @Inject(method = "scale", at = @At("TAIL"))
    private void wolfblood$olcek(AbstractClientPlayer player, PoseStack poseStack, float partialTick, CallbackInfo ci) {
        float ilerleme = WolfClientState.getIlerleme(player.getUUID(), partialTick);
        if (ilerleme <= 0f) {
            return;
        }

        float hedefOlcek = WerewolfScale.OLCEK;
        float uygulananOlcek = Mth.lerp(ilerleme, 1f, hedefOlcek);

        poseStack.scale(uygulananOlcek, uygulananOlcek, uygulananOlcek);
    }

    // addLayer protected; mixin LivingEntityRenderer alt sınıfı olduğu için doğrudan çağrılabilir.
    // Named-mapping reflection jar remap'inde kırılıyordu.
    @Inject(method = "<init>", at = @At("TAIL"))
    private void wolfblood$layerEkle(EntityRendererProvider.Context context, boolean slim, CallbackInfo ci) {
        PlayerRenderer self = (PlayerRenderer) (Object) this;
        this.addLayer(new WerewolfEyesLayer(self));
        this.addLayer(new WerewolfEarsLayer(self, context.getModelSet()));
        this.addLayer(new WerewolfClawsLayer(self, context.getModelSet()));
    }
}

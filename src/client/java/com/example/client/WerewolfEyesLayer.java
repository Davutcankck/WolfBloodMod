package com.example.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

// Dönüşmüş oyuncunun kafasına lycan_eyes.png'yi (skin UV) emissive olarak basar.
// Form açılırken/kapanırken WolfClientState'teki "ilerleme" değerine göre ALPHA ile
// yumuşakça belirip kayboluyor - bu artık WolfClientState.getIlerleme() içindeki
// fallback düzeltmesi sayesinde çalışıyor, RenderType'a dokunmaya gerek yok.
//
// NOT: RenderType.eyes(...) additive (katmalı) blend kullanıyor - bu YÜZDEN karanlıkta
// gözler daha belirgin/parlak, aydınlıkta ortam rengiyle karışıp daha soluk/pembemsi
// görünüyor. Bu İSTENEN görsel davranış - entityTranslucentEmissive'e geçmek bu glow
// efektini kaybettiriyordu, bu yüzden eyes()'e geri dönüldü.
public class WerewolfEyesLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    private static final ResourceLocation GOZ_TEXTURE =
            new ResourceLocation("wolfblood", "textures/entity/lycan_eyes.png");

    public WerewolfEyesLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> context) {
        super(context);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                       AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {

        // render() zaten partialTicks veriyor - Minecraft.getInstance().getFrameTime() hack'ine
        // gerek yok, doğrudan bunu kullanıyoruz.
        float ilerleme = WolfClientState.getIlerleme(player.getUUID(), partialTicks);
        if (ilerleme <= 0f) {
            return; // insan formunda, göz efekti yok
        }
        if (player.isInvisible()) {
            return;
        }


        PlayerModel<AbstractClientPlayer> model = getParentModel();
        VertexConsumer consumer = buffer.getBuffer(RenderType.eyes(GOZ_TEXTURE));
        int isik = LightTexture.FULL_BRIGHT;

        // alpha = ilerleme: dönüşüm ilerledikçe gözler yavaşça beliriyor, tam forma geçince
        // tam parlaklığa ulaşıyor. RGB'yi 1'de tutuyoruz, rengi zaten texture belirliyor.
        model.head.render(poseStack, consumer, isik, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, ilerleme);
        model.hat.render(poseStack, consumer, isik, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, ilerleme);
    }
}
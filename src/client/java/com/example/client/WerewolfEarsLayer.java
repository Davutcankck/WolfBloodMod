package com.example.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

// Dönüşmüş oyuncunun kafasına kulak modelini çizer. WolfClientState'teki AYNI "ilerleme"
// değerini kullanır (getIlerleme) - böylece vücut/göz/kulak hep senkron büyür-küçülür.
public class WerewolfEarsLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    private static final ResourceLocation KULAK_TEXTURE =
            new ResourceLocation("wolfblood", "textures/entity/lycan_kulak.png");

    private final LycanKulakModel<AbstractClientPlayer> kulakModel;

    public WerewolfEarsLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> context,
                              EntityModelSet modelSet) {
        super(context);
        this.kulakModel = new LycanKulakModel<>(modelSet.bakeLayer(WolfModelLayers.LYCAN_KULAK));
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                        AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                        float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {

        float ilerleme = WolfClientState.getIlerleme(player.getUUID(), partialTicks);
        if (ilerleme <= 0f || player.isInvisible()) {
            return;
        }

        PlayerModel<AbstractClientPlayer> oyuncuModeli = getParentModel();

        poseStack.pushPose();
        // Kulakları oyuncunun kafasıyla AYNI koordinat uzayına (aynı yaw/pitch) hizala.
        // Vanilla "head" pivot'u (0,0,0) olduğu için burada sadece rotasyon eklenmiş olur,
        // konum kaymaz - bu yüzden Blockbench'teki pivot değerlerini (örn. y=7) hiçbir ekstra
        // ofset hesaplamadan doğrudan kullanabiliyoruz.
        oyuncuModeli.head.translateAndRotate(poseStack);

        // Büyüme animasyonu: her bone KENDİ pivot'undan (kulağın tabanından) ölçekleniyor,
        // çünkü ModelPart.render() scale'i kendi pivot'una göre uyguluyor. Blockbench'te
        // pivot'u tabana koyduğun için bu satır otomatik doğru yönde büyüyecek şekilde çalışıyor.
        kulakModel.bone.xScale = kulakModel.bone.yScale = kulakModel.bone.zScale = ilerleme;
        kulakModel.bone2.xScale = kulakModel.bone2.yScale = kulakModel.bone2.zScale = ilerleme;

        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(KULAK_TEXTURE));
        kulakModel.bone.render(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY);
        kulakModel.bone2.render(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY);

        poseStack.popPose();
    }
}

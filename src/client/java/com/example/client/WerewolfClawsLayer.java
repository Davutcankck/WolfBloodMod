package com.example.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

// Dönüşmüş oyuncunun ellerine kemik rengi bıçak pençeleri çizer. WerewolfEarsLayer ile AYNI "ilerleme"
// değerini (WolfClientState.getIlerleme) kullanır: dönüşürken pençeler elden büyüyerek çıkar.
// Yerleşim ve şekil ayarları LycanPencelerModel'in en üstündeki sabitlerde.
//
// Parlama (istersen aç: TAM_PARLAK / HALE_VAR) iki katmanla yapılıyor (gerçek bloom yok, ona yaklaşık bir efekt):
//  1) Gövde: tam parlaklıkta (karanlıkta bile ışır) normal çizim
//  2) Hale: aynı model biraz büyütülmüş, eklemeli (additive) yarı saydam çizim, sadece parlak kısımlar belli olur
public class WerewolfClawsLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    private static final ResourceLocation PENCE_TEXTURE =
            new ResourceLocation("wolfblood", "textures/entity/lycan_pencesi.png");

    // Genel boyut çarpanı. 1.0 = modelde tanımlı boyut. Hepsini birden büyütüp küçültmek için buradan oyna.
    private static final float PENCE_OLCEGI = 0.6F;

    // Parlama ayarları
    private static final boolean TAM_PARLAK = false;    // true: karanlıkta da parlar. false: normal ışıkla gölgelenir
    private static final boolean HALE_VAR = false;      // false yaparsan sadece gövde çizilir (halesiz)
    private static final float HALE_BUYUKLUK = 1.18F;   // haleyi ne kadar büyüteceği
    private static final float HALE_SAYDAMLIK = 0.35F;  // 0 = görünmez, 1 = tam parlak

    private final LycanPencelerModel<AbstractClientPlayer> pencelerModel;

    public WerewolfClawsLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> context,
                              EntityModelSet modelSet) {
        super(context);
        this.pencelerModel = new LycanPencelerModel<>(modelSet.bakeLayer(WolfModelLayers.LYCAN_PENCE));
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
        int isik = TAM_PARLAK ? LightTexture.FULL_BRIGHT : packedLight;
        float olcek = ilerleme * PENCE_OLCEGI;

        // 1) Gövde
        olcekle(olcek);
        VertexConsumer govde = buffer.getBuffer(RenderType.entityCutoutNoCull(PENCE_TEXTURE));
        elleriCiz(poseStack, oyuncuModeli, govde, isik, 1.0F);

        // 2) Hale (eklemeli, biraz büyütülmüş, yarı saydam)
        if (HALE_VAR) {
            olcekle(olcek * HALE_BUYUKLUK);
            VertexConsumer hale = buffer.getBuffer(RenderType.eyes(PENCE_TEXTURE));
            elleriCiz(poseStack, oyuncuModeli, hale, LightTexture.FULL_BRIGHT, HALE_SAYDAMLIK * ilerleme);
        }
    }

    private void elleriCiz(PoseStack poseStack, PlayerModel<AbstractClientPlayer> oyuncuModeli,
                           VertexConsumer consumer, int isik, float alfa) {
        // Sağ el: kolun omuz pivot'una git (kolun o anki sallanma açısı dahil), pençeleri çiz
        poseStack.pushPose();
        oyuncuModeli.rightArm.translateAndRotate(poseStack);
        pencelerModel.sagPence.render(poseStack, consumer, isik, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, alfa);
        poseStack.popPose();

        // Sol el
        poseStack.pushPose();
        oyuncuModeli.leftArm.translateAndRotate(poseStack);
        pencelerModel.solPence.render(poseStack, consumer, isik, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, alfa);
        poseStack.popPose();
    }

    private void olcekle(float olcek) {
        ayarla(pencelerModel.sagPence, olcek);
        ayarla(pencelerModel.solPence, olcek);
    }

    private static void ayarla(ModelPart parca, float olcek) {
        parca.xScale = olcek;
        parca.yScale = olcek;
        parca.zScale = olcek;
    }
}
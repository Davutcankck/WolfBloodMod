package com.example.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

// Dönüşmüş oyuncunun ellerine Blockbench'te yaptığın pençe modelini (LycanPencelerModel) takar.
// WerewolfEarsLayer ile AYNI "ilerleme" değerini (WolfClientState.getIlerleme) kullanır:
// dönüşürken pençeler elden büyüyerek çıkar.
//
// Blockbench'te model, kolun ucuna doğru UZANAN bir el olarak çizilmişti (parmaklar -Z yönünde).
// Oyunda kol AŞAĞI sarkıyor, bu yüzden model önce 90 derece döndürülüp elin alt kenarına oturtuluyor.
public class WerewolfClawsLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    private static final ResourceLocation PENCE_TEXTURE =
            new ResourceLocation("wolfblood", "textures/entity/lycan_pencesi.png");

    // ============ YERLEŞİM AYARLARI (oyunda bakıp buradan oynayabilirsin) ============

    // Oyundaki kol (klasik Steve kolu): kol pivot'una göre elin alt kenarı y = 10, kolun yatay merkezi ±1.
    private static final float EL_ALT_Y = 10.0F;
    private static final float KOL_MERKEZ_X = 1.0F;   // slim (Alex) kol için 0.5 dene

    // Blockbench modelinde, parmakların bağlandığı noktanın (kolun kesit merkezi ve bittiği yer) koordinatları.
    //  X: parmaklar x -0.9...3.0 arası -> merkez 1.05
    //  Y: parmak sırası kolun ÜST yüzeyine yaslı çizilmiş (üst yüzü y = 12.2). 4 birim kalın kolun kesit merkezi
    //     bu yüzden 12.2 - 2 = 10.2 -> dışa aktarma uzayında 24 - 10.2 = 13.8. (Eskiden parmak sırasının ortasını
    //     (12.3) almıştım, bu pençeleri kolun tam ortasına koyuyordu. Şimdi parmaklar kolun ön yüzüne yaslı çıkar.)
    //  Z: kol z = -2'de bitiyor.
    private static final float MODEL_EL_X = 1.05F;
    private static final float MODEL_EL_Y = 13.8F;
    private static final float MODEL_EL_Z = -2.0F;

    // Model SOL kol düzeninde çizilmiş (başparmak -X yanında). true: sol kola olduğu gibi, sağ kola
    // AYNALANARAK takılır. Başparmaklar dışarıda kalıyorsa bunu false yap (iki elin rolü yer değiştirir).
    private static final boolean MODEL_SOL_KOL_ICIN = true;

    // Ek ince ayar (birim = model pikseli). UZANTI_KAYMA: + değer pençeleri AŞAĞI (elden uzağa) indirir.
    // DERINLIK_KAYMA: + değer pençeleri İLERİ (oyuncunun önüne) kaydırır, - değer geriye.
    private static final float UZANTI_KAYMA = -1.5F;   // eksi = modeli kola doğru (yukarı) çeker: et blokları kolun içinde kalır
    private static final float DERINLIK_KAYMA = 0.0F;

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
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(PENCE_TEXTURE));

        elCiz(poseStack, oyuncuModeli.rightArm, true, ilerleme, consumer, packedLight);
        elCiz(poseStack, oyuncuModeli.leftArm, false, ilerleme, consumer, packedLight);
    }

    private void elCiz(PoseStack ps, ModelPart kol, boolean sagEl, float ilerleme,
                       VertexConsumer consumer, int packedLight) {
        ps.pushPose();

        // 1) Kolun omuz pivot'una git (kolun o anki sallanma açısı dahil)
        kol.translateAndRotate(ps);

        // 2) Elin alt kenarının ortasına git (sağ kol -X, sol kol +X tarafında)
        ps.translate((sagEl ? -KOL_MERKEZ_X : KOL_MERKEZ_X) / 16.0F, EL_ALT_Y / 16.0F, 0.0F);

        // 3) Sağ el için modeli X ekseninde aynala (negatif ölçek, NoCull olduğu için yüzler kaybolmaz)
        if (sagEl == MODEL_SOL_KOL_ICIN) {
            ps.scale(-1.0F, 1.0F, 1.0F);
        }

        // 4) Modelin "ileri" (-Z) yönü kolun "aşağı" (+Y) yönü olsun: X ekseninde +90 derece
        ps.mulPose(Axis.XP.rotationDegrees(90.0F));

        // 5) Büyüme animasyonu: tek tip ölçek, elin alt kenarı merkez (pençeler elden çıkıyor gibi)
        ps.scale(ilerleme, ilerleme, ilerleme);

        // 6) Modelin bağlanma noktasını (kolun bittiği yer) bu merkeze oturt
        // (döndürme sonrası modelin +Y'si dünyada geriye, +Z'si yukarıya bakar, o yüzden kaymalar eksi işaretli)
        ps.translate(-MODEL_EL_X / 16.0F, (-MODEL_EL_Y - DERINLIK_KAYMA) / 16.0F,
                (-MODEL_EL_Z - UZANTI_KAYMA) / 16.0F);

        pencelerModel.kok.render(ps, consumer, packedLight, OverlayTexture.NO_OVERLAY);

        ps.popPose();
    }
}
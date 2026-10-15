package com.example.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.world.entity.Entity;

// Kurt adam pençeleri: her elde 3 kavisli TIRNAK pençe (elin ön kenarında yan yana) + yanda yatık 1 başparmak pençesi.
//
// Her pençe, birbirinin ÇOCUĞU olan 7 segmentten oluşur: segmentler X'te geniş (tırnağın eni), Z'de ince (kalınlık);
// en uca doğru daralır (sivrilir), her segment bir öncekine göre daha fazla döner (kıvrım). Dönüşler birikir,
// pürüzsüz, parmak gibi kıvrılan bir tırnak çıkar. Boyutlar ondalık: kodda 1 piksel sınırı yok.
//
// Koordinatlar KOLUN kendi pivot'una (omuz) göre. Klasik kol kutusu y = -2 ... +10, yani elin alt kenarı y = 10.
// Ön yüz -Z yönünde (kolun ön yüzü z = -2).
//
// sagPence / solPence PUBLIC: büyüme animasyonu için layer xScale/yScale/zScale ayarlıyor.
public class LycanPencelerModel<T extends Entity> extends EntityModel<T> {

    // ============ AYARLAR (oyunda bakıp buradan oynayabilirsin) ============

    // Yerleşim
    private static final float EL_ALT_Y = 10.0F;        // elin alt kenarı (kol pivot'una göre)
    private static final float KOL_MERKEZ_X = 1.0F;     // klasik kol: ±1. Slim (Alex) kol için 0.5 yap
    private static final float ON_Z = -1.0F;            // bıçak tabanının merkezi (taban z = -2...0 arası, elin ön yarısı)
    private static final float KOK_Y = -0.8F;           // pençe tabanı elin biraz İÇİNDE başlar, birleşim görünmez

    // 3 ana pençe: yatay konum, boy çarpanı, hafif açılma (Z dönüşü, rad)
    private static final float[] PENCE_X = {-1.2F, 0.0F, 1.2F};
    private static final float[] UZUNLUK = {0.90F, 1.00F, 0.90F};
    private static final float[] YAYILMA = {0.06F, 0.0F, -0.06F};
    private static final float BASLANGIC_EGIM = -0.08F; // tüm pençenin öne eğimi

    // Tırnak şekli: GENISLIK (X, tırnağın eni) tabandan uca azalır (sivrilir), KALINLIK (Z) sabit, 7 segment
    private static final float KALINLIK = 0.7F;
    private static final float[] GENISLIK = {1.2F, 1.1F, 0.95F, 0.78F, 0.6F, 0.4F, 0.2F};
    private static final float SEGMENT_UZUNLUK = 0.65F; // x 7 segment = pençenin toplam boyu (~4.5)
    private static final float ORTUSME = 0.12F;         // segmentler birleşimde çentik kalmasın diye hafif üst üste biner

    // Kıvrım: KIVRIM[s] segment s'nin bir öncekine göre dönüşü (rad), uca doğru artar (parmak gibi kıvrılır).
    private static final float[] KIVRIM = {0.0F, -0.10F, -0.14F, -0.18F, -0.22F, -0.26F, -0.30F};

    // Uçların KIVRILDIĞI yön (derece, kolun dikey ekseni etrafında):
    //    0: ÖNE (oyuncunun önünde duran birine doğru), parmaklar gibi öne kıvrılır
    //   90: vücuda doğru (içe), -90: dışa, 180: arkaya. Küçük değerler (ör. 20) hafif çapraz yapar.
    private static final float KIVRILMA_YONU_DERECE = 0.0F;

    // Başparmak pençesi: kolun DIŞ yanında, hafif aşağıda, dışa doğru yatık
    private static final float BP_X = 2.1F;             // kolun merkezinden dışa uzaklık
    private static final float BP_Y = -1.0F;            // elin alt kenarının 1 birim üstü
    private static final float BP_Z = -1.2F;
    private static final float BP_UZUNLUK = 0.70F;
    private static final float BP_KALINLIK = 0.75F;
    private static final float BP_GENISLIK = 0.80F;     // ana pençelerin genişliğiyle çarpılır
    private static final float BP_ONE_EGIM = -0.25F;
    private static final float BP_DISA_EGIM = 0.95F;    // dışa yatış (rad, ~54 derece)

    public final ModelPart sagPence;
    public final ModelPart solPence;

    public LycanPencelerModel(ModelPart root) {
        this.sagPence = root.getChild("sag_pence");
        this.solPence = root.getChild("sol_pence");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshDefinition = new MeshDefinition();
        PartDefinition root = meshDefinition.getRoot();

        // Klasik kolda sağ kol merkezi -X, sol kol +X tarafında. "disYon": kolun DIŞ tarafı (sağ kol -X, sol kol +X).
        elKur(root, "sag_pence", -KOL_MERKEZ_X, -1.0F);
        elKur(root, "sol_pence", KOL_MERKEZ_X, 1.0F);

        // 256x256 PNG, 64x64 UV alanı: her UV birimi 4 piksel (küçük kutular için net, bulanık olmayan doku)
        return LayerDefinition.create(meshDefinition, 64, 64);
    }

    private static void elKur(PartDefinition root, String ad, float merkezX, float disYon) {
        // Grup: elin alt kenarının ortası. Büyüme animasyonunun ölçek merkezi de burası.
        PartDefinition grup = root.addOrReplaceChild(ad, CubeListBuilder.create(),
                PartPose.offset(merkezX, EL_ALT_Y, 0.0F));

        // Yön: 90 derecede sol kolda (disYon = +1) uçlar -X'e (vücuda), sağ kolda +X'e doğru kıvrılsın diye işaret kola göre değişir.
        float yon = (float) Math.toRadians(KIVRILMA_YONU_DERECE) * disYon;
        for (int i = 0; i < PENCE_X.length; i++) {
            bicakEkle(grup, "pence_" + i, i,
                    PENCE_X[i], KOK_Y, ON_Z, BASLANGIC_EGIM, yon, YAYILMA[i],
                    UZUNLUK[i], KALINLIK, 1.0F);
        }

        // Başparmak: dış yanda, dışa doğru yatık. Sol kolda dış = +X, o yüzden Z dönüşü işareti kola göre değişir.
        bicakEkle(grup, "basparmak", PENCE_X.length,
                disYon * BP_X, BP_Y, BP_Z, BP_ONE_EGIM, 0.0F, -disYon * BP_DISA_EGIM,
                BP_UZUNLUK, BP_KALINLIK, BP_GENISLIK);
    }

    // Tek bir kavisli tırnak pençe: segment zinciri, her segment bir öncekinin çocuğu, pivot'u kendi ÜST ucunda.
    private static void bicakEkle(PartDefinition grup, String ad, int dokuSutunu,
                                  float x, float y, float z, float egimX, float egimY, float egimZ,
                                  float uzunlukCarpani, float kalinlik, float genislikCarpani) {
        PartDefinition parca = grup.addOrReplaceChild(ad, CubeListBuilder.create(),
                PartPose.offsetAndRotation(x, y, z, egimX, egimY, egimZ));

        float segBoy = SEGMENT_UZUNLUK * uzunlukCarpani;
        for (int s = 0; s < GENISLIK.length; s++) {
            float g = GENISLIK[s] * genislikCarpani;
            PartPose poz = (s == 0)
                    ? PartPose.ZERO
                    : PartPose.offsetAndRotation(0.0F, segBoy, 0.0F, KIVRIM[s], 0.0F, 0.0F);
            // Doku: her pençenin kendi sütunu (14 birim), segment başına 3 satır
            parca = parca.addOrReplaceChild("segment_" + s, CubeListBuilder.create()
                            .texOffs(dokuSutunu * 14, s * 3)
                            .addBox(-g / 2.0F, 0.0F, -kalinlik / 2.0F, g, segBoy + ORTUSME, kalinlik),
                    poz);
        }
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        // İskelet animasyonu yok: pençeler kolla birlikte hareket eder, ölçek layer'da uygulanır.
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay,
                               float red, float green, float blue, float alpha) {
        sagPence.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
        solPence.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}
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

    // ============ AYARLAR ============

    // Yerleşim
    private static final float EL_ALT_Y = 10.0F;        // elin alt kenarı (kol pivot'una göre)
    private static final float KOL_MERKEZ_X = 1.0F;     // klasik kol: ±1
    private static final float KOK_Y = -0.15F;          // elin tam ucunda başlar (hafif gömülü birleşim)

    // Parmakların göreceli X konumları (disYon ile çarpılır: pozitif dış, negatif iç)
    // 0: İşaret parmağı, 1: Orta parmak, 2: Yüzük/Dış parmak
    private static final float[] PENCE_DIS_X = {-0.45F, 0.35F, 1.15F};
    private static final float[] PENCE_Z = {-0.45F, -0.50F, -0.40F};
    private static final float[] UZUNLUK = {0.90F, 1.00F, 0.88F};
    private static final float[] YAYILMA_DIS = {-0.04F, 0.0F, 0.08F}; // Z rotasyonu (dışa/içe açılma)
    private static final float BASLANGIC_EGIM = 0.08F;  // bize doğru (arkaya / avuç içine) başlangıç eğimi

    // Tırnak şekli: GENISLIK (X) tabandan uca azalır (sivrilir), KALINLIK (Z) sabit, 7 segment
    private static final float KALINLIK = 0.85F;
    private static final float[] GENISLIK = {1.25F, 1.15F, 1.00F, 0.85F, 0.65F, 0.45F, 0.25F};
    private static final float SEGMENT_UZUNLUK = 0.40F; // daha kısa ve tok parmak/pençe boyu
    private static final float ORTUSME = 0.08F;

    // Kıvrım: pozitif değerler pençenin bize doğru (arkaya / avuç içine) kıvrılmasını sağlar
    private static final float[] KIVRIM = {0.0F, 0.12F, 0.16F, 0.20F, 0.24F, 0.28F, 0.32F};

    // Başparmak: en iç tarafta, elin ucunda, diğer parmaklarla uyumlu hizada
    private static final float BP_IC_X = 1.25F;         // elin iç tarafı
    private static final float BP_Z = -0.35F;
    private static final float BP_UZUNLUK = 0.80F;
    private static final float BP_KALINLIK = 0.85F;
    private static final float BP_GENISLIK = 0.90F;
    private static final float BP_ARKAYA_EGIM = 0.10F;  // bize doğru eğim
    private static final float BP_ICE_EGIM = 0.10F;     // hafif içe eğim

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

        // 256x256 PNG, 64x64 UV alanı: her UV birimi 4 piksel
        return LayerDefinition.create(meshDefinition, 64, 64);
    }

    private static void elKur(PartDefinition root, String ad, float merkezX, float disYon) {
        // Grup: elin alt kenarının ortası. Büyüme animasyonunun ölçek merkezi de burası.
        PartDefinition grup = root.addOrReplaceChild(ad, CubeListBuilder.create(),
                PartPose.offset(merkezX, EL_ALT_Y, 0.0F));

        // 3 ana pençe (işaret, orta, dış parmak): elin ucundan parmak gibi çıkar, disYon ile tam ayna simetrisi
        for (int i = 0; i < PENCE_DIS_X.length; i++) {
            float posX = disYon * PENCE_DIS_X[i];
            float rotZ = disYon * YAYILMA_DIS[i];
            bicakEkle(grup, "pence_" + i, i,
                    posX, KOK_Y, PENCE_Z[i], BASLANGIC_EGIM, 0.0F, rotZ,
                    UZUNLUK[i], KALINLIK, 1.0F);
        }

        // Başparmak: elin iç tarafında (-disYon), elin tam ucunda, bize doğru kıvrık
        float bpPosX = -disYon * BP_IC_X;
        float bpRotZ = -disYon * BP_ICE_EGIM;
        bicakEkle(grup, "basparmak", PENCE_DIS_X.length,
                bpPosX, KOK_Y, BP_Z, BP_ARKAYA_EGIM, 0.0F, bpRotZ,
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
package com.example.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.world.entity.Entity;

// WolfClaws.bbmodel'den (Blockbench, Modded Entity, flip_y) OTOMATIK üretildi, Mojmap'e çevrildi:
//   pivot = (ox, 24 - oy, oz)      küp ofseti y = oy - to.y      dönüş açıları (-rx, ry, -rz) radyan
//   Yarn -> Mojmap: ModelPartData -> PartDefinition, ModelPartBuilder -> CubeListBuilder,
//   ModelTransform -> PartPose, Dilation -> CubeDeformation, TexturedModelData -> LayerDefinition
//
// Model TEK bir el için çizildi (parmaklar + başparmak). Aynı model WerewolfClawsLayer içinde iki kola da
// takılır; sağ kol için ayna çevrilir. Modelin hangi noktasına el takılacağı ve nasıl döndürüleceği
// WerewolfClawsLayer'daki sabitlerle ayarlanır, bu dosyaya dokunmana gerek yok.
//
// Doku: textures/entity/lycan_pencesi.png (128x128 olabilir, UV alanı 16x16 birimdir)
public class LycanPencelerModel<T extends Entity> extends EntityModel<T> {

    // Blockbench'teki doku UV alanı (Texture ayarlarındaki uv_width/uv_height). Doku kayık ya da
    // gerilmiş görünürse bunları proje çözünürlüğü olan 18 yapmayı dene.
    private static final int DOKU_UV_GENISLIK = 16;
    private static final int DOKU_UV_YUKSEKLIK = 16;

    public final ModelPart kok;

    public LycanPencelerModel(ModelPart root) {
        this.kok = root.getChild("sag_kol_koku");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshDefinition = new MeshDefinition();
        PartDefinition root = meshDefinition.getRoot();

        PartDefinition g_sag_kol_koku = root.addOrReplaceChild("sag_kol_koku",
                CubeListBuilder.create()
                , PartPose.offset(0.0F, 24.0F, 0.0F));

        PartDefinition g_parmak_merkez = g_sag_kol_koku.addOrReplaceChild("parmak_merkez",
                CubeListBuilder.create()
                        .texOffs(10, 2).addBox(0.5F, -12.2F, -4.6F, 1.0F, 1.0F, 1.0F, new CubeDeformation(-0.1F))
                , PartPose.offset(0.0F, 0.0F, 0.0F));

        g_parmak_merkez.addOrReplaceChild("cube_r1", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-1.0F, -1.0F, -1.0F, 1.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(1.5F, -11.05F, -2.9F, -0.1309F, 0.0F, 0.0F));

        g_parmak_merkez.addOrReplaceChild("cube_r2", CubeListBuilder.create()
                        .texOffs(10, 4).addBox(-1.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(-0.2F)),
                PartPose.offsetAndRotation(1.5F, -11.2F, -4.1F, 0.08727F, 0.0F, 0.0F));

        g_parmak_merkez.addOrReplaceChild("cube_r3", CubeListBuilder.create()
                        .texOffs(10, 8).addBox(-1.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(-0.3F)),
                PartPose.offsetAndRotation(1.5F, -11.2F, -4.5F, 0.08727F, 0.0F, 0.0F));

        PartDefinition g_parmak_sol = g_sag_kol_koku.addOrReplaceChild("parmak_sol",
                CubeListBuilder.create()
                        .texOffs(6, 6).addBox(2.0F, -12.2F, -4.4F, 1.0F, 1.0F, 1.0F, new CubeDeformation(-0.1F))
                , PartPose.offset(0.0F, 0.0F, 0.0F));

        g_parmak_sol.addOrReplaceChild("cube_r4", CubeListBuilder.create()
                        .texOffs(0, 6).addBox(-1.0F, -1.0F, -1.0F, 1.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.1F, -11.05F, -2.8F, -0.1309F, 0.0F, 0.0F));

        g_parmak_sol.addOrReplaceChild("cube_r5", CubeListBuilder.create()
                        .texOffs(4, 10).addBox(-1.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(-0.2F)),
                PartPose.offsetAndRotation(3.0F, -11.2F, -3.9F, 0.08727F, 0.0F, 0.0F));

        g_parmak_sol.addOrReplaceChild("cube_r6", CubeListBuilder.create()
                        .texOffs(8, 10).addBox(-1.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(-0.3F)),
                PartPose.offsetAndRotation(3.0F, -11.2F, -4.3F, 0.08727F, 0.0F, 0.0F));

        PartDefinition g_parmak_sag = g_sag_kol_koku.addOrReplaceChild("parmak_sag",
                CubeListBuilder.create()
                        .texOffs(0, 9).addBox(-0.9F, -12.2F, -4.4F, 1.0F, 1.0F, 1.0F, new CubeDeformation(-0.1F))
                , PartPose.offset(0.0F, 0.0F, 0.0F));

        g_parmak_sag.addOrReplaceChild("cube_r7", CubeListBuilder.create()
                        .texOffs(0, 3).addBox(-1.0F, -1.0F, -1.0F, 1.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(2.95F, -11.05F, -2.7F, -0.1309F, 0.0F, 0.0F));

        g_parmak_sag.addOrReplaceChild("cube_r8", CubeListBuilder.create()
                        .texOffs(0, 11).addBox(-1.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(-0.3F)),
                PartPose.offsetAndRotation(0.1F, -11.2F, -4.3F, 0.08727F, 0.0F, 0.0F));

        g_parmak_sag.addOrReplaceChild("cube_r9", CubeListBuilder.create()
                        .texOffs(10, 6).addBox(-1.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(-0.2F)),
                PartPose.offsetAndRotation(0.1F, -11.2F, -3.9F, 0.08727F, 0.0F, 0.0F));

        PartDefinition g_basparmak = g_sag_kol_koku.addOrReplaceChild("basparmak",
                CubeListBuilder.create()
                , PartPose.offset(0.0F, 0.0F, 0.0F));

        g_basparmak.addOrReplaceChild("cube_r10", CubeListBuilder.create()
                        .texOffs(6, 0).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(-1.5F, -9.55F, -2.2F, -0.02287F, -0.87693F, -0.04273F));

        g_basparmak.addOrReplaceChild("cube_r11", CubeListBuilder.create()
                        .texOffs(6, 8).addBox(-1.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(-0.3F)),
                PartPose.offsetAndRotation(-1.1F, -10.6F, -3.9F, -0.1645F, 0.07047F, -1.62418F));

        g_basparmak.addOrReplaceChild("cube_r12", CubeListBuilder.create()
                        .texOffs(6, 2).addBox(-1.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(-0.1F)),
                PartPose.offsetAndRotation(-1.1F, -10.6F, -3.0F, -0.25177F, 0.07047F, -1.62418F));

        g_basparmak.addOrReplaceChild("cube_r13", CubeListBuilder.create()
                        .texOffs(6, 4).addBox(-1.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(-0.2F)),
                PartPose.offsetAndRotation(-1.1F, -10.6F, -3.5F, -0.1645F, 0.07047F, -1.62418F));

        return LayerDefinition.create(meshDefinition, DOKU_UV_GENISLIK, DOKU_UV_YUKSEKLIK);
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        // İskelet animasyonu yok: büyüme (ölçek) WerewolfClawsLayer'da PoseStack ile uygulanıyor.
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay,
                               float red, float green, float blue, float alpha) {
        kok.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}
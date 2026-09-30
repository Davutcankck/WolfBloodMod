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

// Blockbench'ten YARN mapping ile export edildi, bu proje MOJMAP kullandığı için çevrildi:
//   MatrixStack -> PoseStack          ModelData -> MeshDefinition
//   ModelPartData -> PartDefinition   ModelPartBuilder -> CubeListBuilder
//   ModelTransform -> PartPose        Dilation -> CubeDeformation
//   TexturedModelData -> LayerDefinition
//   setAngles(...) -> setupAnim(...)  render(...) -> renderToBuffer(...)  [EntityModel'in Mojmap'teki isimleri]
//   .uv(x,y) -> .texOffs(x,y)         .cuboid(...) -> .addBox(...)
//   ModelTransform.pivot -> PartPose.offset      ModelTransform.of -> PartPose.offsetAndRotation
//
// bone/bone2 alanları PUBLIC yapıldı (Blockbench çıktısında private'tı) çünkü WerewolfEarsLayer
// büyüme animasyonu için bunların xScale/yScale/zScale'ine dışarıdan erişmesi gerekiyor.
//
// DÜZELTME: bone/bone2 pivot'undaki Y değeri +7.0F -> -7.0F yapıldı. Minecraft model
// uzayında Y aşağı doğru artar; WerewolfEarsLayer içinde head.translateAndRotate(...)
// çağrısı bizi kafa/boyun pivot'una (y=0) taşıyor. Pozitif Y burada aşağıya (gövdeye,
// kalçaya doğru) gitmek anlamına geliyordu - bu yüzden kulaklar kalça hizasında
// render oluyordu. Negatif Y ile artık kafanın ÜSTÜNE doğru gidiyor.
public class LycanKulakModel<T extends Entity> extends EntityModel<T> {

    public final ModelPart bone;
    public final ModelPart bone2;

    public LycanKulakModel(ModelPart root) {
        this.bone = root.getChild("bone");
        this.bone2 = root.getChild("bone2");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshDefinition = new MeshDefinition();
        PartDefinition partDefinition = meshDefinition.getRoot();

        PartDefinition bone = partDefinition.addOrReplaceChild("bone", CubeListBuilder.create(),
                PartPose.offset(-1.5F, -3.5F, 2.0F));

        bone.addOrReplaceChild("cube_r1", CubeListBuilder.create()
                        .texOffs(10, 4).addBox(0.3F, -2.0F, -3.0F, 1.0F, 1.0F, 3.0F, new CubeDeformation(0.0F))
                        .texOffs(0, 0).addBox(0.3F, -3.0F, -3.0F, 1.0F, 1.0F, 4.0F, new CubeDeformation(0.0F))
                        .texOffs(0, 10).addBox(0.3F, -2.0F, -3.0F, 1.0F, 0.0F, 4.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(-4.0F, 0.0F, 0.0F, 0.6587F, -0.1037F, -0.08F));

        bone.addOrReplaceChild("cube_r2", CubeListBuilder.create()
                        .texOffs(10, 8).addBox(0.15F, -1.5F, -3.0F, 1.0F, 1.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(-4.0F, -1.0F, 0.0F, 0.6587F, -0.1037F, -0.08F));

        PartDefinition bone2 = partDefinition.addOrReplaceChild("bone2", CubeListBuilder.create(),
                PartPose.offset(1.5F, -3.5F, 2.0F));

        bone2.addOrReplaceChild("cube_r3", CubeListBuilder.create()
                        .texOffs(0, 14).addBox(-1.3F, -2.0F, -3.0F, 1.0F, 1.0F, 3.0F, new CubeDeformation(0.0F))
                        .texOffs(0, 5).addBox(-1.3F, -3.0F, -3.0F, 1.0F, 1.0F, 4.0F, new CubeDeformation(0.0F))
                        .texOffs(10, 0).addBox(-1.3F, -2.0F, -3.0F, 1.0F, 0.0F, 4.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(4.0F, 0.0F, 0.0F, 0.6587F, 0.1037F, 0.08F));

        bone2.addOrReplaceChild("cube_r4", CubeListBuilder.create()
                        .texOffs(10, 12).addBox(-1.15F, -1.5F, -3.0F, 1.0F, 1.0F, 3.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(4.0F, -1.0F, 0.0F, 0.6587F, 0.1037F, 0.08F));

        return LayerDefinition.create(meshDefinition, 32, 32);
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        // Kulaklar için ekstra iskelet animasyonu gerekmiyor - pozisyon/rotasyon Blockbench'te sabitlendi.
        // Büyüme animasyonu (scale) bu metodun DIŞINDA, WerewolfEarsLayer içinde uygulanıyor.
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay,
                               float red, float green, float blue, float alpha) {
        bone.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
        bone2.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}
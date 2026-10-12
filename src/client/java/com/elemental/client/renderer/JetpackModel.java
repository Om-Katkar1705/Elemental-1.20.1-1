package com.elemental.client.renderer;

import net.minecraft.client.model.*;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;

public class JetpackModel extends Model {
    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart leftThruster;
    private final ModelPart rightThruster;
    private final ModelPart core;

    public JetpackModel(ModelPart root) {
        super(RenderLayer::getEntityCutoutNoCull);
        this.root = root;
        this.body = root.getChild("body");
        this.leftThruster = this.body.getChild("left_thruster");
        this.rightThruster = this.body.getChild("right_thruster");
        this.core = this.body.getChild("core");
    }

    public static TexturedModelData getTexturedModelData() {
        ModelData modelData = new ModelData();
        ModelPartData rootData = modelData.getRoot();

        // Main mount / body on back:
        // Torso back is at Z = 2.0. We mount slightly offset (Z = 2.3) so it looks natural with or without armor.
        ModelPartData bodyData = rootData.addChild("body",
                ModelPartBuilder.create()
                        // Central mount plate: 8 wide, 10 tall, 2 deep
                        .uv(0, 0).cuboid(-4.0f, 1.0f, 2.3f, 8.0f, 10.0f, 2.0f, new Dilation(0.0f)),
                ModelTransform.NONE
        );

        // Aerogel Core in the center: glowing buoyant chamber
        bodyData.addChild("core",
                ModelPartBuilder.create()
                        .uv(20, 0).cuboid(-2.5f, 3.0f, 3.8f, 5.0f, 6.0f, 3.0f, new Dilation(0.0f)),
                ModelTransform.NONE
        );

        // Left Thruster canister & exhaust nozzle (player's left = +X)
        bodyData.addChild("left_thruster",
                ModelPartBuilder.create()
                        .uv(0, 16).cuboid(2.5f, 0.0f, 2.8f, 4.0f, 11.0f, 4.0f, new Dilation(0.0f))
                        .uv(16, 16).cuboid(3.0f, 11.0f, 3.3f, 3.0f, 3.0f, 3.0f, new Dilation(0.0f)),
                ModelTransform.NONE
        );

        // Right Thruster canister & exhaust nozzle (player's right = -X)
        bodyData.addChild("right_thruster",
                ModelPartBuilder.create()
                        .uv(0, 32).cuboid(-6.5f, 0.0f, 2.8f, 4.0f, 11.0f, 4.0f, new Dilation(0.0f))
                        .uv(16, 32).cuboid(-6.0f, 11.0f, 3.3f, 3.0f, 3.0f, 3.0f, new Dilation(0.0f)),
                ModelTransform.NONE
        );

        return TexturedModelData.of(modelData, 64, 64);
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, float red, float green, float blue, float alpha) {
        this.root.render(matrices, vertices, light, overlay, red, green, blue, alpha);
    }
}

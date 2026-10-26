package com.elemental.client.renderer;

import com.elemental.client.util.ItemRenderContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.state.BoneSnapshot;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public abstract class ElementalWeaponRenderer<T extends Item & GeoAnimatable> extends GeoItemRenderer<T> {

    public ElementalWeaponRenderer(GeoModel<T> model) {
        super(model);
    }

    public static boolean isHeldByPlayer(ModelTransformationMode perspective, LivingEntity entity) {
        if (perspective == null) {
            return false;
        }

        boolean isHandPerspective = perspective == ModelTransformationMode.FIRST_PERSON_RIGHT_HAND
                || perspective == ModelTransformationMode.FIRST_PERSON_LEFT_HAND
                || perspective == ModelTransformationMode.THIRD_PERSON_RIGHT_HAND
                || perspective == ModelTransformationMode.THIRD_PERSON_LEFT_HAND;

        if (!isHandPerspective) {
            return false;
        }

        // In first person, the holding entity is always the player
        if (perspective.isFirstPerson()) {
            return true;
        }

        // In third person, ensure the holding entity is actually a player (not armor stand or mob)
        return entity instanceof PlayerEntity;
    }

    public static void resetBoneRecursively(GeoBone bone) {
        if (bone == null) {
            return;
        }
        bone.saveInitialSnapshot();
        BoneSnapshot initial = bone.getInitialSnapshot();
        if (initial != null) {
            bone.updateRotation(initial.getRotX(), initial.getRotY(), initial.getRotZ());
            bone.updatePosition(initial.getOffsetX(), initial.getOffsetY(), initial.getOffsetZ());
            bone.updateScale(initial.getScaleX(), initial.getScaleY(), initial.getScaleZ());
        }
        bone.resetStateChanges();
        if (bone.getChildBones() != null) {
            for (GeoBone child : bone.getChildBones()) {
                resetBoneRecursively(child);
            }
        }
    }

    public static void resetModelBones(BakedGeoModel model) {
        if (model != null && model.topLevelBones() != null) {
            for (GeoBone bone : model.topLevelBones()) {
                resetBoneRecursively(bone);
            }
        }
    }

    @Override
    public void actuallyRender(MatrixStack poseStack, T animatable, BakedGeoModel model, RenderLayer renderType,
                               VertexConsumerProvider bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick,
                               int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        LivingEntity currentEntity = ItemRenderContext.get();
        boolean heldByPlayer = isHeldByPlayer(this.renderPerspective, currentEntity);

        if (!isReRender) {
            if (heldByPlayer) {
                AnimationState<T> animationState = new AnimationState<>(animatable, 0, 0, partialTick, false);
                long instanceId = getInstanceId(animatable);
                GeoModel<T> currentModel = getGeoModel();

                animationState.setData(DataTickets.TICK, animatable.getTick(this.currentItemStack));
                animationState.setData(DataTickets.ITEM_RENDER_PERSPECTIVE, this.renderPerspective);
                animationState.setData(DataTickets.ITEMSTACK, this.currentItemStack);
                if (currentEntity != null) {
                    animationState.setData(DataTickets.ENTITY, currentEntity);
                }
                animatable.getAnimatableInstanceCache().getManagerForId(instanceId).setData(DataTickets.ITEM_RENDER_PERSPECTIVE, this.renderPerspective);
                currentModel.addAdditionalStateData(animatable, instanceId, animationState::setData);
                currentModel.handleAnimations(animatable, instanceId, animationState);
            } else {
                // When dropped, placed in item frames, shown in GUI, or held by non-players:
                // No animations should be played, keep the model in its pristine resting pose.
                resetModelBones(model);
            }
        }

        this.modelRenderTranslations = new Matrix4f(poseStack.peek().getPositionMatrix());

        updateAnimatedTextureFrame(animatable);

        for (GeoBone group : model.topLevelBones()) {
            renderRecursively(poseStack, animatable, group, renderType, bufferSource, buffer, isReRender, partialTick, packedLight,
                    packedOverlay, red, green, blue, alpha);
        }
    }
}

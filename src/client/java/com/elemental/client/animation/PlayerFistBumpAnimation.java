package com.elemental.client.animation;

import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/**
 * High-performance, butter-smooth animation evaluator for the player's fist bump animation.
 * Exactly replicates all bone rotations, pivots, and timings defined in player.animation.json.
 */
public class PlayerFistBumpAnimation {

    public static final int TOTAL_TICKS = 40; // 2.0s at 20 ticks/sec

    public static class Frame {
        public final float rPitch, rYaw, rRoll;
        public final float lPitch, lYaw, lRoll;
        public final float bPitch, bYaw, bRoll;
        public final float hPitch, hYaw, hRoll;
        public final float rLegPitch, lLegPitch;
        public final float pivotY;

        public Frame(float rPitch, float rYaw, float rRoll,
                     float lPitch, float lYaw, float lRoll,
                     float bPitch, float bYaw, float bRoll,
                     float hPitch, float hYaw, float hRoll,
                     float rLegPitch, float lLegPitch,
                     float pivotY) {
            this.rPitch = rPitch; this.rYaw = rYaw; this.rRoll = rRoll;
            this.lPitch = lPitch; this.lYaw = lYaw; this.lRoll = lRoll;
            this.bPitch = bPitch; this.bYaw = bYaw; this.bRoll = bRoll;
            this.hPitch = hPitch; this.hYaw = hYaw; this.hRoll = hRoll;
            this.rLegPitch = rLegPitch; this.lLegPitch = lLegPitch;
            this.pivotY = pivotY;
        }
    }

    private static final Frame[] FRAMES = new Frame[] {
        new Frame(0.00000f, 0.00000f, 0.00000f, 0.00000f, 0.00000f, 0.00000f, 0.00000f, 0.00000f, 0.00000f, 0.00000f, 0.00000f, 0.00000f, 0.00000f, 0.00000f, -0.0000f),
        new Frame(-0.00794f, -0.02610f, -0.00038f, -0.00781f, 0.00273f, -0.00483f, 0.00681f, -0.00545f, 0.00136f, 0.00545f, 0.00273f, 0.00000f, -0.00545f, -0.00409f, 0.0312f),
        new Frame(-0.02466f, -0.08609f, -0.00202f, -0.02752f, 0.00949f, -0.01708f, 0.02374f, -0.01899f, 0.00475f, 0.01899f, 0.00949f, 0.00000f, -0.01899f, -0.01424f, 0.1088f),
        new Frame(-0.03946f, -0.15256f, -0.00570f, -0.05356f, 0.01820f, -0.03335f, 0.04550f, -0.03640f, 0.00910f, 0.03640f, 0.01820f, 0.00000f, -0.03640f, -0.02730f, 0.2085f),
        new Frame(-0.04168f, -0.19806f, -0.01217f, -0.08037f, 0.02673f, -0.05026f, 0.06682f, -0.05345f, 0.01336f, 0.05345f, 0.02673f, 0.00000f, -0.05345f, -0.04009f, 0.3063f),
        new Frame(-0.19199f, -0.41888f, 0.10472f, -0.10238f, 0.03297f, -0.06442f, 0.08243f, -0.06594f, 0.01649f, 0.06594f, 0.03297f, 0.00000f, -0.06594f, -0.04946f, 0.3778f),
        new Frame(-0.17690f, -0.43020f, 0.08210f, -0.11604f, 0.03491f, -0.07439f, 0.09630f, -0.07162f, 0.01745f, 0.09150f, 0.03671f, 0.00000f, -0.07343f, -0.05417f, 0.4207f),
        new Frame(-0.16494f, -0.41825f, 0.05425f, -0.13831f, 0.03521f, -0.09594f, 0.17473f, -0.08747f, 0.01745f, 0.27835f, 0.05246f, 0.00000f, -0.10522f, -0.07022f, 0.6052f),
        new Frame(-0.18239f, -0.37954f, 0.04952f, -0.13615f, 0.03929f, -0.10689f, 0.17745f, -0.09019f, 0.01745f, 0.26611f, 0.05382f, 0.00000f, -0.11202f, -0.07565f, 0.6753f),
        new Frame(-0.38737f, -0.28517f, 0.08330f, -0.12532f, 0.04696f, -0.12244f, 0.18257f, -0.09530f, 0.01745f, 0.24308f, 0.05638f, 0.00000f, -0.12481f, -0.08589f, 0.8072f),
        new Frame(-0.96572f, -0.06526f, 0.15212f, -0.10901f, 0.05675f, -0.14037f, 0.18909f, -0.10183f, 0.01745f, 0.21373f, 0.05964f, 0.00000f, -0.14112f, -0.09894f, 0.9754f),
        new Frame(-1.26395f, 0.06751f, 0.20146f, -0.09039f, 0.06715f, -0.15849f, 0.19603f, -0.10876f, 0.01745f, 0.18251f, 0.06311f, 0.00000f, -0.15846f, -0.11281f, 1.1543f),
        new Frame(-1.28445f, 0.12794f, 0.23006f, -0.07265f, 0.07669f, -0.17458f, 0.20239f, -0.11512f, 0.01745f, 0.15391f, 0.06629f, 0.00000f, -0.17435f, -0.12552f, 1.3182f),
        new Frame(-1.27177f, 0.18146f, 0.25159f, -0.05896f, 0.08386f, -0.18644f, 0.20717f, -0.11990f, 0.01745f, 0.13238f, 0.06868f, 0.00000f, -0.18631f, -0.13509f, 1.4415f),
        new Frame(-1.25709f, 0.20878f, 0.26157f, -0.05251f, 0.08719f, -0.19186f, 0.20939f, -0.12212f, 0.01745f, 0.12240f, 0.06979f, 0.00000f, -0.19186f, -0.13953f, 1.4987f),
        new Frame(-1.43076f, 0.23663f, 0.24464f, -0.05236f, 0.08727f, -0.19295f, 0.21138f, -0.12217f, 0.01745f, 0.12250f, 0.06981f, 0.00000f, -0.19295f, -0.14059f, 1.5055f),
        new Frame(-1.81192f, 0.29582f, 0.20830f, -0.05236f, 0.08727f, -0.19608f, 0.21762f, -0.12217f, 0.01745f, 0.12341f, 0.06981f, 0.00000f, -0.19608f, -0.14372f, 1.5234f),
        new Frame(-1.92566f, 0.31052f, 0.20034f, -0.05236f, 0.08727f, -0.20034f, 0.22615f, -0.12217f, 0.01745f, 0.12428f, 0.06981f, 0.00000f, -0.20034f, -0.14798f, 1.5479f),
        new Frame(-1.85147f, 0.28289f, 0.20469f, -0.05236f, 0.08727f, -0.20469f, 0.23485f, -0.12217f, 0.01745f, 0.12452f, 0.06981f, 0.00000f, -0.20469f, -0.15233f, 1.5728f),
        new Frame(-1.53440f, 0.31055f, 0.27066f, -0.05236f, 0.08727f, -0.20808f, 0.24162f, -0.12217f, 0.01745f, 0.12348f, 0.06981f, 0.00000f, -0.20808f, -0.15572f, 1.5922f),
        new Frame(-1.85005f, 0.27925f, 0.20944f, -0.05236f, 0.08727f, -0.20944f, 0.24435f, -0.12217f, 0.01745f, 0.15708f, 0.06981f, 0.00000f, -0.20944f, -0.15708f, 1.6000f),
        new Frame(-1.91030f, 0.27381f, 0.17106f, -0.05236f, 0.08727f, -0.21125f, 0.26613f, -0.12399f, 0.01745f, 0.16894f, 0.05850f, 0.00000f, -0.21852f, -0.16434f, 1.6520f),
        new Frame(-1.86913f, 0.26082f, 0.16892f, -0.05236f, 0.08727f, -0.21558f, 0.31807f, -0.12832f, 0.01745f, 0.23858f, 0.05236f, 0.00000f, -0.24016f, -0.18165f, 1.7760f),
        new Frame(-1.80783f, 0.24532f, 0.17041f, -0.05236f, 0.08727f, -0.22075f, 0.38006f, -0.13348f, 0.01745f, 0.33284f, 0.05236f, 0.00000f, -0.26599f, -0.20232f, 1.9240f),
        new Frame(-1.75216f, 0.23234f, 0.17309f, -0.05236f, 0.08727f, -0.22508f, 0.43200f, -0.13781f, 0.01745f, 0.40901f, 0.05236f, 0.00000f, -0.28763f, -0.21963f, 2.0480f),
        new Frame(-1.72788f, 0.22689f, 0.17453f, -0.05236f, 0.08727f, -0.22689f, 0.45379f, -0.13963f, 0.01745f, 0.43815f, 0.05236f, 0.00000f, -0.29671f, -0.22689f, 2.1000f),
        new Frame(-1.91548f, 0.26119f, 0.19189f, -0.05238f, 0.06974f, -0.15699f, 0.20944f, -0.10467f, 0.00003f, 0.13508f, 0.05233f, 0.00000f, -0.19189f, -0.13955f, 1.4990f),
        new Frame(-1.73837f, 0.23611f, 0.18745f, -0.05329f, 0.06641f, -0.15301f, 0.20944f, -0.10245f, 0.00113f, 0.25438f, 0.05112f, 0.00000f, -0.18745f, -0.13622f, 1.4545f),
        new Frame(-1.41295f, 0.18868f, 0.17788f, -0.05502f, 0.05923f, -0.14430f, 0.20944f, -0.09767f, 0.00353f, 0.26063f, 0.04737f, 0.00000f, -0.17788f, -0.12905f, 1.3586f),
        new Frame(-1.09194f, 0.13876f, 0.16517f, -0.05687f, 0.04970f, -0.13251f, 0.20944f, -0.09131f, 0.00670f, 0.25915f, 0.04164f, 0.00000f, -0.16517f, -0.11951f, 1.2311f),
        new Frame(-0.92802f, 0.10622f, 0.15130f, -0.05814f, 0.03929f, -0.11928f, 0.20944f, -0.08437f, 0.01017f, 0.25143f, 0.03491f, 0.00000f, -0.15130f, -0.10911f, 1.0920f),
        new Frame(-0.78224f, 0.08991f, 0.11562f, -0.05815f, 0.02951f, -0.10624f, 0.20944f, -0.07785f, 0.01343f, 0.23896f, 0.02818f, 0.00000f, -0.13825f, -0.09932f, 0.9612f),
        new Frame(-0.70801f, 0.07711f, 0.09311f, -0.05618f, 0.02183f, -0.09502f, 0.20944f, -0.07273f, 0.01599f, 0.22323f, 0.02244f, 0.00000f, -0.12801f, -0.09165f, 0.8586f),
        new Frame(-0.79112f, 0.07032f, 0.08767f, -0.05154f, 0.01776f, -0.08726f, 0.20944f, -0.07001f, 0.01735f, 0.20573f, 0.01869f, 0.00000f, -0.12258f, -0.08757f, 0.8040f),
        new Frame(-0.67733f, 0.06368f, 0.08113f, -0.02877f, 0.01745f, -0.06765f, 0.09992f, -0.03852f, 0.00181f, 0.06715f, 0.01748f, 0.00000f, -0.07523f, -0.05597f, 0.5311f),
        new Frame(-0.41501f, 0.05042f, 0.06885f, -0.01995f, 0.01649f, -0.05943f, 0.08243f, -0.03297f, 0.00000f, 0.04946f, 0.01649f, 0.00000f, -0.06594f, -0.04946f, 0.4723f),
        new Frame(-0.36622f, 0.04236f, 0.06028f, -0.01356f, 0.01336f, -0.04687f, 0.06682f, -0.02673f, 0.00000f, 0.04009f, 0.01336f, 0.00000f, -0.05345f, -0.04009f, 0.3828f),
        new Frame(-0.26259f, 0.02951f, 0.04303f, -0.00807f, 0.00910f, -0.03133f, 0.04550f, -0.01820f, 0.00000f, 0.02730f, 0.00910f, 0.00000f, -0.03640f, -0.02730f, 0.2607f),
        new Frame(-0.14186f, 0.01564f, 0.02318f, -0.00378f, 0.00475f, -0.01613f, 0.02374f, -0.00949f, 0.00000f, 0.01424f, 0.00475f, 0.00000f, -0.01899f, -0.01424f, 0.1360f),
        new Frame(-0.04175f, 0.00454f, 0.00681f, -0.00099f, 0.00136f, -0.00459f, 0.00681f, -0.00273f, 0.00000f, 0.00409f, 0.00136f, 0.00000f, -0.00545f, -0.00409f, 0.0390f),
        new Frame(0.00000f, 0.00000f, 0.00000f, 0.00000f, 0.00000f, 0.00000f, 0.00000f, 0.00000f, 0.00000f, 0.00000f, 0.00000f, 0.00000f, 0.00000f, 0.00000f, -0.0000f)
    };

    public static Frame getInterpolatedFrame(float currentTick) {
        float clamped = MathHelper.clamp(currentTick, 0.0f, (float) TOTAL_TICKS);
        int idx = Math.min((int) clamped, TOTAL_TICKS - 1);
        float alpha = clamped - idx;
        Frame f0 = FRAMES[idx];
        Frame f1 = FRAMES[Math.min(idx + 1, TOTAL_TICKS)];

        return new Frame(
                MathHelper.lerp(alpha, f0.rPitch, f1.rPitch),
                MathHelper.lerp(alpha, f0.rYaw, f1.rYaw),
                MathHelper.lerp(alpha, f0.rRoll, f1.rRoll),
                MathHelper.lerp(alpha, f0.lPitch, f1.lPitch),
                MathHelper.lerp(alpha, f0.lYaw, f1.lYaw),
                MathHelper.lerp(alpha, f0.lRoll, f1.lRoll),
                MathHelper.lerp(alpha, f0.bPitch, f1.bPitch),
                MathHelper.lerp(alpha, f0.bYaw, f1.bYaw),
                MathHelper.lerp(alpha, f0.bRoll, f1.bRoll),
                MathHelper.lerp(alpha, f0.hPitch, f1.hPitch),
                MathHelper.lerp(alpha, f0.hYaw, f1.hYaw),
                MathHelper.lerp(alpha, f0.hRoll, f1.hRoll),
                MathHelper.lerp(alpha, f0.rLegPitch, f1.rLegPitch),
                MathHelper.lerp(alpha, f0.lLegPitch, f1.lLegPitch),
                MathHelper.lerp(alpha, f0.pivotY, f1.pivotY)
        );
    }

    public static void applyPose(PlayerEntityModel<?> model, float currentTick) {
        Frame f = getInterpolatedFrame(currentTick);

        // Right Arm (reaches out forward and punches towards Rocky)
        model.rightArm.pitch = f.rPitch;
        model.rightArm.yaw = f.rYaw;
        model.rightArm.roll = f.rRoll;

        // Left Arm (balances slightly back)
        model.leftArm.pitch = f.lPitch;
        model.leftArm.yaw = f.lYaw;
        model.leftArm.roll = f.lRoll;

        // Body (slight forward lean and turn into the bump)
        model.body.pitch = f.bPitch;
        model.body.yaw = f.bYaw;
        model.body.roll = f.bRoll;

        // Head (looks down towards Rocky)
        model.head.pitch += f.hPitch;
        model.head.yaw += f.hYaw;
        model.head.roll += f.hRoll;

        // Legs (slight knee flex to accommodate the friendly crouch)
        model.rightLeg.pitch += f.rLegPitch;
        model.leftLeg.pitch += f.lLegPitch;

        // Height crouch offset
        model.body.pivotY += f.pivotY;
        model.head.pivotY += f.pivotY;
        model.rightArm.pivotY += f.pivotY;
        model.leftArm.pivotY += f.pivotY;

        // Sync overlay layers (jacket, sleeves, pants)
        model.rightSleeve.copyTransform(model.rightArm);
        model.leftSleeve.copyTransform(model.leftArm);
        model.jacket.copyTransform(model.body);
        model.rightPants.copyTransform(model.rightLeg);
        model.leftPants.copyTransform(model.leftLeg);
    }

    public static void applyFirstPersonArm(MatrixStack matrices, float currentTick) {
        // Progress factor based on right arm pitch reaching out
        // Normalized peak thrust is at tick 20 (t = 1.0s)
        float thrustProgress = (float) Math.sin(MathHelper.clamp(currentTick / (float) TOTAL_TICKS, 0.0f, 1.0f) * Math.PI);

        // Translate arm forward (-Z), slightly inward (+X), and slightly down (+Y)
        matrices.translate(thrustProgress * 0.15f, thrustProgress * 0.05f, -thrustProgress * 0.45f);

        // Rotate arm to match the fist bump angle in first person
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(thrustProgress * -25.0f));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(thrustProgress * 15.0f));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(thrustProgress * 10.0f));
    }
}

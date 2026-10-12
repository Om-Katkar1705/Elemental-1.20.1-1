package com.elemental.client.mixin;

import com.elemental.Elemental;
import com.elemental.mixin.SlotAccessor;
import com.elemental.util.JetpackSlotHolder;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.AbstractInventoryScreen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(CreativeInventoryScreen.class)
public abstract class CreativeInventoryScreenMixin extends AbstractInventoryScreen<CreativeInventoryScreen.CreativeScreenHandler> {

    @Shadow
    public abstract boolean isInventoryTabSelected();

    @Unique
    private static final Identifier ELEMENTAL$INVENTORY_TEXTURE = new Identifier("textures/gui/container/inventory.png");
    @Unique
    private static final Identifier ELEMENTAL$EMPTY_JETPACK_SLOT = new Identifier(Elemental.MOD_ID, "textures/gui/empty_jetpack_slot.png");
    @Unique
    private static final int JETPACK_SLOT_INDEX = 46;

    public CreativeInventoryScreenMixin(CreativeInventoryScreen.CreativeScreenHandler screenHandler, PlayerInventory playerInventory, Text text) {
        super(screenHandler, playerInventory, text);
    }

    @Inject(method = "setSelectedTab", at = @At("TAIL"))
    private void elemental$positionJetpackSlotInCreative(ItemGroup group, CallbackInfo ci) {
        if (this.isInventoryTabSelected()) {
            elemental$repositionExtraSlot();
        }
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void elemental$onInit(CallbackInfo ci) {
        if (this.isInventoryTabSelected()) {
            elemental$repositionExtraSlot();
        }
    }

    @Unique
    private void elemental$repositionExtraSlot() {
        if (this.handler.slots.size() > JETPACK_SLOT_INDEX) {
            Slot slot = this.handler.slots.get(JETPACK_SLOT_INDEX);
            if (slot instanceof SlotAccessor accessor) {
                accessor.elemental$setX(127);
                accessor.elemental$setY(20);
            }
        }
    }

    @Inject(method = "drawBackground", at = @At("TAIL"))
    private void elemental$drawCreativeJetpackSlot(DrawContext context, float delta, int mouseX, int mouseY, CallbackInfo ci) {
        if (this.isInventoryTabSelected()) {
            // Draw standard 18x18 slot background frame at (x + 126, y + 19)
            context.drawTexture(ELEMENTAL$INVENTORY_TEXTURE, this.x + 126, this.y + 19, 76, 61, 18, 18);

            if (this.client != null && this.client.player != null) {
                ItemStack extra = JetpackSlotHolder.getJetpackStack(this.client.player);
                if (extra.isEmpty()) {
                    RenderSystem.enableBlend();
                    RenderSystem.defaultBlendFunc();
                    context.drawTexture(ELEMENTAL$EMPTY_JETPACK_SLOT, this.x + 127, this.y + 20, 0, 0, 16, 16, 16, 16);
                }
            }
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void elemental$renderCreativeTooltip(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (this.isInventoryTabSelected() && this.handler.getCursorStack().isEmpty()) {
            if (this.focusedSlot != null && this.focusedSlot.id == JETPACK_SLOT_INDEX && !this.focusedSlot.hasStack()) {
                context.drawTooltip(this.textRenderer, Text.translatable("tooltip.elemental.extra_jetpack_slot"), mouseX, mouseY);
            }
        }
    }
}

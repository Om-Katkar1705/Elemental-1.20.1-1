package com.elemental.client.mixin;

import com.elemental.Elemental;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.AbstractInventoryScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin extends AbstractInventoryScreen<PlayerScreenHandler> {

    @Unique
    private static final Identifier ELEMENTAL$INVENTORY_TEXTURE = new Identifier("textures/gui/container/inventory.png");
    @Unique
    private static final Identifier ELEMENTAL$EMPTY_JETPACK_SLOT = new Identifier(Elemental.MOD_ID, "textures/gui/empty_jetpack_slot.png");
    @Unique
    private static final int JETPACK_SLOT_INDEX = 46;

    public InventoryScreenMixin(PlayerScreenHandler screenHandler, PlayerInventory playerInventory, Text text) {
        super(screenHandler, playerInventory, text);
    }

    @Inject(method = "drawBackground", at = @At("TAIL"))
    private void elemental$drawExtraJetpackSlot(DrawContext context, float delta, int mouseX, int mouseY, CallbackInfo ci) {
        // Draw the 18x18 slot background frame at (x + 76, y + 43), copying the vanilla slot border from UV (76, 61)
        context.drawTexture(ELEMENTAL$INVENTORY_TEXTURE, this.x + 76, this.y + 43, 76, 61, 18, 18);

        // If the extra jetpack slot (index 46) is empty, render the subtle jetpack silhouette icon
        if (this.handler.slots.size() > JETPACK_SLOT_INDEX && !this.handler.getSlot(JETPACK_SLOT_INDEX).hasStack()) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            context.drawTexture(ELEMENTAL$EMPTY_JETPACK_SLOT, this.x + 77, this.y + 44, 0, 0, 16, 16, 16, 16);
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void elemental$renderTooltip(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (this.handler.getCursorStack().isEmpty()) {
            if (this.focusedSlot != null && this.focusedSlot.id == JETPACK_SLOT_INDEX && !this.focusedSlot.hasStack()) {
                context.drawTooltip(this.textRenderer, Text.translatable("tooltip.elemental.extra_jetpack_slot"), mouseX, mouseY);
            }
        }
    }
}

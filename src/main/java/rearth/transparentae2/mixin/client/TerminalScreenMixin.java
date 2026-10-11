package rearth.transparentae2.mixin.client;

import java.util.List;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.widgets.ToggleButton;
import appeng.menu.AEBaseMenu;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rearth.transparentae2.Config;
import rearth.transparentae2.NetworkRenderMenu;
import rearth.transparentae2.client.ClientConfig;

@Mixin(AEBaseScreen.class)
abstract class TerminalScreenMixin extends AbstractContainerScreen<AEBaseMenu> {
    @Unique
    private ToggleButton transparentae2$renderButton;

    protected TerminalScreenMixin(AEBaseMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Shadow
    protected abstract <B extends Button> B addToLeftToolbar(B button);

    @Inject(method = "<init>", at = @At("RETURN"))
    private void transparentae2$addButton(CallbackInfo ci) {
        if (menu instanceof NetworkRenderMenu settings) {
            transparentae2$renderButton = new ToggleButton(Icon.OVERLAY_ON, Icon.OVERLAY_OFF,
                    settings::transparentae2$setRenderingEnabled);
            var title = Component.translatable("gui.transparentae2.network_rendering");
            transparentae2$renderButton.setTooltipOn(List.of(title,
                    Component.translatable("gui.transparentae2.network_rendering.enabled")));
            transparentae2$renderButton.setTooltipOff(List.of(title,
                    Component.translatable("gui.transparentae2.network_rendering.disabled")));
            transparentae2$renderButton.active = false;
            transparentae2$renderButton.visible = false;
            addToLeftToolbar(transparentae2$renderButton);
        }
    }

    @Inject(method = "containerTick", at = @At("RETURN"))
    private void transparentae2$updateButton(CallbackInfo ci) {
        if (transparentae2$renderButton != null && menu instanceof NetworkRenderMenu settings) {
            boolean visible = settings.transparentae2$hasNetwork()
                    && Config.ENABLE_TRANSFER_PATHS.getAsBoolean()
                    && ClientConfig.RENDER_TRANSFER_ITEMS.getAsBoolean();
            transparentae2$renderButton.visible = visible;
            transparentae2$renderButton.active = visible;
            transparentae2$renderButton.setState(settings.transparentae2$isRenderingEnabled());
        }
    }
}

package rearth.transparentae2.mixin;

import appeng.menu.AEBaseMenu;
import appeng.menu.guisync.ClientActionKey;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.PatternAccessTermMenu;
import appeng.menu.me.common.MEStorageMenu;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rearth.transparentae2.NetworkRenderMenu;
import rearth.transparentae2.NetworkRenderService;

@Mixin(value = {MEStorageMenu.class, PatternAccessTermMenu.class})
abstract class TerminalMenuMixin extends AEBaseMenu implements NetworkRenderMenu {
    @Unique
    private static final ClientActionKey<Boolean> transparentae2$SET_RENDERING =
            new ClientActionKey<>("transparentae2:set_network_rendering");

    @GuiSync(30000)
    public boolean transparentae2$networkAvailable;
    @GuiSync(30001)
    public boolean transparentae2$renderingEnabled;

    protected TerminalMenuMixin(MenuType<?> type, int id, Inventory inventory, Object host) {
        super(type, id, inventory, host);
    }

    @Inject(method = "broadcastChanges", at = @At("HEAD"))
    private void transparentae2$syncSetting(CallbackInfo ci) {
        if (isServerSide()) {
            var host = getActionHost();
            var node = host != null ? host.getActionableNode() : null;
            transparentae2$networkAvailable = node != null;
            transparentae2$renderingEnabled = NetworkRenderService.isEnabled(node);
        }
    }

    @Override
    public boolean transparentae2$hasNetwork() {
        return transparentae2$networkAvailable;
    }

    @Override
    public boolean transparentae2$isRenderingEnabled() {
        return transparentae2$renderingEnabled;
    }

    @Override
    public void transparentae2$setRenderingEnabled(boolean enabled) {
        if (isClientSide()) {
            sendClientAction(transparentae2$SET_RENDERING, enabled);
            return;
        }
        var host = getActionHost();
        var node = host != null ? host.getActionableNode() : null;
        if (node != null && node.isActive() && stillValid(getPlayer())) {
            node.getGrid().getService(NetworkRenderService.class).setEnabled(enabled);
            broadcastChanges();
        }
    }
}

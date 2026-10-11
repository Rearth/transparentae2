package rearth.transparentae2.mixin;

import java.util.function.Consumer;

import appeng.menu.AEBaseMenu;
import appeng.menu.guisync.ClientActionKey;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rearth.transparentae2.NetworkRenderMenu;

@Mixin(AEBaseMenu.class)
abstract class TerminalMenuActionsMixin extends AbstractContainerMenu {
    protected TerminalMenuActionsMixin(MenuType<?> type, int id) {
        super(type, id);
    }

    @Shadow
    protected abstract <T> void registerClientAction(ClientActionKey<T> key,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec, Consumer<T> handler);

    @Inject(method = "<init>", at = @At("RETURN"))
    private void transparentae2$registerAction(CallbackInfo ci) {
        // The base constructor runs exactly once for every terminal variant, on both sides.
        if (this instanceof NetworkRenderMenu settings) {
            registerClientAction(new ClientActionKey<>("transparentae2:set_network_rendering"),
                    ByteBufCodecs.BOOL, settings::transparentae2$setRenderingEnabled);
        }
    }
}

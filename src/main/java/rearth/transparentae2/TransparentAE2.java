package rearth.transparentae2;

import org.slf4j.Logger;
import appeng.api.networking.GridServices;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import rearth.transparentae2.init.ModDataComponents;
import rearth.transparentae2.init.ModItems;
import rearth.transparentae2.network.TransferPathNetworking;

@Mod(TransparentAE2.MODID)
public class TransparentAE2 {
    public static final String MODID = "transparentae2";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TransparentAE2(IEventBus modEventBus, ModContainer modContainer) {
        GridServices.register(NetworkRenderService.class, NetworkRenderService.class);
        ModDataComponents.COMPONENTS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        modEventBus.addListener(ModItems::addCreativeTabContents);
        modEventBus.addListener(TransferPathNetworking::register);
        NeoForge.EVENT_BUS.addListener(TransferBatcher::flush);
        modContainer.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
        LOGGER.info("Transparent AE2 enabled");
    }
}

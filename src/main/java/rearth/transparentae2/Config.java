package rearth.transparentae2;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLE_TRANSFER_PATHS = BUILDER
            .comment("Calculate AE2 item-transfer paths and send them to clients for rendering.")
            .translation("config.transparentae2.enableTransferPaths")
            .define("enableTransferPaths", true);

    static final ModConfigSpec SPEC = BUILDER.build();
}

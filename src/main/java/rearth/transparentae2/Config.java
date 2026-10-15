package rearth.transparentae2;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLE_TRANSFER_PATHS = BUILDER
            .comment("Calculate AE2 item-transfer paths and send them to clients for rendering.")
            .translation("config.transparentae2.enableTransferPaths")
            .define("enableTransferPaths", true);

    public static final ModConfigSpec.BooleanValue ENABLE_TRANSFER_LOGGING = BUILDER
            .comment("Log each AE2 item transfer and resolved path at DEBUG level. Requires DEBUG logging to be enabled.",
                    "Disabled by default because busy networks can produce very large debug logs. Does not affect animations.")
            .translation("config.transparentae2.enableTransferLogging")
            .define("enableTransferLogging", false);

    static final ModConfigSpec SPEC = BUILDER.build();
}

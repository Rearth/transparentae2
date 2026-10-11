package rearth.transparentae2.client;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue RENDER_TRANSFER_ITEMS = BUILDER
            .comment("Render item stacks moving along observed AE2 transfer paths.")
            .translation("config.transparentae2.renderTransferItems")
            .define("renderTransferItems", true);

    public static final ModConfigSpec.DoubleValue ITEM_MOVEMENT_SPEED = BUILDER
            .comment("Movement speed of rendered transfer items in blocks per second.")
            .translation("config.transparentae2.itemMovementSpeed")
            .defineInRange("itemMovementSpeed", 6.0, 0.1, 100.0);

    public static final ModConfigSpec.IntValue MAX_TRANSFERS = BUILDER
            .comment("Maximum number of recent item transfers retained by the client renderer.")
            .translation("config.transparentae2.maxTransfers")
            .defineInRange("maxTransfers", 128, 1, 4096);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private ClientConfig() {
    }
}

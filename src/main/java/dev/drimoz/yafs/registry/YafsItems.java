package dev.drimoz.yafs.registry;

import dev.drimoz.yafs.YetAnotherFarmingStation;
import net.minecraft.world.item.BlockItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class YafsItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(YetAnotherFarmingStation.MOD_ID);

    public static final DeferredItem<BlockItem> GREENHOUSE_T1 = ITEMS.registerSimpleBlockItem(YafsBlocks.GREENHOUSE_T1);
    public static final DeferredItem<BlockItem> GREENHOUSE_T2 = ITEMS.registerSimpleBlockItem(YafsBlocks.GREENHOUSE_T2);
    public static final DeferredItem<BlockItem> GREENHOUSE_T3 = ITEMS.registerSimpleBlockItem(YafsBlocks.GREENHOUSE_T3);

    private YafsItems() {}
}

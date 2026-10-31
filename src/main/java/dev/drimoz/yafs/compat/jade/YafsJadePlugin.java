package dev.drimoz.yafs.compat.jade;

import dev.drimoz.yafs.YetAnotherFarmingStation;
import dev.drimoz.yafs.content.greenhouse.GreenhouseBlock;
import dev.drimoz.yafs.content.greenhouse.GreenhouseBlockEntity;
import dev.drimoz.yafs.content.multiblock.MultiblockPartBlock;
import dev.drimoz.yafs.content.multiblock.MultiblockPartBlockEntity;
import dev.drimoz.yafs.core.Efficiency;
import dev.drimoz.yafs.core.PlotStatus;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

import java.util.Locale;

/**
 * Jade (YAF-081) : au survol de n'importe quel bloc d'une serre, ce qu'elle fait et ce qui la
 * freine, sans l'ouvrir. Chargé par Jade seulement : sans Jade, rien ici n'est lu.
 *
 * <p>Le serveur résume, le client affiche : les verdicts d'efficacité n'existent qu'au serveur.
 */
@WailaPlugin
public class YafsJadePlugin implements IWailaPlugin {

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(Greenhouse.INSTANCE, GreenhouseBlockEntity.class);
        registration.registerBlockDataProvider(Greenhouse.INSTANCE, MultiblockPartBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(Greenhouse.INSTANCE, GreenhouseBlock.class);
        registration.registerBlockComponent(Greenhouse.INSTANCE, MultiblockPartBlock.class);
    }

    enum Greenhouse implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
        INSTANCE;

        private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(YetAnotherFarmingStation.MOD_ID, "greenhouse");

        @Override
        public ResourceLocation getUid() {
            return UID;
        }

        /** Un résumé de la serre : marche, parcelles qui poussent ou coincent, la plus lente. */
        @Override
        public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            BlockEntity entity = accessor.getBlockEntity();
            if (entity instanceof MultiblockPartBlockEntity part) entity = part.master();
            if (!(entity instanceof GreenhouseBlockEntity greenhouse)) return;

            int growing = 0;
            int problems = 0;
            int blocked = 0;
            Efficiency.Verdict slowest = null;
            for (int plot = 0; plot < greenhouse.plotCount(); plot++) {
                PlotStatus status = greenhouse.status(plot);
                if (status == PlotStatus.PROBLEM) problems++;
                if (status == PlotStatus.BLOCKED) blocked++;
                Efficiency.Verdict verdict = greenhouse.verdict(plot);
                if (verdict == null) continue;
                growing++;
                if (slowest == null || verdict.value() < slowest.value()) slowest = verdict;
            }

            data.putBoolean("switchedOn", greenhouse.isSwitchedOn());
            data.putBoolean("running", greenhouse.isRunning());
            data.putInt("plots", greenhouse.plotCount());
            data.putInt("growing", growing);
            data.putInt("problems", problems);
            data.putInt("blocked", blocked);
            data.putLong("demand", greenhouse.demand());
            data.putBoolean("starved", greenhouse.starved());
            if (slowest != null && slowest.value() < 1D) {
                data.putString("limiting", slowest.limiting().name().toLowerCase(Locale.ROOT));
                data.putInt("efficiency", (int) Math.round(slowest.value() * 100));
            }
        }

        /** Les mêmes mots que l'onglet Fonctionnement de l'écran. */
        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getServerData();
            if (!data.contains("plots")) return;

            int growing = data.getInt("growing");
            Component state;
            if (!data.getBoolean("running")) {
                state = Component.translatable(data.getBoolean("switchedOn") ? "yafs.gui.env.redstone" : "yafs.gui.env.off").withStyle(ChatFormatting.GRAY);
            } else if (data.getBoolean("starved")) {
                state = Component.translatable("yafs.gui.modules.unpowered").withStyle(ChatFormatting.RED);
            } else if (data.getInt("blocked") > 0) {
                state = Component.translatable("yafs.gui.ops.full").withStyle(ChatFormatting.GOLD);
            } else if (data.getInt("problems") > 0) {
                state = Component.translatable("yafs.gui.env.problems", data.getInt("problems")).withStyle(ChatFormatting.RED);
            } else if (growing == 0) {
                state = Component.translatable("yafs.gui.env.idle").withStyle(ChatFormatting.GRAY);
            } else {
                state = Component.translatable("yafs.gui.ops.running").withStyle(ChatFormatting.GREEN);
            }
            tooltip.add(state);

            if (growing > 0) {
                tooltip.add(Component.translatable("yafs.jade.growing", growing, data.getInt("plots")));
                tooltip.add(data.contains("limiting")
                        ? Component.translatable("yafs.gui.env.limited", Component.translatable("yafs.gui.factor." + data.getString("limiting")),
                                data.getInt("efficiency")).withStyle(ChatFormatting.GOLD)
                        : Component.translatable("yafs.gui.env.ideal").withStyle(ChatFormatting.GREEN));
            }
            if (data.getLong("demand") > 0) tooltip.add(Component.translatable("yafs.jade.energy", data.getLong("demand")));
        }
    }
}

package dev.drimoz.yafs.content.crop;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import dev.drimoz.yafs.YetAnotherFarmingStation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Ce que les datapacks disent des cultures et des substrats (YAF-014) : lu au chargement et à
 * chaque {@code /reload}, puis envoyé aux joueurs, puisque le client refait la détection.
 *
 * <p>Un fichier invalide est refusé et journalisé, jamais corrigé : les autres restent.
 */
public final class CropData {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();

    private static volatile List<CropOverride> overrides = List.of();
    private static volatile List<SubstrateDefinition> substrates = List.of();

    private CropData() {}

    public static List<CropOverride> overrides() {
        return overrides;
    }

    public static List<SubstrateDefinition> substrates() {
        return substrates;
    }

    /** Remplace tout et refait la détection. Public pour les GameTests. */
    public static void set(List<CropOverride> newOverrides, List<SubstrateDefinition> newSubstrates) {
        overrides = List.copyOf(newOverrides);
        substrates = List.copyOf(newSubstrates);
        Crops.invalidate();
    }

    // Chargement

    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        HolderLookup.Provider registries = event.getRegistryAccess();
        event.addListener(new Loader<>("yafs/crop", CropOverride.CODEC, registries,
                loaded -> set(loaded, substrates)));
        event.addListener(new Loader<>("yafs/substrate", SubstrateDefinition.CODEC, registries,
                loaded -> set(overrides, loaded)));
    }

    private static final class Loader<T> extends SimpleJsonResourceReloadListener {

        private final String directory;
        private final Codec<T> codec;
        private final HolderLookup.Provider registries;
        private final Consumer<List<T>> sink;

        Loader(String directory, Codec<T> codec, HolderLookup.Provider registries, Consumer<List<T>> sink) {
            super(GSON, directory);
            this.directory = directory;
            this.codec = codec;
            this.registries = registries;
            this.sink = sink;
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
            RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, this.registries);
            List<T> loaded = new ArrayList<>();
            files.forEach((id, json) -> this.codec.parse(ops, json).ifSuccess(loaded::add)
                    .ifError(error -> LOGGER.error("{}/{} refusé : {}", this.directory, id, error.message())));
            LOGGER.info("{} : {} entrées", this.directory, loaded.size());
            this.sink.accept(loaded);
        }
    }

    // Synchronisation

    /**
     * Ce que lâche une culture, tiré au serveur : les tables de loot n'existent que là, et JEI veut
     * montrer la récolte. Des quantités moyennes sur {@link #ROLLS} tirages, pas une promesse.
     */
    public record Harvest(Item seed, List<ItemStack> drops) {

        static final StreamCodec<RegistryFriendlyByteBuf, Harvest> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.registry(Registries.ITEM), Harvest::seed,
                ItemStack.LIST_STREAM_CODEC, Harvest::drops,
                Harvest::new);
    }

    private static final int ROLLS = 16;
    private static volatile Map<Item, List<ItemStack>> harvests = Map.of();

    /** La récolte typique de cette graine ; vide si le serveur ne l'a pas encore dite. */
    public static List<ItemStack> harvest(Item seed) {
        return harvests.getOrDefault(seed, List.of());
    }

    private static void setHarvests(List<Harvest> sampled) {
        Map<Item, List<ItemStack>> bySeed = new HashMap<>();
        for (Harvest harvest : sampled) bySeed.put(harvest.seed(), List.copyOf(harvest.drops()));
        harvests = Map.copyOf(bySeed);
    }

    /** Chaque culture reconnue, sa table de loot tirée {@link #ROLLS} fois : les items, en quantité moyenne. */
    private static List<Harvest> sample(ServerLevel level) {
        List<Harvest> sampled = new ArrayList<>();
        for (Crop crop : Crops.all().values()) {
            Map<Item, Integer> totals = new LinkedHashMap<>();
            for (int roll = 0; roll < ROLLS; roll++) {
                for (ItemStack drop : Block.getDrops(crop.harvestState(), level, BlockPos.ZERO, null)) totals.merge(drop.getItem(), drop.getCount(), Integer::sum);
            }
            List<ItemStack> drops = new ArrayList<>();
            totals.forEach((item, total) -> drops.add(new ItemStack(item, Math.max(1, Math.round(total / (float) ROLLS)))));
            sampled.add(new Harvest(crop.seed(), drops));
        }
        return sampled;
    }

    public record Payload(List<CropOverride> overrides, List<SubstrateDefinition> substrates, List<Harvest> harvests) implements CustomPacketPayload {

        public static final Type<Payload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(YetAnotherFarmingStation.MOD_ID, "crop_data"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Payload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.fromCodecWithRegistries(CropOverride.CODEC.listOf()), Payload::overrides,
                ByteBufCodecs.fromCodecWithRegistries(SubstrateDefinition.CODEC.listOf()), Payload::substrates,
                Harvest.STREAM_CODEC.apply(ByteBufCodecs.list()), Payload::harvests,
                Payload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(Payload.TYPE, Payload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    set(payload.overrides(), payload.substrates());
                    setHarvests(payload.harvests());
                }));
    }

    /** À la connexion d'un joueur, et à chaque {@code /reload} pour tous. */
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        // Tiré une fois par synchronisation, gardé aussi ici : en solo, le client lit la même mémoire.
        List<Harvest> sampled = sample(event.getPlayerList().getServer().overworld());
        setHarvests(sampled);
        Payload payload = new Payload(overrides, substrates, sampled);
        event.getRelevantPlayers().forEach(player -> PacketDistributor.sendToPlayer(player, payload));
    }
}

package dev.drimoz.yafs.gametest;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import dev.drimoz.yafs.YetAnotherFarmingStation;
import dev.drimoz.yafs.content.greenhouse.GreenhouseBlock;
import dev.drimoz.yafs.content.greenhouse.GreenhouseBlockEntity;
import dev.drimoz.yafs.content.multiblock.MultiblockBlock;
import dev.drimoz.yafs.registry.YafsBlocks;
import dev.drimoz.yafs.registry.YafsItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

@EventBusSubscriber(modid = YetAnotherFarmingStation.MOD_ID, value = Dist.CLIENT)
public final class GuiPreview {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean ENABLED = "1".equals(System.getenv("YAFS_GUI_PREVIEW")) || PerfBench.ENABLED;
    private static final String SOURCE_WORLD = "New World";
    private static final String WORLD = "GuiPreview";
    private static final BlockPos ORIGIN = new BlockPos(2000, 150, 2000);

    private record Step(int delay, Consumer<Minecraft> action) {}

    private static final Deque<Step> STEPS = new ArrayDeque<>();
    private static boolean started;
    private static int wait;
    private static String pendingShot;

    static int frames;

    private GuiPreview() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!ENABLED) return;
        Minecraft minecraft = Minecraft.getInstance();

        if (!started && minecraft.screen instanceof TitleScreen title && minecraft.getOverlay() == null) {
            started = true;
            minecraft.options.pauseOnLostFocus = false;
            copyWorld(minecraft);
            if (PerfBench.ENABLED) PerfBench.script();
            else script();
            minecraft.createWorldOpenFlows().openWorld(WORLD, () -> minecraft.setScreen(title));
            return;
        }
        if (!started || minecraft.player == null || minecraft.getSingleplayerServer() == null) return;
        if (pendingShot != null || STEPS.isEmpty()) return;
        if (wait-- > 0) return;

        Step step = STEPS.poll();
        step.action().accept(minecraft);
        wait = STEPS.isEmpty() ? 0 : STEPS.peek().delay();
    }

    @SubscribeEvent
    public static void onRenderFrame(RenderFrameEvent.Post event) {
        if (!ENABLED) return;
        frames++;
        if (pendingShot == null) return;
        Minecraft minecraft = Minecraft.getInstance();

        File file = new File(minecraft.gameDirectory, "screenshots/preview/" + pendingShot + ".png");
        file.getParentFile().mkdirs();
        try (NativeImage image = Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
            image.writeToFile(file);
            LOGGER.info("Aperçu d'écran : {}", file);
        } catch (IOException e) {
            LOGGER.error("Capture impossible : {}", file, e);
        }
        pendingShot = null;
    }

    private static void script() {
        BlockPos small = ORIGIN.offset(0, 0, 4);
        BlockPos big = ORIGIN.offset(10, 0, 6);

        step(60, mc -> command(mc, "gamemode creative"));
        step(5, mc -> command(mc, "time set noon"));
        step(5, mc -> command(mc, "weather clear"));

        step(5, mc -> fly(mc));
        step(5, mc -> command(mc, "tp @s " + ORIGIN.getX() + " " + ORIGIN.getY() + " " + ORIGIN.getZ() + " 0 20"));

        step(60, mc -> command(mc, "fill " + (ORIGIN.getX() - 4) + " " + (ORIGIN.getY() - 1) + " " + (ORIGIN.getZ() - 2) + " "
                + (ORIGIN.getX() + 16) + " " + (ORIGIN.getY() - 1) + " " + (ORIGIN.getZ() + 16) + " minecraft:smooth_stone"));
        step(10, mc -> onServer(mc, level -> {
            build(level, small, YafsBlocks.GREENHOUSE_T1.get(), List.of(), 0);

            level.setBlock(small.offset(2, 0, 1), YafsBlocks.CREATIVE_ENERGY_SOURCE.get().defaultBlockState(), 3);
            build(level, big, YafsBlocks.GREENHOUSE_T3.get(),
                    List.of(YafsItems.ACCELERATOR_1.get(), YafsItems.GROW_LAMP.get(), YafsItems.DENSITY_1.get(), YafsItems.CAPACITY.get()), 1_000_000);
        }));

        step(10, mc -> command(mc, "tp @s " + small.getX() + " " + ORIGIN.getY() + " " + (small.getZ() - 4) + " 0 20"));
        step(40, mc -> use(mc, small));
        step(5, mc -> hover(mc, 0.0, 0.0));
        step(20, mc -> shot("greenhouse_t1"));

        step(5, mc -> clickTab(mc, 3));
        step(15, mc -> clickTabContent(mc, 3, 10, 8));
        step(20, mc -> shot("greenhouse_t1_switched_off"));
        step(5, mc -> clickTabContent(mc, 3, 10, 8));
        step(5, mc -> mc.setScreen(null));

        step(10, mc -> command(mc, "tp @s " + (big.getX()) + " " + ORIGIN.getY() + " " + (big.getZ() - 6) + " 0 20"));
        step(40, mc -> use(mc, big));
        step(5, mc -> hover(mc, 0.0, 0.0));
        step(20, mc -> shot("greenhouse_t3"));
        step(5, mc -> clickTab(mc, 2));
        step(15, mc -> shot("greenhouse_t3_modules"));

        step(5, mc -> hoverGui(mc, 176 + 6 + 1 + 8, 4 + 22 + 1 + 8));
        step(10, mc -> shot("greenhouse_t3_module_tooltip"));
        step(5, mc -> hoverGui(mc, 176 + 6 + 1 + 18 + 8, 4 + 22 + 1 + 18 + 8));
        step(10, mc -> shot("greenhouse_t3_module_help"));

        step(5, mc -> hoverGui(mc, 61 + 9, 30 + 9));
        step(10, mc -> shot("greenhouse_t3_plot_tooltip"));
        step(5, mc -> clickGui(mc, 25 + 9, 30 + 9));
        step(5, mc -> hover(mc, 0.0, 0.0));
        step(10, mc -> shot("greenhouse_t3_detail"));
        step(5, mc -> clickGui(mc, 169 - 5, 20 + 4));
        step(5, mc -> clickGui(mc, 25 + 9, 30 + 18 + 9));
        step(10, mc -> shot("greenhouse_t3_detail_empty"));
        step(5, mc -> clickGui(mc, 169 - 5, 20 + 4));
        step(5, mc -> clickTab(mc, 1));
        step(15, mc -> shot("greenhouse_t3_operations"));

        step(5, mc -> hoverTab(mc, 1, 20, 10 + 4));
        step(10, mc -> shot("greenhouse_t3_energy_tooltip"));
        step(5, mc -> hover(mc, 0.0, 0.0));
        step(5, mc -> clickTab(mc, 3));
        step(15, mc -> shot("greenhouse_t3_control"));
        step(5, mc -> clickTab(mc, 1));
        step(5, mc -> clickTab(mc, 3));
        step(15, mc -> shot("greenhouse_t3_tabs_closed"));
        step(5, mc -> mc.setScreen(null));

        step(10, mc -> command(mc, "tp @s " + (big.getX() + 0.5) + " " + (ORIGIN.getY() + 0.5) + " " + (big.getZ() - 5.5) + " 0 10"));
        step(40, mc -> shot("greenhouse_jade"));

        step(10, mc -> command(mc, "tp @s " + (big.getX() - 6) + " " + (ORIGIN.getY() + 4) + " " + (big.getZ() - 8) + " -35 25"));
        step(40, mc -> shot("greenhouse_world"));

        step(5, mc -> {
            if (net.neoforged.fml.ModList.get().isLoaded("jei")) dev.drimoz.yafs.client.compat.jei.YafsJeiPlugin.showCrops();
        });
        step(30, mc -> shot("jei_crops"));
        step(10, Minecraft::stop);
    }

    private static void build(ServerLevel level, BlockPos master, GreenhouseBlock block, List<Item> modules, int energy) {
        level.setBlock(master, block.defaultBlockState().setValue(MultiblockBlock.FACING, Direction.NORTH), 3);
        block.placeParts(level, master, Direction.NORTH);
        if (!(level.getBlockEntity(master) instanceof GreenhouseBlockEntity greenhouse)) return;

        for (int plot = 0; plot < greenhouse.plotCount(); plot++) {
            if (plot % 5 == 4) continue;
            greenhouse.setPlotItem(plot, GreenhouseBlockEntity.CROP, new ItemStack(plot % 3 == 0 ? Items.CARROT : Items.WHEAT_SEEDS));
            greenhouse.setPlotItem(plot, GreenhouseBlockEntity.SUBSTRATE, new ItemStack(plot == 2 ? Items.SAND : Items.DIRT));
        }
        greenhouse.water().fill(new FluidStack(Fluids.WATER, 2_000), IFluidHandler.FluidAction.EXECUTE);
        for (int tick = 0; tick < 3; tick++) greenhouse.grow(GreenhouseBlockEntity.STEP * 20);

        for (int slot = 0; slot < modules.size() && slot < greenhouse.modules().getSlots(); slot++) {
            greenhouse.modules().setStackInSlot(slot, new ItemStack(modules.get(slot)));
        }
        greenhouse.water().fill(new FluidStack(Fluids.WATER, 6_000), IFluidHandler.FluidAction.EXECUTE);
        for (int given = 0; given < energy; given += 100_000) greenhouse.energy().receiveEnergy(100_000, false);
        greenhouse.output().setStackInSlot(0, new ItemStack(Items.WHEAT, 23));
        greenhouse.fertilizer().setStackInSlot(0, new ItemStack(Items.BONE_MEAL, 12));
        greenhouse.output().setStackInSlot(1, new ItemStack(Items.WHEAT_SEEDS, 41));
    }

    static void fly(Minecraft minecraft) {
        minecraft.player.getAbilities().flying = true;
        minecraft.player.onUpdateAbilities();
    }

    static void step(int delay, Consumer<Minecraft> action) {
        STEPS.add(new Step(delay, action));
    }

    static void shot(String name) {
        pendingShot = name;
    }

    static void onServer(Minecraft minecraft, Consumer<ServerLevel> action) {
        var server = minecraft.getSingleplayerServer();
        var dimension = minecraft.player.level().dimension();
        server.execute(() -> {
            try {
                action.accept(server.getLevel(dimension));
                LOGGER.info("Aperçu : serres posées dans {}", dimension.location());
            } catch (RuntimeException e) {
                LOGGER.error("Aperçu : serres impossibles à poser", e);
            }
        });
    }

    static void command(Minecraft minecraft, String command) {
        var server = minecraft.getSingleplayerServer();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(minecraft.player.getUUID());
            if (player != null) {
                server.getCommands().performPrefixedCommand(player.createCommandSourceStack().withPermission(4), command);
            }
        });
    }

    static void use(Minecraft minecraft, BlockPos pos) {
        minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    }

    static void clickTab(Minecraft minecraft, int index) {
        if (minecraft.screen instanceof dev.drimoz.yafs.client.greenhouse.GreenhouseScreen screen) {
            var area = screen.tabAreas().get(index);
            screen.mouseClicked(area.getX() + 11, area.getY() + 11, 0);
        }
    }

    static void clickTabContent(Minecraft minecraft, int index, int x, int y) {
        if (minecraft.screen instanceof dev.drimoz.yafs.client.greenhouse.GreenhouseScreen screen) {
            var area = screen.tabAreas().get(index);
            screen.mouseClicked(area.getX() + 6 + x, area.getY() + 22 + y, 0);
        }
    }

    static void clickGui(Minecraft minecraft, int x, int y) {
        if (minecraft.screen instanceof AbstractContainerScreen<?> screen) {
            screen.mouseClicked(screen.getGuiLeft() + x, screen.getGuiTop() + y, 0);
        }
    }

    static void hoverGui(Minecraft minecraft, int x, int y) {
        if (!(minecraft.screen instanceof AbstractContainerScreen<?> screen)) return;
        double scale = minecraft.getWindow().getGuiScale();
        hover(minecraft, (screen.getGuiLeft() + x) * scale / minecraft.getWindow().getScreenWidth(),
                (screen.getGuiTop() + y) * scale / minecraft.getWindow().getScreenHeight());
    }

    static void hoverTab(Minecraft minecraft, int index, int x, int y) {
        if (!(minecraft.screen instanceof dev.drimoz.yafs.client.greenhouse.GreenhouseScreen screen)) return;
        var area = screen.tabAreas().get(index);
        double scale = minecraft.getWindow().getGuiScale();
        hover(minecraft, (area.getX() + 6 + x) * scale / minecraft.getWindow().getScreenWidth(),
                (area.getY() + 22 + y) * scale / minecraft.getWindow().getScreenHeight());
    }

    static void hover(Minecraft minecraft, double fx, double fy) {
        try {
            MouseHandler mouse = minecraft.mouseHandler;
            Field x = MouseHandler.class.getDeclaredField("xpos");
            Field y = MouseHandler.class.getDeclaredField("ypos");
            x.setAccessible(true);
            y.setAccessible(true);
            x.setDouble(mouse, minecraft.getWindow().getScreenWidth() * fx);
            y.setDouble(mouse, minecraft.getWindow().getScreenHeight() * fy);
        } catch (ReflectiveOperationException e) {
            LOGGER.error("Curseur inaccessible", e);
        }
    }

    private static void copyWorld(Minecraft minecraft) {
        Path saves = minecraft.gameDirectory.toPath().resolve("saves");
        Path source = saves.resolve(SOURCE_WORLD);
        Path target = saves.resolve(WORLD);
        try {
            if (Files.exists(target)) {
                try (Stream<Path> walk = Files.walk(target)) {
                    walk.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
                }
            }
            try (Stream<Path> walk = Files.walk(source)) {
                for (Path path : (Iterable<Path>) walk::iterator) {
                    if (path.getFileName().toString().equals("session.lock")) continue;
                    Path copy = target.resolve(source.relativize(path).toString());
                    if (Files.isDirectory(path)) Files.createDirectories(copy);
                    else Files.copy(path, copy);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Copie du monde d'aperçu impossible", e);
        }
    }
}

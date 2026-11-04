package dev.drimoz.yafs.gametest;

import com.mojang.logging.LogUtils;
import dev.drimoz.yafs.client.greenhouse.GreenhouseRenderer;
import dev.drimoz.yafs.content.greenhouse.GreenhouseBlockEntity;
import dev.drimoz.yafs.content.multiblock.MultiblockBlock;
import dev.drimoz.yafs.registry.YafsBlocks;
import dev.drimoz.yafs.registry.YafsItems;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class PerfBench {

    static final boolean ENABLED = "1".equals(System.getenv("YAFS_PERF_BENCH"));

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final BlockPos ORIGIN = new BlockPos(3000, 250, 3000);
    private static final int COLUMNS = 10;
    private static final int ROWS = 5;
    private static final int SPACING = 9;
    private static final int SERVER_TICKS = 2_000;
    private static final int RENDER_TICKS = 200;

    private static final List<BlockPos> MASTERS = new ArrayList<>();
    private static final StringBuilder REPORT = new StringBuilder();
    private static long frameStart;
    private static int framesAtStart;

    private PerfBench() {}

    static void script() {
        for (int row = 0; row < ROWS; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                MASTERS.add(ORIGIN.offset(column * SPACING, 0, row * SPACING));
            }
        }
        BlockPos centre = ORIGIN.offset(COLUMNS * SPACING / 2, 0, ROWS * SPACING / 2);

        GuiPreview.step(60, mc -> GuiPreview.command(mc, "gamemode creative"));
        GuiPreview.step(5, mc -> GuiPreview.command(mc, "time set noon"));
        GuiPreview.step(5, mc -> GuiPreview.command(mc, "weather clear"));
        GuiPreview.step(5, GuiPreview::fly);
        GuiPreview.step(5, mc -> {

            mc.options.enableVsync().set(false);
            mc.options.framerateLimit().set(260);
            GuiPreview.command(mc, "tp @s " + centre.getX() + " " + (ORIGIN.getY() + 20) + " " + centre.getZ());
        });
        GuiPreview.step(80, mc -> GuiPreview.command(mc, "fill " + (ORIGIN.getX() - 4) + " " + (ORIGIN.getY() - 1) + " " + (ORIGIN.getZ() - 4) + " "
                + (ORIGIN.getX() + COLUMNS * SPACING) + " " + (ORIGIN.getY() - 1) + " " + (ORIGIN.getZ() + ROWS * SPACING) + " minecraft:smooth_stone"));
        GuiPreview.step(20, mc -> GuiPreview.onServer(mc, PerfBench::build));
        GuiPreview.step(40, mc -> GuiPreview.onServer(mc, PerfBench::measureServer));

        GuiPreview.step(20, mc -> GuiPreview.command(mc, "tp @s " + centre.getX() + " " + (ORIGIN.getY() + 18) + " " + (ORIGIN.getZ() + ROWS * SPACING + 14) + " 180 40"));
        GuiPreview.step(5, GuiPreview::fly);
        GuiPreview.step(100, PerfBench::startRender);
        GuiPreview.step(RENDER_TICKS, PerfBench::stopRender);
        GuiPreview.step(5, mc -> GuiPreview.shot("perf_field"));
        GuiPreview.step(20, mc -> write(mc));
        GuiPreview.step(10, Minecraft::stop);
    }

    private static void build(ServerLevel level) {
        for (BlockPos master : MASTERS) {
            level.setBlock(master, YafsBlocks.GREENHOUSE_T3.get().defaultBlockState().setValue(MultiblockBlock.FACING, Direction.NORTH), 3);
            YafsBlocks.GREENHOUSE_T3.get().placeParts(level, master, Direction.NORTH);
            if (!(level.getBlockEntity(master) instanceof GreenhouseBlockEntity greenhouse)) continue;

            for (int plot = 0; plot < greenhouse.plotCount(); plot++) {
                greenhouse.setPlotItem(plot, GreenhouseBlockEntity.CROP, new ItemStack(Items.WHEAT_SEEDS));
                greenhouse.setPlotItem(plot, GreenhouseBlockEntity.SUBSTRATE, new ItemStack(Items.DIRT));
            }
            greenhouse.modules().setStackInSlot(0, new ItemStack(YafsItems.ACCELERATOR_3.get(), 4));
            greenhouse.modules().setStackInSlot(1, new ItemStack(YafsItems.DENSITY_3.get()));
            greenhouse.modules().setStackInSlot(2, new ItemStack(YafsItems.GROW_LAMP.get()));
            refill(greenhouse);
        }
    }

    private static void measureServer(ServerLevel level) {
        List<GreenhouseBlockEntity> greenhouses = new ArrayList<>();
        for (BlockPos master : MASTERS) {
            if (level.getBlockEntity(master) instanceof GreenhouseBlockEntity greenhouse) greenhouses.add(greenhouse);
        }

        long nanos = 0L;
        long harvested = 0L;
        for (int tick = -200; tick < SERVER_TICKS; tick++) {
            for (GreenhouseBlockEntity greenhouse : greenhouses) {
                if (tick % 20 == 0) harvested += empty(greenhouse);
                refill(greenhouse);
            }
            long start = System.nanoTime();
            for (GreenhouseBlockEntity greenhouse : greenhouses) greenhouse.serverTick();
            if (tick >= 0) nanos += System.nanoTime() - start;
        }

        double perTick = nanos / 1e6 / SERVER_TICKS;
        line("Serveur : %d serres P3, %d ticks", greenhouses.size(), SERVER_TICKS);
        line("  %.3f ms par tick pour toutes (%.1f %% d'un tick de 50 ms)", perTick, perTick / 50 * 100);
        line("  %.1f µs par serre et par tick", perTick * 1000 / Math.max(1, greenhouses.size()));
        line("  %d items récoltés", harvested);
    }

    private static void refill(GreenhouseBlockEntity greenhouse) {
        greenhouse.water().fill(new FluidStack(Fluids.WATER, GreenhouseBlockEntity.TANK_CAPACITY), IFluidHandler.FluidAction.EXECUTE);
        while (greenhouse.energy().receiveEnergy(Integer.MAX_VALUE, false) > 0) {

        }
    }

    private static int empty(GreenhouseBlockEntity greenhouse) {
        int count = 0;
        for (int slot = 0; slot < GreenhouseBlockEntity.OUTPUT_SLOTS; slot++) {
            count += greenhouse.output().getStackInSlot(slot).getCount();
            greenhouse.output().setStackInSlot(slot, ItemStack.EMPTY);
        }
        return count;
    }

    private static void startRender(Minecraft minecraft) {
        GreenhouseRenderer.resetProfile();
        frameStart = System.nanoTime();
        framesAtStart = GuiPreview.frames;
    }

    private static void stopRender(Minecraft minecraft) {
        line("Caméra en %s", minecraft.player.blockPosition().toShortString());
        double seconds = (System.nanoTime() - frameStart) / 1e9;
        int frames = Math.max(1, GuiPreview.frames - framesAtStart);
        line("Rendu : %d images en %.1f s (%.0f img/s)", frames, seconds, frames / seconds);
        line("  %.3f ms par image dans le rendu des serres", GreenhouseRenderer.nanos() / 1e6 / frames);
        line("  %.1f serres dessinées par image (contour seul : les plantes sont dans le maillage du chunk)", (double) GreenhouseRenderer.calls() / frames);
    }

    private static void line(String format, Object... arguments) {
        String text = String.format(Locale.ROOT, format, arguments);
        LOGGER.info("Banc YAF-041 : {}", text);
        REPORT.append(text).append('\n');
    }

    private static void write(Minecraft minecraft) {
        Path file = minecraft.gameDirectory.toPath().resolve("perf/bench.txt");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, REPORT.toString());
        } catch (IOException e) {
            LOGGER.error("Banc : rapport impossible à écrire", e);
        }
    }
}

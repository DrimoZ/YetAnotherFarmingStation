package dev.drimoz.yafs.client.multiblock;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.drimoz.yafs.YetAnotherFarmingStation;
import dev.drimoz.yafs.content.multiblock.MultiblockBlock;
import dev.drimoz.yafs.content.multiblock.MultiblockPartBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import javax.annotation.Nullable;

/**
 * Contour du volume qu'occupera un multibloc, tant que le joueur en tient un : vert si la pose
 * passera, rouge sinon. Sans lui, poser un bloc de 3×3 revient à viser à l'aveugle.
 *
 * <p>Le verdict vient de {@link MultiblockBlock#canPlace}, la méthode même qui décide de la
 * pose : l'aperçu ne peut pas promettre ce que le serveur refusera. Porté de FactoryIO.
 *
 * <p>Viser un multibloc qu'on peut agrandir montre le volume agrandi, verdict de
 * {@link MultiblockBlock#canGrowInto} (YAF-006).
 */
@EventBusSubscriber(modid = YetAnotherFarmingStation.MOD_ID, value = Dist.CLIENT)
public final class MultiblockPlacementPreview {

    private MultiblockPlacementPreview() {}

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || !(minecraft.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) return;

        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof BlockItem item) || !(item.getBlock() instanceof MultiblockBlock block)) return;

        boolean valid;
        AABB bounds;

        BlockPos master = masterOf(minecraft.level, hit.getBlockPos());
        BlockState target = master != null ? minecraft.level.getBlockState(master) : null;
        if (target != null && target.getBlock() instanceof MultiblockBlock smaller && smaller.growsInto(block)) {
            // Viser un multibloc plus petit avec son successeur : le volume agrandi, autour du même maître.
            Direction facing = target.getValue(MultiblockBlock.FACING);
            valid = smaller.canGrowInto(minecraft.level, master, facing, block.shape());
            bounds = block.shape().bounds(master, facing).inflate(0.002D);
        } else {
            BlockPlaceContext context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hit);
            if (!context.canPlace()) return;

            Direction facing = context.getHorizontalDirection().getOpposite();
            valid = block.canPlace(context, facing);
            bounds = block.shape().bounds(context.getClickedPos(), facing).inflate(0.002D);
        }

        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);

        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        LevelRenderer.renderLineBox(poseStack, buffers.getBuffer(RenderType.lines()), bounds,
                valid ? 0.3F : 1.0F, valid ? 1.0F : 0.25F, valid ? 0.3F : 0.25F, 0.9F);
        buffers.endBatch(RenderType.lines());

        poseStack.popPose();
    }

    /** Le maître du multibloc visé, qu'on vise le maître ou une partie ; {@code null} sinon. */
    @Nullable
    private static BlockPos masterOf(Level level, BlockPos pos) {
        if (level.getBlockState(pos).getBlock() instanceof MultiblockBlock) return pos;
        return level.getBlockEntity(pos) instanceof MultiblockPartBlockEntity part ? part.masterPos() : null;
    }
}

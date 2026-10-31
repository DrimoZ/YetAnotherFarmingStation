package dev.drimoz.yafs.client.greenhouse;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.drimoz.yafs.content.greenhouse.GreenhouseBlock;
import dev.drimoz.yafs.content.greenhouse.GreenhouseBlockEntity;
import dev.drimoz.yafs.content.greenhouse.GreenhouseTier;
import dev.drimoz.yafs.content.multiblock.MultiblockBlock;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;

/**
 * Ce qui reste à dessiner à chaque image : le contour du volume des paliers sans verre. Les
 * plantes sont cuites dans le maillage du chunk, voir {@link GreenhouseModel}.
 */
public class GreenhouseRenderer implements BlockEntityRenderer<GreenhouseBlockEntity> {

    // Profil du banc YAF-041 (YAFS_PERF_BENCH=1) : en jeu normal, rien n'est compté.
    private static final boolean PROFILE = "1".equals(System.getenv("YAFS_PERF_BENCH"));
    private static long nanos;
    private static long calls;

    public GreenhouseRenderer(BlockEntityRendererProvider.Context context) {}

    public static void resetProfile() {
        nanos = 0L;
        calls = 0L;
    }

    public static long nanos() {
        return nanos;
    }

    public static long calls() {
        return calls;
    }

    @Override
    public void render(GreenhouseBlockEntity greenhouse, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        long start = PROFILE ? System.nanoTime() : 0L;
        // ponytail: au-delà de P1, pas de verre dessiné ; un contour dit le volume (YAF-090).
        if (greenhouse.tier() != GreenhouseTier.I) {
            AABB bounds = getRenderBoundingBox(greenhouse).move(-greenhouse.getBlockPos().getX(), -greenhouse.getBlockPos().getY(), -greenhouse.getBlockPos().getZ());
            LevelRenderer.renderLineBox(poseStack, buffers.getBuffer(RenderType.lines()), bounds, 0.75F, 0.9F, 0.75F, 0.6F);
        }
        if (PROFILE) {
            nanos += System.nanoTime() - start;
            calls++;
        }
    }

    /** Le volume entier : sans quoi le contour disparaîtrait dès que le maître sort de l'écran. */
    @Override
    public AABB getRenderBoundingBox(GreenhouseBlockEntity greenhouse) {
        GreenhouseBlock block = (GreenhouseBlock) greenhouse.getBlockState().getBlock();
        return block.shape().bounds(greenhouse.getBlockPos(), greenhouse.getBlockState().getValue(MultiblockBlock.FACING));
    }
}

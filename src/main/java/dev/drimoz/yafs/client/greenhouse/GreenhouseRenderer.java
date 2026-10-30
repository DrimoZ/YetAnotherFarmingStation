package dev.drimoz.yafs.client.greenhouse;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.drimoz.yafs.content.greenhouse.GreenhouseBlock;
import dev.drimoz.yafs.content.greenhouse.GreenhouseBlockEntity;
import dev.drimoz.yafs.content.greenhouse.GreenhouseTier;
import dev.drimoz.yafs.content.multiblock.MultiblockBlock;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Le maître dessine les parcelles : un carré de substrat et, dessus, la culture au stade de sa
 * croissance, avec le modèle du jeu. Prototype de YAF-040 : grille du palier, aucun
 * niveau de détail selon la distance (YAF-041). Le type de rendu {@code null} laisse chaque modèle
 * choisir le sien (cutout pour les cultures).
 */
public class GreenhouseRenderer implements BlockEntityRenderer<GreenhouseBlockEntity> {

    /** Marge entre le bord du volume et la grille, en blocs : 1,2 bloc par case en P1, 1,6 en P3. */
    private static final float MARGIN = 0.6F;
    /** Les plantes poussent sur le socle, la couche du bas, qui est un bloc plein. */
    private static final float FLOOR = 1F;
    private static final float SOIL = 0.25F;

    private final BlockRenderDispatcher blocks;

    public GreenhouseRenderer(BlockEntityRendererProvider.Context context) {
        this.blocks = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(GreenhouseBlockEntity greenhouse, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        GreenhouseTier tier = greenhouse.tier();
        int side = tier.plotSide();
        float spacing = (tier.shape().width() - MARGIN) / side;

        // ponytail: au-delà de P1, pas de verre dessiné ; un contour dit le volume (YAF-090).
        if (tier != GreenhouseTier.I) outline(greenhouse, poseStack, buffers);

        for (int plot = 0; plot < greenhouse.plotCount(); plot++) {
            BlockState crop = greenhouse.displayedState(plot);
            if (crop == null) continue;

            poseStack.pushPose();
            poseStack.translate((plot % side - (side - 1) / 2F) * spacing, FLOOR, (plot / side - (side - 1) / 2F) * spacing);

            ItemStack substrate = greenhouse.plots().getStackInSlot(plot * GreenhouseBlockEntity.SLOTS_PER_PLOT + GreenhouseBlockEntity.SUBSTRATE);
            if (substrate.getItem() instanceof BlockItem soil) {
                poseStack.pushPose();
                poseStack.scale(1F, SOIL, 1F);
                this.blocks.renderSingleBlock(soil.getBlock().defaultBlockState(), poseStack, buffers, packedLight, packedOverlay, ModelData.EMPTY, null);
                poseStack.popPose();
            }

            poseStack.translate(0F, SOIL, 0F);
            this.blocks.renderSingleBlock(crop, poseStack, buffers, packedLight, packedOverlay, ModelData.EMPTY, null);
            poseStack.popPose();
        }
    }

    private void outline(GreenhouseBlockEntity greenhouse, PoseStack poseStack, MultiBufferSource buffers) {
        AABB bounds = getRenderBoundingBox(greenhouse).move(-greenhouse.getBlockPos().getX(), -greenhouse.getBlockPos().getY(), -greenhouse.getBlockPos().getZ());
        LevelRenderer.renderLineBox(poseStack, buffers.getBuffer(RenderType.lines()), bounds, 0.75F, 0.9F, 0.75F, 0.6F);
    }

    /** Le volume entier : sans quoi les plantes disparaîtraient dès que le maître sort de l'écran. */
    @Override
    public AABB getRenderBoundingBox(GreenhouseBlockEntity greenhouse) {
        GreenhouseBlock block = (GreenhouseBlock) greenhouse.getBlockState().getBlock();
        return block.shape().bounds(greenhouse.getBlockPos(), greenhouse.getBlockState().getValue(MultiblockBlock.FACING));
    }
}

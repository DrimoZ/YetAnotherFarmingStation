package dev.drimoz.yafs.client.greenhouse;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.drimoz.yafs.content.greenhouse.GreenhouseBlock;
import dev.drimoz.yafs.content.greenhouse.GreenhouseBlockEntity;
import dev.drimoz.yafs.content.multiblock.MultiblockBlock;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Le maître dessine les parcelles : un carré de substrat et, dessus, la culture au stade de sa
 * croissance, avec le modèle du jeu. Prototype de YAF-040 : grille 2×2 de la serre P1, aucun
 * niveau de détail selon la distance (YAF-041). Le type de rendu {@code null} laisse chaque modèle
 * choisir le sien (cutout pour les cultures).
 */
public class GreenhouseRenderer implements BlockEntityRenderer<GreenhouseBlockEntity> {

    /** Décalage des centres de case par rapport au centre de la serre, en blocs. */
    private static final float CELL = 0.6F;
    /** Le dessus du socle du modèle provisoire : 2 pixels. */
    private static final float FLOOR = 2F / 16F;
    private static final float SOIL = 0.25F;

    private final BlockRenderDispatcher blocks;

    public GreenhouseRenderer(BlockEntityRendererProvider.Context context) {
        this.blocks = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(GreenhouseBlockEntity greenhouse, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        for (int plot = 0; plot < GreenhouseBlockEntity.PLOTS; plot++) {
            ItemStack crop = greenhouse.plots().getStackInSlot(plot * GreenhouseBlockEntity.SLOTS_PER_PLOT + GreenhouseBlockEntity.CROP);
            if (!(crop.getItem() instanceof BlockItem cropItem)) continue;

            poseStack.pushPose();
            poseStack.translate(plot % 2 == 0 ? -CELL : CELL, FLOOR, plot / 2 == 0 ? -CELL : CELL);

            ItemStack substrate = greenhouse.plots().getStackInSlot(plot * GreenhouseBlockEntity.SLOTS_PER_PLOT + GreenhouseBlockEntity.SUBSTRATE);
            if (substrate.getItem() instanceof BlockItem soil) {
                poseStack.pushPose();
                poseStack.scale(1F, SOIL, 1F);
                this.blocks.renderSingleBlock(soil.getBlock().defaultBlockState(), poseStack, buffers, packedLight, packedOverlay, ModelData.EMPTY, null);
                poseStack.popPose();
            }

            poseStack.translate(0F, SOIL, 0F);
            this.blocks.renderSingleBlock(GreenhouseBlockEntity.displayedState(cropItem.getBlock(), greenhouse.growth(plot)),
                    poseStack, buffers, packedLight, packedOverlay, ModelData.EMPTY, null);
            poseStack.popPose();
        }
    }

    /** Le volume entier : sans quoi les plantes disparaîtraient dès que le maître sort de l'écran. */
    @Override
    public AABB getRenderBoundingBox(GreenhouseBlockEntity greenhouse) {
        GreenhouseBlock block = (GreenhouseBlock) greenhouse.getBlockState().getBlock();
        return block.shape().bounds(greenhouse.getBlockPos(), greenhouse.getBlockState().getValue(MultiblockBlock.FACING));
    }
}

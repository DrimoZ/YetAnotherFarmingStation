package dev.drimoz.yafs.client.greenhouse;

import com.mojang.math.Transformation;
import dev.drimoz.yafs.content.greenhouse.GreenhouseBlock;
import dev.drimoz.yafs.content.greenhouse.GreenhouseBlockEntity;
import dev.drimoz.yafs.content.greenhouse.GreenhouseTier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.QuadTransformers;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.common.util.TriState;
import org.joml.Matrix4f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Le modèle du maître, plus les parcelles : un carré de substrat et la culture au stade de sa
 * croissance, avec les modèles du jeu. Cuit dans le maillage du chunk (YAF-041) : rien ne se
 * redessine d'une image à l'autre, le chunk ne se reconstruit que quand un stade change.
 *
 * <p>Tout va dans la liste sans face ({@code side == null}) : une plante n'est cachée par aucun
 * voisin, et une face « vers le nord » d'une parcelle n'a rien à voir avec le nord du maître.
 * L'éclairage est celui du maître, à plat.
 *
 * <p>ponytail: la teinte suit le maître, pas la culture ; une tige de melon sort blanche. Une
 * {@code BlockColor} par parcelle si une culture teintée compte.
 */
public class GreenhouseModel extends BakedModelWrapper<BakedModel> {

    /** Marge entre le bord du volume et la grille, en blocs : 1,2 bloc par case en P1, 1,6 en P3. */
    private static final float MARGIN = 0.6F;
    /** Les plantes poussent sur le socle, la couche du bas, qui est un bloc plein. */
    private static final float FLOOR = 1F;
    private static final float SOIL = 0.25F;
    private static final Direction[] SIDES = Direction.values();

    public GreenhouseModel(BakedModel original) {
        super(original);
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData data, @Nullable RenderType renderType) {
        List<BakedQuad> base = super.getQuads(state, side, rand, data, renderType);
        GreenhouseBlockEntity.Plants plants = data.get(GreenhouseBlockEntity.PLANTS);
        if (side != null || plants == null || state == null || !(state.getBlock() instanceof GreenhouseBlock block)) return base;

        List<BakedQuad> quads = new ArrayList<>(base);
        GreenhouseTier tier = block.tier();
        int grid = tier.plotSide();
        float spacing = (tier.shape().width() - MARGIN) / grid;
        for (int plot = 0; plot < plants.crops().length; plot++) {
            BlockState crop = plants.crops()[plot];
            if (crop == null) continue;
            float x = (plot % grid - (grid - 1) / 2F) * spacing;
            float z = (plot / grid - (grid - 1) / 2F) * spacing;

            BlockState soil = plants.soils()[plot];
            if (soil != null) add(quads, soil, new Matrix4f().translate(x, FLOOR, z).scale(1F, SOIL, 1F), rand, renderType);
            add(quads, crop, new Matrix4f().translate(x, FLOOR + SOIL, z), rand, renderType);
        }
        return quads;
    }

    /** Les quads de {@code state} pour cette passe de rendu, déplacés par {@code matrix}. */
    private static void add(List<BakedQuad> into, BlockState state, Matrix4f matrix, RandomSource rand, @Nullable RenderType renderType) {
        BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
        if (renderType != null && !model.getRenderTypes(state, rand, ModelData.EMPTY).contains(renderType)) return;

        var transform = QuadTransformers.applying(new Transformation(matrix));
        for (Direction direction : SIDES) {
            for (BakedQuad quad : model.getQuads(state, direction, rand, ModelData.EMPTY, renderType)) into.add(transform.process(quad));
        }
        for (BakedQuad quad : model.getQuads(state, null, rand, ModelData.EMPTY, renderType)) into.add(transform.process(quad));
    }

    /**
     * Pas d'occlusion ambiante : elle échantillonne les voisins du maître, pas ceux d'une parcelle
     * à trois blocs de là, et noircissait les plantes. Les cultures vanilla s'en passent aussi.
     */
    @Override
    public TriState useAmbientOcclusion(BlockState state, ModelData data, RenderType renderType) {
        return TriState.FALSE;
    }

    /** Les passes du maître, plus celles de ses cultures et substrats (cutout, le plus souvent). */
    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
        ChunkRenderTypeSet own = super.getRenderTypes(state, rand, data);
        GreenhouseBlockEntity.Plants plants = data.get(GreenhouseBlockEntity.PLANTS);
        if (plants == null) return own;

        List<ChunkRenderTypeSet> sets = new ArrayList<>();
        sets.add(own);
        for (BlockState[] states : List.of(plants.crops(), plants.soils())) {
            for (BlockState planted : states) {
                if (planted != null) sets.add(Minecraft.getInstance().getBlockRenderer().getBlockModel(planted).getRenderTypes(planted, rand, ModelData.EMPTY));
            }
        }
        return ChunkRenderTypeSet.union(sets);
    }
}

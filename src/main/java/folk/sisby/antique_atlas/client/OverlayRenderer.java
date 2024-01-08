package folk.sisby.antique_atlas.client;

import com.mojang.blaze3d.systems.RenderSystem;
import folk.sisby.antique_atlas.api.AtlasAPI;
import folk.sisby.antique_atlas.client.api.AtlasClientAPI;
import folk.sisby.antique_atlas.AntiqueAtlas;
import folk.sisby.antique_atlas.client.gui.GuiAtlas;
import folk.sisby.antique_atlas.client.gui.tiles.SubTile;
import folk.sisby.antique_atlas.client.gui.tiles.SubTileQuartet;
import folk.sisby.antique_atlas.client.gui.tiles.TileRenderIterator;
import folk.sisby.antique_atlas.client.resource.MarkerRenderInfo;
import folk.sisby.antique_atlas.client.resource.MarkerType;
import folk.sisby.antique_atlas.client.resource.TileTextureMap;
import folk.sisby.antique_atlas.core.WorldData;
import folk.sisby.antique_atlas.marker.DimensionMarkersData;
import folk.sisby.antique_atlas.marker.Marker;
import folk.sisby.antique_atlas.marker.MarkersData;
import folk.sisby.antique_atlas.util.Rect;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Quaternionf;
import org.lwjgl.opengl.GL11;

import java.util.List;

public class OverlayRenderer {
    /**
     * Number of blocks per chunk in minecraft. This is certianly stored
     * somewhere else, but I couldn't be bothered to find it.
     */
    private static final int CHUNK_SIZE = 16;
    private static final float INNER_ELEMENTS_SCALE_FACTOR = 1.9F;
    private PlayerEntity player;
    private World world;

    public void drawOverlay(MatrixStack matrices, VertexConsumerProvider vertexConsumer, int light) {
        // Overlay must close if Atlas GUI is opened
        if (MinecraftClient.getInstance().currentScreen instanceof GuiAtlas) {
            return;
        }

        if (MinecraftClient.getInstance().world == null || MinecraftClient.getInstance().player == null) {
            return;
        }

        this.player = MinecraftClient.getInstance().player;
        this.world = MinecraftClient.getInstance().world;

        drawMinimap(matrices, AtlasAPI.getPlayerAtlasId(player), vertexConsumer, light);
    }

    private void drawMinimap(MatrixStack matrices, int atlasID, VertexConsumerProvider buffer, int light) {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        matrices.push();
        matrices.translate(0, 0, 0.01);
        AntiqueAtlasTextures.BOOK.drawWithLight(buffer, matrices, 0, 0, (int) (GuiAtlas.WIDTH * 1.5), (int) (GuiAtlas.HEIGHT * 1.5), light);
        matrices.pop();

        matrices.push();
        matrices.scale(INNER_ELEMENTS_SCALE_FACTOR, INNER_ELEMENTS_SCALE_FACTOR, 1F);


        drawTiles(buffer, matrices, atlasID, light);
        matrices.translate(0, 0, -0.01);
        if (AntiqueAtlas.CONFIG.Appearance.markerSize > 0) {
            drawMarkers(buffer, matrices, atlasID, light);
        }
        matrices.pop();

        matrices.translate(0, 0, -0.02);
        drawPlayer(buffer, matrices, light);

        // Overlay the frame so that edges of the map are smooth:
        matrices.translate(0, 0, -0.01);
        AntiqueAtlasTextures.BOOK_FRAME.drawWithLight(buffer, matrices, 0, 0, (int) (GuiAtlas.WIDTH * 1.5), (int) (GuiAtlas.HEIGHT * 1.5), light);

        RenderSystem.disableBlend();
    }

    private void drawTiles(VertexConsumerProvider buffer, MatrixStack matrices, int atlasID, int light) {
        Rect iteratorScope = getChunkCoverage(player.getPos());
        TileRenderIterator iter = AtlasClientAPI.getTileAPI().getTiles(world, atlasID, iteratorScope, 1);

        Vec3d chunkPosition = player.getPos().multiply(1D / CHUNK_SIZE, 1D / CHUNK_SIZE, 1D / CHUNK_SIZE);
        int shapeMiddleX = (int) ((GuiAtlas.WIDTH * 1.5F) / (INNER_ELEMENTS_SCALE_FACTOR * 2));
        int shapeMiddleY = (int) ((GuiAtlas.HEIGHT * 1.5F) / (INNER_ELEMENTS_SCALE_FACTOR * 2));
        SetTileRenderer renderer = new SetTileRenderer(buffer, matrices, AntiqueAtlas.CONFIG.Appearance.tileSize / 2, light);

        while (iter.hasNext()) {
            SubTileQuartet subtiles = iter.next();
            for (SubTile subtile : subtiles) {
                if (subtile == null || subtile.tile == null)
                    continue;
                // Position of this subtile (measured in chunks) relative to the
                // player
                float relativeChunkPositionX = (float) (subtile.x / 2.0
                    + iteratorScope.minX - chunkPosition.x);
                float relativeChunkPositionY = (float) (subtile.y / 2.0
                    + iteratorScope.minY - chunkPosition.z);
                renderer.addTileCorner(
                    TileTextureMap.instance().getTexture(subtile).getTexture(),
                    shapeMiddleX
                        + (int) Math.floor(relativeChunkPositionX
                        * AntiqueAtlas.CONFIG.Appearance.tileSize),
                    shapeMiddleY
                        + (int) Math.floor(relativeChunkPositionY
                        * AntiqueAtlas.CONFIG.Appearance.tileSize), subtile.getTextureU(),
                    subtile.getTextureV());
            }
        }
        renderer.draw();
    }

    private void drawMarkers(VertexConsumerProvider buffer, MatrixStack matrices, int atlasID, int light) {
        // biomeData needed to prevent undiscovered markers from appearing
        WorldData biomeData = AntiqueAtlas.tileData.getData(
            atlasID, this.world).getWorldData(
            this.world.getRegistryKey());
        DimensionMarkersData globalMarkersData = AntiqueAtlas.globalMarkersData
            .getData().getMarkersDataInWorld(this.world.getRegistryKey());

        // Draw global markers:
        drawMarkersData(buffer, matrices, globalMarkersData, biomeData, light);

        MarkersData markersData = AntiqueAtlas.markersData.getMarkersData(
            atlasID, MinecraftClient.getInstance().world);
        if (markersData != null) {
            DimensionMarkersData localMarkersData = markersData.getMarkersDataInWorld(world.getRegistryKey());

            // Draw local markers:
            drawMarkersData(buffer, matrices, localMarkersData, biomeData, light);
        }
    }

    private void drawPlayer(VertexConsumerProvider buffer, MatrixStack matrices, int light) {
        matrices.push();

        matrices.translate((int) ((GuiAtlas.WIDTH * 1.5F) / 2F), (int) ((GuiAtlas.HEIGHT * 1.5F) / 2F), 0);
        matrices.multiply(new Quaternionf(RotationAxis.POSITIVE_Z.rotationDegrees(this.player.getHeadYaw() + 180)));
        matrices.translate(-AntiqueAtlas.CONFIG.Appearance.playerIconWidth / 2.0, -AntiqueAtlas.CONFIG.Appearance.playerIconHeight / 2.0, 0);

        AntiqueAtlasTextures.PLAYER.drawWithLight(buffer, matrices, 0, 0, AntiqueAtlas.CONFIG.Appearance.playerIconWidth, AntiqueAtlas.CONFIG.Appearance.playerIconHeight, light);
        matrices.pop();
    }

    private void drawMarkersData(VertexConsumerProvider buffer, MatrixStack matrices, DimensionMarkersData markersData, WorldData biomeData, int light) {
        //this will be large enough to include markers that are larger than tiles
        Rect mcchunks = getChunkCoverage(player.getPos());
        Rect chunks = new Rect(mcchunks.minX / MarkersData.CHUNK_STEP,
            mcchunks.minY / MarkersData.CHUNK_STEP,
            (int) Math.ceil((float) mcchunks.maxX / MarkersData.CHUNK_STEP),
            (int) Math.ceil((float) mcchunks.maxY / MarkersData.CHUNK_STEP));

        int shapeMiddleX = (int) ((GuiAtlas.WIDTH * 1.5F) / (INNER_ELEMENTS_SCALE_FACTOR * 2));
        int shapeMiddleY = (int) ((GuiAtlas.HEIGHT * 1.5F) / (INNER_ELEMENTS_SCALE_FACTOR * 2));
        Vec3d chunkPosition = player.getPos().multiply(1D / CHUNK_SIZE, 1D / CHUNK_SIZE, 1D / CHUNK_SIZE);

        for (int x = chunks.minX; x <= chunks.maxX; x++) {
            for (int z = chunks.minY; z <= chunks.maxY; z++) {
                //A marker chunk is greater than a Minecraft chunk
                List<Marker> markers = markersData.getMarkersAtChunk(x, z);
                if (markers == null)
                    continue;
                for (Marker marker : markers) {
                    float relativeChunkPositionX = (float) (marker.getChunkX()
                        - chunkPosition.x);
                    float relativeChunkPositionY = (float) (marker.getChunkZ()
                        - chunkPosition.z);

                    renderMarker(buffer, matrices, marker,
                        shapeMiddleX
                            + (int) Math.floor(relativeChunkPositionX * 8),
                        shapeMiddleY
                            + (int) Math.floor(relativeChunkPositionY * 8), biomeData, light);
                }
            }
        }
    }

    private void renderMarker(VertexConsumerProvider buffer, MatrixStack matrices, Marker marker, int x, int y, WorldData biomeData, int light) {
        int tileHalfSize = GuiAtlas.MARKER_SIZE / 16;
        if (!((x + tileHalfSize) <= 240 && (x - tileHalfSize >= 3) && (y + tileHalfSize) < 166 && (y - tileHalfSize) >= 0))
            return;

        if (!marker.isVisibleAhead() && !biomeData.hasTileAt(marker.getChunkX(), marker.getChunkZ())) {
            return;
        }

        MarkerType type = MarkerType.REGISTRY.get(marker.getType());
        // TODO Fabric - Scale factor?
        MarkerRenderInfo info = type.getRenderInfo(1, AntiqueAtlas.CONFIG.Appearance.tileSize, 1);
        info.tex.drawWithLight(buffer, matrices, x - GuiAtlas.MARKER_SIZE / 4 + 4, y - GuiAtlas.MARKER_SIZE / 4 + 4, GuiAtlas.MARKER_SIZE / 2, GuiAtlas.MARKER_SIZE / 2, light);
    }

    private Rect getChunkCoverage(Vec3d position) {
        int minChunkX = (int) Math.floor(position.x / CHUNK_SIZE
            - (GuiAtlas.WIDTH) / (4f * AntiqueAtlas.CONFIG.Appearance.tileSize));
        minChunkX -= 4;
        int minChunkY = (int) Math.floor(position.z / CHUNK_SIZE
            - (GuiAtlas.HEIGHT) / (4f * AntiqueAtlas.CONFIG.Appearance.tileSize));
        minChunkY -= 3;
        int maxChunkX = (int) Math.ceil(position.x / CHUNK_SIZE
            + (GuiAtlas.WIDTH) / (4f * AntiqueAtlas.CONFIG.Appearance.tileSize));
        maxChunkX += 4;
        int maxChunkY = (int) Math.ceil(position.z / CHUNK_SIZE
            + (GuiAtlas.HEIGHT) / (4f * AntiqueAtlas.CONFIG.Appearance.tileSize));
        maxChunkY += 2;
        return new Rect(minChunkX, minChunkY, maxChunkX, maxChunkY);
    }
}
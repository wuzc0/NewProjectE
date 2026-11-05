package moze_intel.projecte.rendering;

import com.mojang.blaze3d.vertex.PoseStack;
import moze_intel.projecte.gameObjs.block_entities.EmcChestBlockEntity;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.chest.ChestModel;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.ChestRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

public class PEChestRenderer implements BlockEntityRenderer<EmcChestBlockEntity, PEChestRenderer.ChestRenderState> {
   private final ChestModel model;
   private final SpriteGetter sprites;
   private final SpriteId spriteId;

   public PEChestRenderer(Context context, Identifier texture) {
      this.sprites = context.sprites();
      this.spriteId = Sheets.BLOCKS_MAPPER.apply(texture);
      this.model = new ChestModel(context.bakeLayer(ModelLayers.CHEST));
   }

   public PEChestRenderer.ChestRenderState createRenderState() {
      return new PEChestRenderer.ChestRenderState();
   }

   public void extractRenderState(
      @NotNull EmcChestBlockEntity chest,
      @NotNull PEChestRenderer.ChestRenderState state,
      float partialTick,
      @NotNull Vec3 cameraPos,
      @NotNull CrumblingOverlay crumbling
   ) {
      BlockEntityRenderState.extractBase(chest, state, crumbling);
      state.openNess = chest.getOpenNess(partialTick);
      state.facing = (Direction)chest.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
      if (state.facing == null) {
         state.facing = Direction.NORTH;
      }
   }

   public void submit(
      @NotNull PEChestRenderer.ChestRenderState state,
      @NotNull PoseStack matrix,
      @NotNull SubmitNodeCollector collector,
      @NotNull CameraRenderState cameraState
   ) {
      matrix.pushPose();
      matrix.mulPose(ChestRenderer.modelTransformation(state.facing));
      float openNess = 1.0F - state.openNess;
      openNess = 1.0F - openNess * openNess * openNess;
      collector.submitModel(this.model, openNess, matrix, state.lightCoords, OverlayTexture.NO_OVERLAY, -1, this.spriteId, this.sprites, 0);
      if (state.breakProgress != null) {
         collector.order(1).submitCrumblingOverlay(this.model, openNess, matrix, this.spriteId.renderType(this.model.renderType()), state.lightCoords, OverlayTexture.NO_OVERLAY, -1, state.breakProgress);
      }
      matrix.popPose();
   }

   public static class ChestRenderState extends BlockEntityRenderState {
      public float openNess;
      public Direction facing = Direction.NORTH;
   }
}

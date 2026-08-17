package dev.celestiacraft.railway_automation.common.block.track_placer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringRenderer;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.Level;

public class TrackPlacerRenderer extends SafeBlockEntityRenderer<TrackPlacerBlockEntity> {
	public TrackPlacerRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	protected void renderSafe(TrackPlacerBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
		// The block is a full opaque cube, so the light at its own position is 0 and the filter item
		// would render pitch black. Use the light of the block above, where the filter slot sits.
		Level level = be.getLevel();
		if (level != null) {
			light = LevelRenderer.getLightColor(level, be.getBlockPos().above());
		}
		FilteringRenderer.renderOnBlockEntity(be, partialTicks, ms, buffer, light, overlay);
	}
}
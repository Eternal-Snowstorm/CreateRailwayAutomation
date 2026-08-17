package dev.celestiacraft.railway_automation.common.event.placer;

import com.simibubi.create.content.trains.track.BezierConnection;
import com.simibubi.create.content.trains.track.ITrackBlock;
import com.simibubi.create.content.trains.track.TrackBlock;
import com.simibubi.create.content.trains.track.TrackMaterial;
import com.simibubi.create.content.trains.track.TrackPaver;
import com.simibubi.create.content.trains.track.TrackShape;
import com.simibubi.create.foundation.block.ProperWaterloggedBlock;
import com.simibubi.create.foundation.utility.BlockHelper;
import dev.celestiacraft.railway_automation.utils.TrackPlacementUtils;
import net.createmod.catnip.data.Couple;
import net.createmod.catnip.math.AngleHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;

public class TrackTurnPlacer {
	public static Builder builder(Level level, BlockPos start, Vec3i offset) {
		return new Builder(level, start, offset);
	}

	public static class Builder {
		private final Level level;
		private final BlockPos start;
		private final Vec3i offset;
		private TrackMaterial material;
		private Block paveBlock;
		private int cachedTrackCount = -1;
		private int cachedPaveCount = -1;
		private boolean planned = false;
		private boolean executed = false;

		private Builder(Level level, BlockPos start, Vec3i offset) {
			this.level = level;
			this.start = start;
			this.offset = offset;
			BlockState state = level.getBlockState(start);
			material = state.getBlock() instanceof ITrackBlock track
					? track.getMaterial()
					: TrackMaterial.ANDESITE;
		}

		public Builder material(TrackMaterial material) {
			this.material = material;
			return this;
		}

		public Builder pave(Block paveBlock) {
			if (this.paveBlock != paveBlock) {
				this.paveBlock = paveBlock;
				planned = false;
				cachedTrackCount = -1;
				cachedPaveCount = -1;
			}
			return this;
		}

		private void plan() {
			if (planned) {
				return;
			}
			int[] paveCount = new int[1];
			cachedTrackCount = placeTurn(level, start, offset, material, paveBlock, paveCount, true);
			cachedPaveCount = paveCount[0];
			planned = true;
		}

		public int trackCount() {
			plan();
			return cachedTrackCount;
		}

		public int paveCount() {
			plan();
			return cachedPaveCount;
		}

		public void execute() {
			if (executed) {
				return;
			}
			int[] paveCount = new int[1];
			cachedTrackCount = placeTurn(level, start, offset, material, paveBlock, paveCount, false);
			cachedPaveCount = paveCount[0];
			planned = true;
			executed = true;
		}
	}

	private static int placeTurn(
			Level level,
			BlockPos start,
			Vec3i offset,
			TrackMaterial material,
			Block paveBlock,
			int[] paveCount,
			boolean simulate
	) {
		if (level == null || start == null || offset == null || material == null) {
			return 0;
		}
		int hx = offset.getX();
		int hz = offset.getZ();
		if (Math.abs(hx) > 16 || Math.abs(hz) > 16) {
			return placeTurnWithStraightExtension(level, start, offset, material, paveBlock, paveCount, simulate);
		}
		BlockPos end = start.offset(offset);
		BlockState state1 = level.getBlockState(start);
		BlockState state2 = level.getBlockState(end);

		if (!isAxialTrack(state1)) {
			state1 = axialState(material, axialShapeForDirection(offset.getX(), offset.getZ()));
		}
		if (!isAxialTrack(state2)) {
			TrackShape endShape = state1.getValue(TrackBlock.SHAPE) == TrackShape.XO ? TrackShape.ZO : TrackShape.XO;
			state2 = axialState(material, endShape);
		}

		int consumed = connectTurnTracks(level, start, end, state1, state2, material, paveBlock, paveCount, simulate);
		if (consumed >= 0) {
			return consumed;
		}
		return Math.max(0, placeStraightConnector(level, start, offset, material, paveBlock, paveCount, simulate));
	}

	private static int placeTurnWithStraightExtension(
			Level level,
			BlockPos start,
			Vec3i offset,
			TrackMaterial material,
			Block paveBlock,
			int[] paveCount,
			boolean simulate
	) {
		int hx = offset.getX();
		int hz = offset.getZ();
		int cx = Integer.signum(hx) * Math.min(Math.abs(hx), 16);
		int cz = Integer.signum(hz) * Math.min(Math.abs(hz), 16);

		int consumed = placeTurn(level, start, new Vec3i(cx, 0, cz), material, paveBlock, paveCount, simulate);
		if (consumed <= 0) {
			return 0;
		}

		int rx = hx - cx;
		int rz = hz - cz;
		BlockState source = material.getBlock().defaultBlockState();

		if (rx != 0) {
			int step = Integer.signum(rx);
			for (int i = 1; i <= Math.abs(rx); i++) {
				BlockPos target = start.offset(cx + step * i, 0, cz);
				int block = placeExtensionBlock(
						level,
						target,
						Direction.Axis.X,
						source,
						material,
						TrackShape.XO,
						paveBlock,
						paveCount,
						simulate
				);
				if (block < 0) {
					return 0;
				}
				consumed += block;
			}
		}

		if (rz != 0) {
			BlockPos corner = start.offset(hx, 0, cz);
			int block = placeExtensionBlock(
					level,
					corner,
					Direction.Axis.Z,
					source,
					material,
					TrackShape.ZO,
					paveBlock,
					paveCount,
					simulate
			);
			if (block < 0) {
				return 0;
			}
			consumed += block;

			int step = Integer.signum(rz);
			for (int i = 1; i <= Math.abs(rz); i++) {
				BlockPos target = start.offset(hx, 0, cz + step * i);
				block = placeExtensionBlock(
						level,
						target,
						Direction.Axis.Z,
						source,
						material,
						TrackShape.ZO,
						paveBlock,
						paveCount,
						simulate
				);
				if (block < 0) {
					return 0;
				}
				consumed += block;
			}
		}

		return consumed;
	}

	private static int placeExtensionBlock(
			Level level,
			BlockPos target,
			Direction.Axis axis,
			BlockState source,
			TrackMaterial material,
			TrackShape shape,
			Block paveBlock,
			int[] paveCount,
			boolean simulate
	) {
		if (!simulate) {
			TrackPlacementUtils.clearAboveFoundation(level, target, axis);
		}
		BlockState stateAtPos = level.getBlockState(target);

		int count;
		if (simulate) {
			count = TrackPlacementUtils.simulateTrackPlacement(stateAtPos, level, target);
			if (count < 0) {
				return -1;
			}
		} else {
			if (!stateAtPos.canBeReplaced() && !stateAtPos.is(BlockTags.FLOWERS)
					&& !(stateAtPos.getBlock() instanceof ITrackBlock)) {
				return -1;
			}

			BlockState toPlace = BlockHelper.copyProperties(source, material.getBlock().defaultBlockState())
					.setValue(TrackBlock.SHAPE, shape)
					.setValue(TrackBlock.HAS_BE, false);

			if (stateAtPos.getBlock() instanceof ITrackBlock existingTrack) {
				toPlace = existingTrack.overlay(level, target, stateAtPos, toPlace);
			}

			level.setBlock(target, ProperWaterloggedBlock.withWater(level, toPlace, target), 3);
			count = stateAtPos.canBeReplaced() || stateAtPos.is(BlockTags.FLOWERS) ? 1 : 0;
		}

		if (paveBlock != null && paveCount != null) {
			Vec3 paveAxis = axis == Direction.Axis.X ? new Vec3(1, 0, 0) : new Vec3(0, 0, 1);
			paveCount[0] += TrackPaver.paveStraight(
					level,
					target.below(),
					paveAxis,
					1,
					paveBlock,
					simulate,
					new HashSet<>()
			);
		}
		return count;
	}

	private static int placeStraightConnector(
			Level level,
			BlockPos start,
			Vec3i offset,
			TrackMaterial material,
			Block paveBlock,
			int[] paveCount,
			boolean simulate
	) {
		int hx = offset.getX();
		int hz = offset.getZ();
		if (hx != 0 && hz != 0) {
			return -1;
		}
		int length = Math.max(Math.abs(hx), Math.abs(hz));
		if (length == 0) {
			return -1;
		}
		int stepX = Integer.signum(hx);
		int stepZ = Integer.signum(hz);
		Direction.Axis axis = stepX != 0 ? Direction.Axis.X : Direction.Axis.Z;
		TrackShape shape = hx != 0 ? TrackShape.XO : TrackShape.ZO;
		BlockState source = material.getBlock().defaultBlockState();
		int consumed = 0;

		for (int i = 0; i <= length; i++) {
			BlockPos target = start.offset(stepX * i, 0, stepZ * i);
			if (!simulate) {
				TrackPlacementUtils.clearAboveFoundation(level, target, axis);
			}
			BlockState stateAtPos = level.getBlockState(target);

			int count;
			if (simulate) {
				count = TrackPlacementUtils.simulateTrackPlacement(stateAtPos, level, target);
				if (count < 0) {
					return -1;
				}
			} else {
				if (!stateAtPos.canBeReplaced() && !stateAtPos.is(BlockTags.FLOWERS)
						&& !(stateAtPos.getBlock() instanceof ITrackBlock)) {
					return -1;
				}

				BlockState toPlace = BlockHelper.copyProperties(source, material.getBlock().defaultBlockState())
						.setValue(TrackBlock.SHAPE, shape)
						.setValue(TrackBlock.HAS_BE, false);
				if (stateAtPos.getBlock() instanceof ITrackBlock existingTrack) {
					toPlace = existingTrack.overlay(level, target, stateAtPos, toPlace);
				}
				level.setBlock(target, ProperWaterloggedBlock.withWater(level, toPlace, target), 3);
				count = stateAtPos.canBeReplaced() || stateAtPos.is(BlockTags.FLOWERS) ? 1 : 0;
			}
			consumed += count;

			if (paveBlock != null && paveCount != null) {
				Vec3 paveAxis = axis == Direction.Axis.X ? new Vec3(1, 0, 0) : new Vec3(0, 0, 1);
				paveCount[0] += TrackPaver.paveStraight(
						level,
						target.below(),
						paveAxis,
						1,
						paveBlock,
						simulate,
						new HashSet<>()
				);
			}
		}
		return consumed;
	}

	private static boolean isAxialTrack(BlockState state) {
		return state.getBlock() instanceof ITrackBlock
				&& state.hasProperty(TrackBlock.SHAPE)
				&& (state.getValue(TrackBlock.SHAPE) == TrackShape.XO
				|| state.getValue(TrackBlock.SHAPE) == TrackShape.ZO);
	}

	private static BlockState axialState(TrackMaterial material, TrackShape shape) {
		return material.getBlock().defaultBlockState()
				.setValue(TrackBlock.SHAPE, shape)
				.setValue(TrackBlock.HAS_BE, false);
	}

	private static TrackShape axialShapeForDirection(int hx, int hz) {
		if (hx != 0 && hz != 0) {
			return TrackShape.ZO;
		}
		return hx != 0 ? TrackShape.XO : TrackShape.ZO;
	}

	private static int connectTurnTracks(
			Level level,
			BlockPos start,
			BlockPos end,
			BlockState state1,
			BlockState state2,
			TrackMaterial material,
			Block paveBlock,
			int[] paveCount,
			boolean simulate
	) {
		if (!(state1.getBlock() instanceof ITrackBlock track1)
				|| !(state2.getBlock() instanceof ITrackBlock track2)
				|| !state1.hasProperty(TrackBlock.SHAPE)
				|| !state2.hasProperty(TrackBlock.SHAPE)) {
			return -1;
		}

		Vec3 axis1 = TrackPlacementUtils.pickAxis(level, start, state1, end);
		Vec3 axis2 = TrackPlacementUtils.pickAxis(level, end, state2, start);
		if (axis1 == null || axis2 == null) {
			return -1;
		}

		Vec3 normal1 = track1.getUpNormal(level, start, state1).normalize();
		Vec3 normal2 = track2.getUpNormal(level, end, state2).normalize();
		Vec3 normedAxis1 = axis1.normalize();
		Vec3 normedAxis2 = axis2.normalize();
		Vec3 end1 = track1.getCurveStart(level, start, state1, axis1);
		Vec3 end2 = track2.getCurveStart(level, end, state2, axis2);

		if (axis1.dot(end2.subtract(end1)) < 0) {
			axis1 = axis1.scale(-1);
			normedAxis1 = normedAxis1.scale(-1);
			end1 = track1.getCurveStart(level, start, state1, axis1);
		}

		double[] intersect = TrackPlacementUtils.intersect(end1, end2, normedAxis1, normedAxis2, Direction.Axis.Y);
		boolean parallel = intersect == null;

		if ((parallel && normedAxis1.dot(normedAxis2) > 0)
				|| (!parallel && (intersect[0] < 0
				|| intersect[1] < 0))) {
			axis2 = axis2.scale(-1);
			normedAxis2 = normedAxis2.scale(-1);
			end2 = track2.getCurveStart(level, end, state2, axis2);
		}

		if (parallel || !normal1.equals(normal2)) {
			return -1;
		}

		double a1 = Mth.atan2(normedAxis2.z(), normedAxis2.x());
		double a2 = Mth.atan2(normedAxis1.z(), normedAxis1.x());
		double angle = a1 - a2;
		double ascend = end2.subtract(end1).y();
		double absAscend = Math.abs(ascend);

		float absAngle = Math.abs(AngleHelper.deg(angle));
		if (absAngle < 60 || absAngle > 300) {
			return -1;
		}

		intersect = TrackPlacementUtils.intersect(end1, end2, normedAxis1, normedAxis2, Direction.Axis.Y);
		double dist1 = Math.abs(intersect[0]);
		double dist2 = Math.abs(intersect[1]);
		float ex1 = 0;
		float ex2 = 0;

		if (dist1 > dist2) {
			ex1 = (float) ((dist1 - dist2) / axis1.length());
		}
		if (dist2 > dist1) {
			ex2 = (float) ((dist2 - dist1) / axis2.length());
		}

		double turnSize = Math.min(dist1, dist2) - 0.1d;
		boolean ninety = (absAngle + 0.25f) % 90 < 1;

		if (intersect[0] < 0 || intersect[1] < 0) {
			return -1;
		}

		double minTurnSize = ninety ? 7 : 3.25;
		double turnSizeToFitAscend = minTurnSize
				+ (ninety ? Math.max(0, absAscend - 3) * 2.0f
				: Math.max(0, absAscend - 1.5f) * 1.5f);

		if (turnSize < minTurnSize) {
			return -1;
		}
		if (turnSize < turnSizeToFitAscend) {
			return -1;
		}
		int end1Extent = Mth.floor(ex1);
		int end2Extent = Mth.floor(ex2);

		Vec3 offset1 = axis1.scale(end1Extent);
		Vec3 offset2 = axis2.scale(end2Extent);
		BlockPos targetPos1 = start.offset(BlockPos.containing(offset1));
		BlockPos targetPos2 = end.offset(BlockPos.containing(offset2));

		BezierConnection curve = new BezierConnection(
				Couple.create(targetPos1, targetPos2),
				Couple.create(end1.add(offset1), end2.add(offset2)),
				Couple.create(normedAxis1, normedAxis2),
				Couple.create(normal1, normal2), true, false, material
		);

		if (!simulate) {
			TrackPlacementUtils.clearAboveCurveFoundation(level, curve);
		}

		return TrackPlacementUtils.placeSlopeTracks(
				level,
				start,
				end,
				state1,
				state2,
				axis1,
				axis2,
				end1Extent,
				end2Extent,
				targetPos1,
				targetPos2,
				curve,
				material,
				paveBlock,
				paveCount,
				simulate
		);
	}
}

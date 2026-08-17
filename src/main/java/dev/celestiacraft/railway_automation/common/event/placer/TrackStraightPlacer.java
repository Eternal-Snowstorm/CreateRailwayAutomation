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
import net.createmod.catnip.math.VecHelper;
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

public class TrackStraightPlacer {
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
			cachedTrackCount = placeStraight(level, start, offset, material, paveBlock, paveCount, true);
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
			cachedTrackCount = placeStraight(level, start, offset, material, paveBlock, paveCount, false);
			cachedPaveCount = paveCount[0];
			planned = true;
			executed = true;
		}
	}

	private static int placeStraight(
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
		if (offset.getY() == 0) {
			return placeStraightLine(level, start, offset, material, paveBlock, paveCount, simulate);
		}
		BlockPos end = start.offset(offset);
		BlockState state1 = level.getBlockState(start);
		BlockState state2 = level.getBlockState(end);
		boolean trackStart = state1.getBlock() instanceof ITrackBlock
				&& state1.hasProperty(TrackBlock.SHAPE);
		boolean trackEnd = state2.getBlock() instanceof ITrackBlock
				&& state2.hasProperty(TrackBlock.SHAPE);

		if (trackStart && trackEnd) {
			return Math.max(0, connectStraightTracks(
					level,
					start,
					end,
					state1,
					state2,
					material,
					false,
					paveBlock,
					paveCount, simulate
			));
		}

		int hx = offset.getX();
		int hy = offset.getY();
		int hz = offset.getZ();
		boolean xAxis = hx != 0;
		boolean zAxis = hz != 0;
		int length = Math.max(Math.abs(hx), Math.abs(hz));
		boolean oneToOne = xAxis != zAxis
				&& hy != 0
				&& Math.abs(hy) == length;

		if (oneToOne) {
			return Math.max(0, placeStraightChain(
					level,
					start,
					offset,
					material,
					trackStart ? state1 : null,
					paveBlock,
					paveCount, simulate
			));
		}

		if (length == 0) {
			return 0;
		}

		int rise = Math.abs(hy);
		TrackShape shape = hx != 0 ? TrackShape.XO : TrackShape.ZO;
		BlockState flat = material.getBlock().defaultBlockState()
				.setValue(TrackBlock.SHAPE, shape)
				.setValue(TrackBlock.HAS_BE, false);

		if (rise > 10) {
			if (length < rise + 9) {
				return Math.max(0, connectStraightTracks(
						level,
						start,
						end,
						flat,
						flat,
						material,
						true,
						paveBlock,
						paveCount, simulate));
			}
			return Math.max(0, placeHybridStraightWithCurves(
					level,
					start,
					offset,
					material,
					paveBlock,
					paveCount,
					simulate
			));
		}

		if (length < 3 * rise) {
			return Math.max(0, placeHybridStraightWithCurves(
					level,
					start,
					offset,
					material,
					paveBlock,
					paveCount,
					simulate
			));
		}
		return Math.max(0, placeOneToThreeStraight(level, start, offset, material, paveBlock, paveCount, simulate));
	}

	private static int placeStraightLine(
			Level level,
			BlockPos start,
			Vec3i offset,
			TrackMaterial material,
			Block paveBlock,
			int[] paveCount,
			boolean simulate
	) {
		Direction.Axis axis = TrackPlacementUtils.getSingleAxis(offset);
		if (axis == null || axis == Direction.Axis.Y) {
			return 0;
		}

		BlockState sourceState = level.getBlockState(start);
		if (!(sourceState.getBlock() instanceof ITrackBlock)) {
			sourceState = material.getBlock().defaultBlockState();
		}

		TrackShape shape = axis == Direction.Axis.X ? TrackShape.XO : TrackShape.ZO;
		int stepX = Integer.signum(offset.getX());
		int stepY = Integer.signum(offset.getY());
		int stepZ = Integer.signum(offset.getZ());
		int length = Math.max(Math.abs(offset.getX()), Math.max(Math.abs(offset.getY()), Math.abs(offset.getZ())));

		int firstIndex = canGrowNewTrack(level, start) ? 0 : 1;
		int consumed = 0;
		for (int i = firstIndex; i <= length; i++) {
			BlockPos target = start.offset(stepX * i, stepY * i, stepZ * i);
			int block = placeStraightBlock(level, target, axis, sourceState, material, shape, paveBlock, paveCount, simulate);
			if (block < 0) {
				return 0;
			}
			consumed += block;
		}

		return consumed;
	}

	private static boolean canGrowNewTrack(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.canBeReplaced() || state.is(BlockTags.FLOWERS);
	}

	private static int placeHybridStraightWithCurves(
			Level level,
			BlockPos start,
			Vec3i offset,
			TrackMaterial material,
			Block paveBlock,
			int[] paveCount,
			boolean simulate
	) {
		int hx = offset.getX();
		int hy = offset.getY();
		int hz = offset.getZ();
		int length = Math.max(Math.abs(hx), Math.abs(hz));
		int rise = Math.abs(hy);
		int curveH = 8;
		int curveV = 4;
		int slopeRise = rise - 2 * curveV;
		int flatTotal = length - 2 * (curveH + 1) - (slopeRise - 1);
		int bottomFlat = flatTotal / 2;
		boolean startOk = canGrowNewTrack(level, start);
		if (!startOk && flatTotal == 1) {
			bottomFlat = 1;
		}
		int topFlat = flatTotal - bottomFlat;

		Direction.Axis axis = hx != 0 ? Direction.Axis.X : Direction.Axis.Z;
		int stepX = Integer.signum(hx);
		int stepY = Integer.signum(hy);
		int stepZ = Integer.signum(hz);
		boolean ascending = stepY > 0;

		TrackShape flatShape = axis == Direction.Axis.X
				? TrackShape.XO
				: TrackShape.ZO;

		TrackShape slopeShape = hx != 0
				? (stepX > 0 ? (ascending ? TrackShape.AE : TrackShape.AW)
				: (ascending ? TrackShape.AW : TrackShape.AE))
				: (stepZ > 0 ? (ascending ? TrackShape.AS : TrackShape.AN)
				: (ascending ? TrackShape.AN : TrackShape.AS));

		BlockState flat = material.getBlock().defaultBlockState()
				.setValue(TrackBlock.SHAPE, flatShape)
				.setValue(TrackBlock.HAS_BE, false);

		BlockState slopeState = material.getBlock().defaultBlockState()
				.setValue(TrackBlock.SHAPE, slopeShape)
				.setValue(TrackBlock.HAS_BE, false);

		Vec3 flatDir = new Vec3(stepX, 0, stepZ);
		Vec3 slopeDir = new Vec3(stepX, stepY, stepZ).normalize();
		Vec3 flatNormal = new Vec3(0, 1, 0);
		Vec3 slopeNormal = slopeShape.getNormal().normalize();

		int firstFlat = startOk ? 0 : 1;
		int consumed = 0;

		BlockPos p1 = start.offset(stepX * bottomFlat, 0, stepZ * bottomFlat);
		for (int i = firstFlat; i < bottomFlat; i++) {
			BlockPos target = start.offset(stepX * i, 0, stepZ * i);
			int block = placeStraightBlock(
					level,
					target,
					axis,
					flat,
					material,
					flatShape,
					paveBlock,
					paveCount,
					simulate
			);
			if (block < 0) {
				return -1;
			}
			consumed += block;
		}

		Vec3 a = new Vec3(p1.getX() + 0.5, p1.getY(), p1.getZ() + 0.5).add(flatDir.scale(0.5));
		Vec3 b = a.add(new Vec3(stepX * curveH, stepY * curveV, stepZ * curveH));
		BlockPos ps = TrackPlacementUtils.slopeBlockAtEnd(b, stepX, stepZ, ascending, axis);

		BlockPos pl = ps.offset(stepX * (slopeRise - 1), stepY * (slopeRise - 1), stepZ * (slopeRise - 1));
		for (int i = 1; i < slopeRise - 1; i++) {
			BlockPos target = ps.offset(stepX * i, stepY * i, stepZ * i);
			int r = placeStraightBlock(
					level,
					target,
					axis,
					slopeState,
					material,
					slopeShape,
					paveBlock,
					paveCount,
					simulate
			);
			if (r < 0) {
				return -1;
			}
			consumed += r;
		}

		Vec3 c = TrackPlacementUtils.slopeTipAtEnd(pl, stepX, stepZ, ascending, axis);
		Vec3 d = c.add(new Vec3(stepX * curveH, stepY * curveV, stepZ * curveH));
		BlockPos pt = TrackPlacementUtils.flatBlockAtEnd(d, stepX, stepZ, axis);

		for (int i = 1; i <= topFlat; i++) {
			BlockPos target = pt.offset(stepX * i, 0, stepZ * i);
			int block = placeStraightBlock(
					level,
					target,
					axis,
					flat,
					material,
					flatShape,
					paveBlock,
					paveCount,
					simulate
			);
			if (block < 0) {
				return -1;
			}
			consumed += block;
		}

		BezierConnection curve1 = new BezierConnection(
				Couple.create(p1, ps),
				Couple.create(a, b),
				Couple.create(flatDir, slopeDir.scale(-1)),
				Couple.create(flatNormal, slopeNormal),
				true,
				false,
				material
		);
		int curve = placeCurve(
				level,
				p1,
				ps,
				flat,
				slopeState,
				flatDir,
				slopeDir,
				curve1,
				material,
				paveBlock,
				paveCount,
				simulate
		);
		if (curve < 0) {
			return -1;
		}
		consumed += curve;

		BezierConnection curve2 = new BezierConnection(
				Couple.create(pl, pt),
				Couple.create(c, d),
				Couple.create(slopeDir, flatDir.scale(-1)),
				Couple.create(slopeNormal, flatNormal),
				true,
				false,
				material
		);
		curve = placeCurve(
				level,
				pl,
				pt,
				slopeState,
				flat,
				slopeDir,
				flatDir,
				curve2,
				material,
				paveBlock,
				paveCount,
				simulate
		);
		if (curve < 0) {
			return -1;
		}
		consumed += curve;

		return consumed;
	}

	private static int placeOneToThreeStraight(
			Level level,
			BlockPos start,
			Vec3i offset,
			TrackMaterial material,
			Block paveBlock,
			int[] paveCount,
			boolean simulate
	) {
		int hx = offset.getX();
		int hy = offset.getY();
		int hz = offset.getZ();
		int length = Math.max(Math.abs(hx), Math.abs(hz));
		int rise = Math.abs(hy);
		int curveH = 3 * rise;
		int flatTotal = length - curveH;
		int bottomFlat = flatTotal / 2;
		boolean startOk = canGrowNewTrack(level, start);

		if (!startOk && flatTotal == 1) {
			bottomFlat = 1;
		}
		int topFlat = flatTotal - bottomFlat;

		Direction.Axis axis = hx != 0 ? Direction.Axis.X : Direction.Axis.Z;
		int stepX = Integer.signum(hx);
		int stepY = Integer.signum(hy);
		int stepZ = Integer.signum(hz);

		TrackShape flatShape = axis == Direction.Axis.X
				? TrackShape.XO
				: TrackShape.ZO;

		BlockState flat = material.getBlock().defaultBlockState()
				.setValue(TrackBlock.SHAPE, flatShape)
				.setValue(TrackBlock.HAS_BE, false);

		int firstFlat = startOk ? 0 : 1;
		BlockPos p1 = start.offset(stepX * bottomFlat, 0, stepZ * bottomFlat);
		int consumed = 0;

		for (int i = firstFlat; i < bottomFlat; i++) {
			BlockPos target = start.offset(stepX * i, 0, stepZ * i);
			int block = placeStraightBlock(
					level,
					target,
					axis,
					flat,
					material,
					flatShape,
					paveBlock,
					paveCount,
					simulate
			);
			if (block < 0) {
				return -1;
			}
			consumed += block;
		}

		BlockPos p2 = start.offset(stepX * (bottomFlat + curveH), stepY * rise, stepZ * (bottomFlat + curveH));
		int tracks = connectStraightTracks(
				level,
				p1,
				p2,
				flat,
				flat,
				material,
				true,
				paveBlock,
				paveCount,
				simulate
		);
		if (tracks < 0) {
			return -1;
		}
		consumed += tracks;

		for (int i = 1; i <= topFlat; i++) {
			BlockPos target = start.offset(
					stepX * (bottomFlat + curveH + i),
					stepY * rise,
					stepZ * (bottomFlat + curveH + i)
			);
			tracks = placeStraightBlock(
					level,
					target,
					axis,
					flat,
					material,
					flatShape,
					paveBlock,
					paveCount,
					simulate
			);
			if (tracks < 0) {
				return -1;
			}
			consumed += tracks;
		}

		return consumed;
	}

	private static int placeCurve(
			Level level,
			BlockPos pos1,
			BlockPos pos2,
			BlockState state1,
			BlockState state2,
			Vec3 axis1,
			Vec3 axis2,
			BezierConnection curve,
			TrackMaterial material,
			Block paveBlock,
			int[] paveCount,
			boolean simulate
	) {
		if (!simulate) {
			TrackPlacementUtils.clearAboveCurveFoundation(level, curve);
		}
		return TrackPlacementUtils.placeSlopeTracks(
				level,
				pos1,
				pos2,
				state1,
				state2,
				axis1,
				axis2,
				0,
				0,
				pos1,
				pos2,
				curve,
				material,
				paveBlock,
				paveCount,
				simulate
		);
	}

	private static int connectStraightTracks(
			Level level,
			BlockPos start,
			BlockPos end,
			BlockState state1,
			BlockState state2,
			TrackMaterial material
	) {
		return connectStraightTracks(
				level,
				start,
				end,
				state1,
				state2,
				material,
				false,
				null,
				null,
				false
		);
	}

	private static int connectStraightTracks(
			Level level,
			BlockPos start,
			BlockPos end,
			BlockState state1,
			BlockState state2,
			TrackMaterial material,
			boolean allowSteep,
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

		double[] intersect = TrackPlacementUtils.intersect(
				end1,
				end2,
				normedAxis1,
				normedAxis2,
				Direction.Axis.Y
		);
		boolean parallel = intersect == null;
		boolean skipCurve;

		if ((parallel && normedAxis1.dot(normedAxis2) > 0)
				|| (!parallel && (intersect[0] < 0
				|| intersect[1] < 0))) {
			axis2 = axis2.scale(-1);
			normedAxis2 = normedAxis2.scale(-1);
			end2 = track2.getCurveStart(level, end, state2, axis2);
		}

		Vec3 cross2 = normedAxis2.cross(new Vec3(0, 1, 0));

		double ascend = end2.subtract(end1).y;
		double absAscend = Math.abs(ascend);
		boolean slope = !normal1.equals(normal2);

		int end1Extent;
		int end2Extent = 0;
		double dist;

		if (!parallel) {
			return -1;
		}
		double[] sTest = TrackPlacementUtils.intersect(
				end1,
				end2,
				normedAxis1,
				cross2,
				Direction.Axis.Y
		);
		if (sTest == null || !Mth.equal(Math.abs(sTest[1]), 0)) {
			return -1;
		}
		skipCurve = true;
		dist = VecHelper.getCenterOf(start).distanceTo(VecHelper.getCenterOf(end));
		end1Extent = (int) Math.round((dist + 1) / axis1.length());

		if (slope) {
			if (!skipCurve) {
				return -1;
			}
			if (Mth.equal(normal1.dot(normal2), 0)) {
				return -1;
			}
			if ((axis1.y < 0 || axis2.y > 0) && ascend > 0) {
				return -1;
			}
			if ((axis1.y > 0 || axis2.y < 0) && ascend < 0) {
				return -1;
			}

			skipCurve = false;
			end1Extent = 0;
			end2Extent = 0;

			Direction.Axis plane = Mth.equal(axis1.x, 0) ? Direction.Axis.X : Direction.Axis.Z;
			intersect = TrackPlacementUtils.intersect(end1, end2, normedAxis1, normedAxis2, plane);
			double dist1 = Math.abs(intersect[0] / axis1.length());
			double dist2 = Math.abs(intersect[1] / axis2.length());

			if (dist1 > dist2) {
				end1Extent = (int) Math.round(dist1 - dist2);
			}
			if (dist2 > dist1) {
				end2Extent = (int) Math.round(dist2 - dist1);
			}

			double turnSize = Math.min(dist1, dist2);
			if (intersect[0] < 0 || intersect[1] < 0) {
				return -1;
			}
			if (turnSize < 2) {
				return -1;
			}
		}

		if (skipCurve && !Mth.equal(ascend, 0)) {
			int hDistance = end1Extent;
			if (axis1.y == 0 || !Mth.equal(absAscend + 1, dist / axis1.length())) {
				if (axis1.y != 0 && axis1.y == -axis2.y) {
					return -1;
				}

				end1Extent = 0;
				double minHDistance = allowSteep
						? 2
						: Math.max(absAscend < 4
						? absAscend * 4 : absAscend * 3, 6)
						/ axis1.length();
				if (hDistance < minHDistance) {
					return -1;
				}
				skipCurve = false;
			}
		}

		Vec3 offset1 = axis1.scale(end1Extent);
		Vec3 offset2 = axis2.scale(end2Extent);
		BlockPos targetPos1 = start.offset(BlockPos.containing(offset1));
		BlockPos targetPos2 = end.offset(BlockPos.containing(offset2));

		BezierConnection curve = skipCurve
				? null
				: new BezierConnection(Couple.create(targetPos1, targetPos2),
				Couple.create(end1.add(offset1), end2.add(offset2)),
				Couple.create(normedAxis1, normedAxis2),
				Couple.create(normal1, normal2),
				true,
				false,
				material
		);

		if (!simulate && curve != null) {
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

	private static int placeStraightChain(
			Level level,
			BlockPos start,
			Vec3i offset,
			TrackMaterial material,
			BlockState anchorState,
			Block paveBlock,
			int[] paveCount,
			boolean simulate
	) {
		int hx = offset.getX();
		int hy = offset.getY();
		int hz = offset.getZ();
		boolean xAxis = hx != 0;
		boolean zAxis = hz != 0;

		if (xAxis == zAxis || hy == 0) {
			return -1;
		}
		int length = Math.max(Math.abs(hx), Math.abs(hz));
		if (Math.abs(hy) != length) {
			return -1;
		}

		Direction.Axis axis = xAxis ? Direction.Axis.X : Direction.Axis.Z;
		int stepX = Integer.signum(hx);
		int stepY = Integer.signum(hy);
		int stepZ = Integer.signum(hz);
		boolean ascending = stepY > 0;

		TrackShape shape = xAxis
				? (stepX > 0
				? (ascending ? TrackShape.AE : TrackShape.AW)
				: (ascending ? TrackShape.AW : TrackShape.AE))
				: (stepZ > 0
				? (ascending ? TrackShape.AS : TrackShape.AN)
				: (ascending ? TrackShape.AN : TrackShape.AS));

		int consumed = 0;
		if (anchorState == null && canGrowNewTrack(level, start)) {
			BlockState flat = material.getBlock().defaultBlockState()
					.setValue(TrackBlock.SHAPE, axis == Direction.Axis.X ? TrackShape.XO : TrackShape.ZO)
					.setValue(TrackBlock.HAS_BE, false);
			int block = placeStraightBlock(
					level,
					start,
					axis,
					flat,
					material,
					flat.getValue(TrackBlock.SHAPE),
					paveBlock,
					paveCount,
					simulate
			);
			if (block < 0) {
				return -1;
			}
			consumed += block;
		}

		BlockState source = anchorState != null ? anchorState : material.getBlock().defaultBlockState();
		for (int i = 1; i <= length; i++) {
			int yOffset = ascending ? i - 1 : i;
			BlockPos target = start.offset(stepX * i, stepY * yOffset, stepZ * i);
			int r = placeStraightBlock(level, target, axis, source, material, shape, paveBlock, paveCount, simulate);
			if (r < 0) {
				return -1;
			}
			consumed += r;
		}

		return consumed;
	}

	private static int placeStraightBlock(
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
			if (!stateAtPos.canBeReplaced()
					&& !stateAtPos.is(BlockTags.FLOWERS)
					&& !(stateAtPos.getBlock() instanceof ITrackBlock)) {
				return -1;
			}

			BlockState toPlace = BlockHelper.copyProperties(source, material.getBlock().defaultBlockState())
					.setValue(TrackBlock.SHAPE, shape)
					.setValue(TrackBlock.HAS_BE, false);

			if (stateAtPos.getBlock() instanceof ITrackBlock existingTrack) {
				toPlace = existingTrack.overlay(level, target, stateAtPos, toPlace);
			}

			level.setBlock(target, ProperWaterloggedBlock.withWater(
					level,
					toPlace,
					target
			), 3);
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
}

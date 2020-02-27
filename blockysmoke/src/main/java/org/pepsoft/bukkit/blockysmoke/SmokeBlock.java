/*
 This file is part of BlockySmokePlugin

 BlockySmokePlugin is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 BlockySmokePlugin is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with Foobar.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.pepsoft.bukkit.blockysmoke;

import static org.pepsoft.bukkit.blockysmoke.BlockySmokePlugin.logger;
import static org.pepsoft.bukkit.blockysmoke.BlockySmokePlugin.windDirection;
import static org.pepsoft.bukkit.blockysmoke.BlockySmokePlugin.windStrength;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.Random;
import java.util.logging.Level;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.pepsoft.util.MathUtils;

/**
 * A single smoke block. Knows if, where and how to propagate itself.
 *
 * @author Pepijn Schmitz
 */
public final class SmokeBlock implements Serializable {
	public SmokeBlock(SmokingBlock smokingBlock, IntLocation location) {
		if (logger.isLoggable(Level.FINE))
			logger.fine("[BlockySmoke] Smoke block " + Integer.toHexString(hashCode()) + " created @ " + location);
		this.smokingBlock = smokingBlock;
		this.location = location;
	}

	public boolean tick(World world, Random random) {
		if (location.y >= world.getMaxHeight()) {
			// The smoke is leaving the world
			if (logger.isLoggable(Level.FINE))
				logger.fine("[BlockySmoke] Smoke block " + Integer.toHexString(hashCode()) + " @ " + location + " has reached the maximum map height; removing it");
			smokingBlock.remove(location);
			return false;
		} else if (MathUtils.getDistance(smokingBlock.location, location) > smokingBlock.maxDistance) {
			// The smoke is too far away from the source block
			if (logger.isLoggable(Level.FINE))
				logger.fine("[BlockySmoke] Smoke block " + Integer.toHexString(hashCode()) + " @ " + location + " has reached the maximum distance from the source block; removing it");
			smokingBlock.remove(location);
			return false;
		} else if (random.nextFloat() < smokingBlock.decayChance) {
			// The smoke should dissipate
			if (logger.isLoggable(Level.FINE))
				logger.fine("[BlockySmoke] Dissipating smoke block " + Integer.toHexString(hashCode()) + " @ " + location);
			smokingBlock.remove(location);
			return false;
		} else {
			final IntLocation newLocation = findLocation(world, random, location);
			if (newLocation != null) {
				// The smake can move to a new location
				if (logger.isLoggable(Level.FINE))
					logger.fine("[BlockySmoke] Moving smoke block " + Integer.toHexString(hashCode()) + " from " + location + " to " + newLocation);
				smokingBlock.update(location, newLocation);
				location = newLocation;
				return true;
			} else {
				// There is no new location to move to
				if (logger.isLoggable(Level.FINE))
					logger.fine("[BlockySmoke] Smoke block " + Integer.toHexString(hashCode()) + " @ " + location + " has nowhere to go; removing it");
				smokingBlock.remove(location);
				return false;
			}
		}
	}

	private IntLocation findLocation(World world, Random random, IntLocation oldLocation) {
		int dx = 0, dz = 0;
		if (smokingBlock.randomSpread) {
			dx = random.nextInt(9);
			if (dx == 0)
				dx = -1;
			else if (dx == 8)
				dx = 1;
			else
				dx = 0;
			dz = random.nextInt(9);
			if (dz == 0)
				dz = -1;
			else if (dz == 8)
				dz = 1;
			else
				dz = 0;
		}
		// Invert wind direction, because a wind direction indicates *from*
		// which direction it comes
		WindDirection wind = windDirection;
		if (smokingBlock.fromDirection != null)
			wind = wind.constrain(smokingBlock.fromDirection, smokingBlock.toDirection);
		dx += wind.dx * -windStrength;
		dz += wind.dy * -windStrength;
		final boolean spread = smokingBlock.densityMax > 1;
		for (int i = 0; i < PROPAGATION_OFFSETS.length; i++)
			if (PROPAGATION_OFFSETS[i].length == 1) {
				final int[] offsets = PROPAGATION_OFFSETS[i][0];
				final IntLocation newLocation = new IntLocation(oldLocation.x + offsets[0] + dx, oldLocation.y + offsets[1], oldLocation.z + offsets[2] + dz);
				if (probe(world, random, newLocation, spread))
					return newLocation;
			} else {
				final Integer[] offsetOffsets = new Integer[PROPAGATION_OFFSETS[i].length];
				for (int j = 0; j < offsetOffsets.length; j++)
					offsetOffsets[j] = j;
				Collections.shuffle(Arrays.asList(offsetOffsets), random);
				for (int j = 0; j < offsetOffsets.length; j++) {
					final int[] offsets = PROPAGATION_OFFSETS[i][offsetOffsets[j]];
					final IntLocation newLocation = new IntLocation(oldLocation.x + offsets[0] + dx, oldLocation.y + offsets[1], oldLocation.z + offsets[2] + dz);
					if (probe(world, random, newLocation, spread))
						return newLocation;
				}
			}
		return null;
	}

	/**
	 * Tests whether a new location for a smoke block is viable.
	 *
	 * @param world The world in which to test for a new location.
	 * @param random The random generator to use for entropy.
	 * @param newLocation The new location to test.
	 * @param spread Whether to spread away from existing smoke blocks.
	 * @return <code>true</code> if location is viable for smoke.
	 */
	private boolean probe(World world, Random random, IntLocation newLocation, boolean spread) {
		if (newLocation.y >= world.getMaxHeight())
			return false;
		final Block block = world.getBlockAt(newLocation.x, newLocation.y, newLocation.z);
		final Material existingBlockType = block.getType();
		if (existingBlockType == Material.AIR)
			// Always spread to air
			return true;
		else if ((existingBlockType == smokingBlock.smokeType))
			// Only spread to existing smoke block in half the cases
			return (! spread) || random.nextBoolean();
		return false;
	}

	private final SmokingBlock smokingBlock;
	private IntLocation location;

	private static final int[][][] PROPAGATION_OFFSETS = {
			{{0, 1, 0}},
			{{-1, 1, -1}, {-1, 1, 0}, {-1, 1, 1}, {0, 1, -1}, {0, 1, 1}, {1, 1, -1}, {1, 1, 0}, {1, 1, 1}},
			{{-1, 0, -1}, {-1, 0, 0}, {-1, 0, 1}, {0, 0, -1}, {0, 0, 0}, {0, 0, 1}, {1, 0, -1}, {1, 0, 0}, {1, 0, 1}},
			{{-1, -1, -1}, {-1, -1, 0}, {-1, -1, 1}, {0, -1, -1}, {0, -1, 1}, {1, -1, -1}, {1, -1, 0}, {1, -1, 1}},
			{{0, -1, 0}}
	};
	private static final long serialVersionUID = 1L;
}
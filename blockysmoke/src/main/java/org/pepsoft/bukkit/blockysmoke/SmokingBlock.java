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

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * A single blocky smoker or smoking block. Keeps the settings and a list of
 * smoke blocks, and some logic.
 *
 * @author Pepijn Schmitz
 */
public final class SmokingBlock implements Serializable {

	public final IntLocation location;
	public final Material blockType, smokeType;
	public final int densityMin, densityMax, maxDistance;
	public final float decayChance;
	public final WindDirection fromDirection, toDirection;
	public final boolean randomSpread;

	private final Set<SmokeBlock> smokeBlocks = new HashSet<>();
	private final Map<IntLocation, Integer> occupancyCounts = new HashMap<>();
	private final Random random = new Random();

	private static final long serialVersionUID = 1L;

	public SmokingBlock(IntLocation location, Material blockType, Material smokeType, int densityMin, int densityMax, float decayChance, int maxDistance, WindDirection fromDirection, WindDirection toDirection, boolean randomSpread) {
		this.location = location;
		this.blockType = blockType;
		this.smokeType = smokeType;
		this.densityMin = densityMin;
		this.densityMax = densityMax;
		this.decayChance = decayChance;
		this.maxDistance = maxDistance;
		this.fromDirection = fromDirection;
		this.toDirection = toDirection;
		this.randomSpread = randomSpread;
	}

	/**
	 * Propagate smoke from this smoking block.
	 *
	 * @return <code>true</code> if the smoking block should continue to exist.
	 */
	public boolean tick(World world) {
		// Check whether the smoking block still exists
		final Block smokingBlock = world.getBlockAt(location.x, location.y, location.z);
		if (smokingBlock.getType() == blockType) {
			// Spawn new smoke blocks in the location of the smoker. The
			// propagate step below will move them in the clear
			final int blocksToSpawn = random.nextInt(densityMax - densityMin + 1) + densityMin;
			for (int i = 0; i < blocksToSpawn; i++)
				smokeBlocks.add(new SmokeBlock(this, location));
			final int newCount = occupancyCounts.containsKey(location) ? occupancyCounts.get(location) + blocksToSpawn : blocksToSpawn;
			occupancyCounts.put(location, newCount);

			// Propagate the smoke blocks
			for (final Iterator<SmokeBlock> i = smokeBlocks.iterator(); i.hasNext(); ) {
				final SmokeBlock smokeBlock = i.next();
				if (! smokeBlock.tick(world, random))
					i.remove();
			}

			// Update the world
			for (final Iterator<Map.Entry<IntLocation, Integer>> i = occupancyCounts.entrySet().iterator(); i.hasNext(); ) {
				final Map.Entry<IntLocation, Integer> entry = i.next();
				final IntLocation smokeCoords = entry.getKey();
				if (entry.getValue() < 1) {
					// There should be no smoke; remove it (if there is actually
					// still smoke there)
					final Block block = world.getBlockAt(smokeCoords.x, smokeCoords.y, smokeCoords.z);
					if (block.getType() == smokeType)
						block.setType(Material.AIR, false);
					i.remove();
				} else if (world.getBlockAt(smokeCoords.x, smokeCoords.y, smokeCoords.z).getType() == Material.AIR) {
					// There should be smoke, and there is currently air; place
					// the smoke
					final Block block = world.getBlockAt(smokeCoords.x, smokeCoords.y, smokeCoords.z);
					block.setType(smokeType, false);
				}
			}
			return true;
		} else {
			// The original block is gone; remove all the smoke
			removeAllSmoke(world);
			return false;
		}
	}

	void removeAllSmoke(World world) {
		smokeBlocks.clear();
		for (final IntLocation smokeLocation: occupancyCounts.keySet()) {
			// Remove the smoke, but double check that it is still there
			// (perhaps somebody removed the smoke and placed a block)
			final Block block = world.getBlockAt(smokeLocation.x, smokeLocation.y, smokeLocation.z);
			if (block.getType() == smokeType)
				block.setType(Material.AIR, false);
		}
		occupancyCounts.clear();
	}

	void update(IntLocation oldLocation, IntLocation newLocation) {
		if (! oldLocation.equals(newLocation)) {
			occupancyCounts.put(oldLocation, occupancyCounts.get(oldLocation) - 1);
			occupancyCounts.put(newLocation, occupancyCounts.containsKey(newLocation) ? occupancyCounts.get(newLocation) + 1 : 1);
		}
	}

	void remove(IntLocation location) {
		occupancyCounts.put(location, occupancyCounts.get(location) - 1);
	}


}
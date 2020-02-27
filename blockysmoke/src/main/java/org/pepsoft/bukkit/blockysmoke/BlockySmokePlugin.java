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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldSaveEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * A Bukkit plugin for creating block based smoke in Minecraft.
 *
 * @author Pepijn Schmitz
 */
public final class BlockySmokePlugin extends JavaPlugin implements Listener {

	private final Map<String, Map<IntLocation, SmokingBlock>> allBlocks = new HashMap<>();
	private final Map<String, Map<IntLocation, SmokingBlock>> activeBlocks = new HashMap<>();
	private final BlockySmokeCommandExecutor commandExecutor = new BlockySmokeCommandExecutor(this);
	private Material smokeType;
	private int densityMin, densityMax, maxDistance, minWindStrength, maxWindStrength, delay;
	private float decayChance;
	private final Random random = new Random();
	private boolean randomSpread;
	private boolean paused;
	private WindDirection windFrom, windTo;

	static WindDirection windDirection = WindDirection.N;
	static int windStrength = 1;

	static final Logger logger = Logger.getLogger("Minecraft.org.pepsoft.bukkit.blockysmoke");

	@Override
	public void onDisable() {
		for (final World world: getServer().getWorlds())
			deactivateWorld(world);
	}

	@Override
	public void onEnable() {
		// Make sure the config file exists
		saveDefaultConfig();

		// Load configuration
		final FileConfiguration config = getConfig();
		delay = config.getInt("delay");
		if (delay < 1)
			throw new IllegalArgumentException("delay < 1");

		final String smokeTypeString = config.getString("smokeType");

		try {
			smokeType = Material.valueOf(smokeTypeString.toUpperCase());
		} catch(final IllegalArgumentException e) {
			throw new IllegalArgumentException("smokeType is not valid material (" + smokeTypeString + ")");
		}

		densityMin = config.getInt("minDensity");
		if (densityMin < 0)
			throw new IllegalArgumentException("minDensity < 0");
		densityMax = config.getInt("maxDensity");
		if (densityMax < 1)
			throw new IllegalArgumentException("densityMax < 1");
		else if (densityMin > densityMax)
			throw new IllegalArgumentException("minDensity > maxDensity");
		maxDistance = config.getInt("maxDistance");
		if (maxDistance < 0)
			throw new IllegalArgumentException("maxDistance < 0");
		decayChance = Float.parseFloat(config.getString("decayChance"));
		if (decayChance <= 0.0f)
			throw new IllegalArgumentException("decayChance <= 0");
		else if (decayChance >= 1.0f)
			throw new IllegalArgumentException("decayChance >= 1");
		randomSpread = config.getBoolean("randomSpread");
		minWindStrength = config.getInt("minWindStrength");
		if (minWindStrength < 0)
			throw new IllegalArgumentException("minWindStrength < 0");
		maxWindStrength = config.getInt("maxWindStrength");
		if (maxWindStrength < 0)
			throw new IllegalArgumentException("maxWindStrength < 0");
		else if (minWindStrength > maxWindStrength)
			throw new IllegalArgumentException("minWindStrength > maxWindStrength");
		String windStr = config.getString("windFrom");
		if ((windStr != null) && (! windStr.trim().isEmpty()))
			windFrom = WindDirection.valueOf(windStr.trim().toUpperCase());
		windStr = config.getString("windTo");
		if ((windStr != null) && (! windStr.trim().isEmpty()))
			windTo = WindDirection.valueOf(windStr.trim().toUpperCase());
		if ((windFrom != null) ? (windTo == null) : (windTo != null))
			throw new IllegalArgumentException("windFrom and windTo must both be specified, or neither");
		logger.info("[BlockySmoke] Settings:");
		logger.info("[BlockySmoke]   Delay: " + delay);
		logger.info("[BlockySmoke]   Wind strength: " + minWindStrength + " - " + maxWindStrength);
		logger.info("[BlockySmoke]   Default smoke type: " + smokeType);
		logger.info("[BlockySmoke]   Default density: " + densityMin + " - " + densityMax);
		logger.info("[BlockySmoke]   Default max. distance: " + maxDistance);
		logger.info("[BlockySmoke]   Default decay chance: " + decayChance);
		logger.info("[BlockySmoke]   Default random spread: " + randomSpread);
		logger.info("[BlockySmoke]   Default wind direction: " + ((windFrom != null) ? (windFrom + " - " + windTo) : "random"));

		// Activate loaded worlds
		final Server server = getServer();
		for (final World world: server.getWorlds())
			activateWorld(world);

		// Register commands
		final PluginManager pm = server.getPluginManager();
		getCommand("createsmoker").setExecutor(commandExecutor);
		getCommand("removesmoker").setExecutor(commandExecutor);
		getCommand("removeallsmokers").setExecutor(commandExecutor);
		getCommand("pausesmokers").setExecutor(commandExecutor);
		getCommand("continuesmokers").setExecutor(commandExecutor);
		getCommand("inspectsmoker").setExecutor(commandExecutor);
		pm.registerEvents(this, this);

		// Start background processing
		server.getScheduler().scheduleSyncRepeatingTask(this, () -> {
			if (paused)
				return;

			final long start = System.currentTimeMillis();
			for (final World world: getServer().getWorlds()) {
				final String worldName = world.getName();
				final Map<IntLocation, SmokingBlock> smokingBlocks = activeBlocks.get(worldName);
				if (smokingBlocks != null) {
					final Set<SmokingBlock> blocksToRemove = new HashSet<>();
					for (final SmokingBlock smokingBlock: smokingBlocks.values())
						if (! smokingBlock.tick(world))
							blocksToRemove.add(smokingBlock);
					for (final SmokingBlock blockToRemove: blocksToRemove) {
						smokingBlocks.remove(blockToRemove.location);
						allBlocks.get(worldName).remove(blockToRemove.location);
					}
					if (smokingBlocks.isEmpty()) {
						activeBlocks.remove(worldName);
						if (allBlocks.get(worldName).isEmpty())
							allBlocks.remove(worldName);
					}
				}
			}

			if (random.nextInt(3) == 0)
				if (random.nextBoolean())
					windDirection = windDirection.clockwise();
				else
					windDirection = windDirection.counterClockwise();
			if (random.nextInt(3) == 0) {
				windStrength += random.nextInt(3) - 1;
				if (windStrength < minWindStrength)
					windStrength = minWindStrength;
				else if (windStrength > maxWindStrength)
					windStrength = maxWindStrength;
			}
			if (logger.isLoggable(Level.FINE))
				logger.fine("Updating blocky smokers took " + (System.currentTimeMillis() - start) + " ms");
		}, delay, delay);
	}

	@EventHandler(priority= EventPriority.MONITOR, ignoreCancelled=true)
	public void onWorldLoad(WorldLoadEvent event) {
		final World world = event.getWorld();
		if (logger.isLoggable(Level.FINE))
			logger.fine("[BlockySmoke] WorldLoadEvent for world " + world.getName());
		activateWorld(world);
	}

	@EventHandler(priority= EventPriority.MONITOR, ignoreCancelled=true)
	public void onWorldSave(WorldSaveEvent event) {
		final String worldName = event.getWorld().getName();
		if (logger.isLoggable(Level.FINE))
			logger.fine("[BlockySmoke] WorldSaveEvent for world " + worldName);
		saveBlocks(worldName, allBlocks.get(worldName));
	}

	@EventHandler(priority= EventPriority.MONITOR, ignoreCancelled=true)
	public void onWorldUnload(WorldUnloadEvent event) {
		final World world = event.getWorld();
		if (logger.isLoggable(Level.FINE))
			logger.fine("[BlockySmoke] WorldUnloadEvent for world " + world.getName());
		deactivateWorld(world);
	}

	@EventHandler(priority= EventPriority.MONITOR, ignoreCancelled=true)
	public void onChunkLoad(ChunkLoadEvent event) {
		final Chunk chunk = event.getChunk();
		if (logger.isLoggable(Level.FINE))
			logger.fine("[BlockySmoke] ChunkLoadEvent for chunk @ " + chunk.getX() + ", " + chunk.getZ() + " in world " + chunk.getWorld().getName());
		activateChunk(chunk);
	}

	@EventHandler(priority= EventPriority.MONITOR, ignoreCancelled=true)
	public void onChunkUnload(ChunkUnloadEvent event) {
		final Chunk chunk = event.getChunk();
		final int chunkX = chunk.getX(), chunkZ = chunk.getZ();
		final String worldName = chunk.getWorld().getName();
		if (logger.isLoggable(Level.FINE))
			logger.fine("[BlockySmoke] ChunkUnloadEvent for chunk @ " + chunkX + ", " + chunkZ + " in world " + worldName);
		final Map<IntLocation, SmokingBlock> activeBlocksForWorld = activeBlocks.get(worldName);
		if (activeBlocksForWorld != null) {
			for (final Iterator<Map.Entry<IntLocation, SmokingBlock>> i = activeBlocksForWorld.entrySet().iterator(); i.hasNext(); ) {
				final Map.Entry<IntLocation, SmokingBlock> entry = i.next();
				final IntLocation location = entry.getKey();
				if ((location.x >> 4 == chunkX) && (location.z >> 4 == chunkZ)) {
					if (logger.isLoggable(Level.FINE))
						logger.fine("[BlockySmoke] Deactivating smoker @ " + location);
					i.remove();
				}
			}
			if (activeBlocksForWorld.isEmpty())
				activeBlocks.remove(worldName);
		}
	}

	boolean createSmokingBlock(CommandSender sender, String[] args) {
		if (!sender.isOp()) {
			sender.sendMessage(ChatColor.RED + "You do not have permission to execute that command");
			return true;
		} else if (!(sender instanceof Player)) {
			sender.sendMessage(ChatColor.RED + "This command needs a target block and can only be executed in-game");
			return true;
		}
		//final HashSet<Material> transparentBlocks = new HashSet<>(Arrays.asList(Material.AIR, smokeType));
		final Block targetBlock = ((Player) sender).getTargetBlock(null, 5);
		if ((targetBlock == null) || (targetBlock.getType() == Material.AIR) || (targetBlock.getType() == smokeType)) {

			sender.sendMessage(ChatColor.RED + "No target block");
			return true;
		}
		final World world = targetBlock.getWorld();
		final String worldName = world.getName();
		Map<IntLocation, SmokingBlock> smokingBlocks = allBlocks.get(worldName);
		if (smokingBlocks == null) {
			smokingBlocks = new HashMap<>();
			allBlocks.put(worldName, smokingBlocks);
		}
		final IntLocation location = new IntLocation(targetBlock.getX(), targetBlock.getY(), targetBlock.getZ());
		final SmokingBlock existingSmokingBlock = smokingBlocks.get(location);
		int myDensityMin = densityMin, myDensityMax = densityMax, myMaxDistance = maxDistance;
		Material mySmokeType = smokeType;
		float myDecayChance = decayChance;
		WindDirection myWindFrom = windFrom, myWindTo = windTo;
		boolean myRandomSpread = randomSpread;
		for (int i = 0; i < args.length; i++) {
			final String arg = args[i].trim().toLowerCase();
			final String[] parts = arg.split("=");
			if (parts.length != 2) {
				sender.sendMessage(ChatColor.RED + "Unrecognized argument: " + args[i]);
				return true;
			} else if (parts[0].equals("id") || parts[0].equals("type")) {
				final Material material = Material.matchMaterial(parts[1]);
				if (material != null)
					mySmokeType = material;
				else {
					sender.sendMessage(ChatColor.RED + "Invalid block type: " + parts[1]);
					return true;

				}
			} else if (parts[0].equals("density"))
				try {
					myDensityMin = Integer.decode(parts[1]);
					myDensityMax = myDensityMin;
					if (myDensityMin < 1) {
						sender.sendMessage(ChatColor.RED + "Invalid density: " + parts[1]);
						return true;
					}
				} catch (final NumberFormatException e) {
					sender.sendMessage(ChatColor.RED + "Invalid density: " + parts[1]);
					return true;
				}
			else if (parts[0].equals("mindensity")) {
				try {
					myDensityMin = Integer.decode(parts[1]);
					if (myDensityMin < 1) {
						sender.sendMessage(ChatColor.RED + "Invalid minimum density: " + parts[1]);
						return true;
					}
				} catch (final NumberFormatException e) {
					sender.sendMessage(ChatColor.RED + "Invalid minimum density: " + parts[1]);
					return true;
				}
				if (myDensityMax < myDensityMin)
					myDensityMax = myDensityMin;
			} else if (parts[0].equals("maxdensity")) {
				try {
					myDensityMax = Integer.decode(parts[1]);
					if (myDensityMax < 1) {
						sender.sendMessage(ChatColor.RED + "Invalid maximum density: " + parts[1]);
						return true;
					}
				} catch (final NumberFormatException e) {
					sender.sendMessage(ChatColor.RED + "Invalid maximum density: " + parts[1]);
					return true;
				}
				if (myDensityMax < myDensityMin)
					myDensityMin = myDensityMax;
			} else if (parts[0].equals("maxdistance"))
				try {
					myMaxDistance = Integer.decode(parts[1]);
					if ((myMaxDistance < 1)) {
						sender.sendMessage(ChatColor.RED + "Invalid maximum distance: " + parts[1]);
						return true;
					}
				} catch (final NumberFormatException e) {
					sender.sendMessage(ChatColor.RED + "Invalid maximum distance: " + parts[1]);
					return true;
				}
			else if (parts[0].equals("decaychance") || parts[0].equals("decay") || parts[0].equals("chance"))
				try {
					myDecayChance = Float.parseFloat(parts[1]);
					if ((myDecayChance < 0.0f) || (myDecayChance > 1.0f)) {
						sender.sendMessage(ChatColor.RED + "Invalid decay chance: " + parts[1]);
						return true;
					}
				} catch (final NumberFormatException e) {
					sender.sendMessage(ChatColor.RED + "Invalid decay chance: " + parts[1]);
					return true;
				}
			else if (parts[0].equals("wind") || parts[0].equals("dir") || parts[0].equals("direction") || parts[0].equals("winddir")) {
				if (parts[1].equals("random")) {
					myWindFrom = null;
					myWindTo = null;
				} else
					try {
						final int p = parts[1].indexOf('-');
						if (p == -1) {
							myWindFrom = WindDirection.valueOf(parts[1].trim().toUpperCase());
							myWindTo = myWindFrom;
						} else {
							myWindFrom = WindDirection.valueOf(parts[1].substring(0, p).trim().toUpperCase());
							myWindTo = WindDirection.valueOf(parts[1].substring(p + 1).trim().toUpperCase());
						}
					} catch (final IllegalArgumentException e) {
						sender.sendMessage(ChatColor.RED + "Invalid wind direction specification: " + parts[1]);
						return true;
					}
			} else if (parts[0].equals("randomspread") || parts[0].equals("random")) {
				if (! parts[1].trim().isEmpty())
					myRandomSpread = Boolean.parseBoolean(parts[1].trim());
				else {
					sender.sendMessage(ChatColor.RED + "Invalid random spread argument: " + parts[1]);
					return true;
				}
			} else {
				sender.sendMessage(ChatColor.RED + "Unrecognized argument: " + args[i]);
				return true;
			}
		}
		if (existingSmokingBlock != null)
			existingSmokingBlock.removeAllSmoke(world);
		final SmokingBlock smokingBlock = new SmokingBlock(location, targetBlock.getType(), mySmokeType, myDensityMin, myDensityMax, myDecayChance, myMaxDistance, myWindFrom, myWindTo, myRandomSpread);
		smokingBlocks.put(location, smokingBlock);
		smokingBlocks = activeBlocks.get(worldName);
		if (smokingBlocks == null) {
			smokingBlocks = new HashMap<>();
			activeBlocks.put(worldName, smokingBlocks);
		}
		smokingBlocks.put(location, smokingBlock);
		final StringBuilder message = new StringBuilder();
		message.append(ChatColor.YELLOW).append("Smoking block ").append((existingSmokingBlock != null) ? "updated" : "created").append(" at ").append(location);
		message.append("; ").append(describeSmokingBlock(smokingBlock, false));
		sender.sendMessage(message.toString());
		if (paused)
			sender.sendMessage(ChatColor.YELLOW + "Please note: blocky smokers are currently paused!");
		return true;
	}

	boolean removeSmoker(CommandSender sender) {
		if (! sender.isOp()) {
			sender.sendMessage(ChatColor.RED + "You do not have permission to execute that command");
			return true;
		} else if (! (sender instanceof Player)) {
			sender.sendMessage(ChatColor.RED + "This command needs a target block and can only be executed in-game");
			return true;
		}
		final HashSet<Material> transparentBlocks = new HashSet<>(Arrays.asList(Material.AIR, smokeType));
		final Block targetBlock = ((Player) sender).getTargetBlock(transparentBlocks, 5);
		if ((targetBlock == null) || (targetBlock.getType() == Material.AIR) || (targetBlock.getType() == smokeType)) {
			sender.sendMessage(ChatColor.RED + "No target block");
			return true;
		}
		final World world = targetBlock.getWorld();
		final String worldName = world.getName();
		final Map<IntLocation, SmokingBlock> smokingBlocks = allBlocks.get(worldName);
		if (smokingBlocks != null) {
			final IntLocation location = new IntLocation(targetBlock.getX(), targetBlock.getY(), targetBlock.getZ());
			final SmokingBlock existingSmokingBlock = smokingBlocks.remove(location);
			if (existingSmokingBlock != null) {
				existingSmokingBlock.removeAllSmoke(world);
				final Map<IntLocation, SmokingBlock> myActiveBlocks = activeBlocks.get(worldName);
				if (myActiveBlocks != null) {
					myActiveBlocks.remove(location);
					if (myActiveBlocks.isEmpty())
						activeBlocks.remove(worldName);
				}
				if (smokingBlocks.isEmpty())
					allBlocks.remove(worldName);
				sender.sendMessage(ChatColor.YELLOW + "Blocky smoker deleted");
				return true;
			}
		}
		sender.sendMessage(ChatColor.RED + "The targeted block (type: " + targetBlock.getType() + ", coords: " + targetBlock.getX() + "," + targetBlock.getY() + "," + targetBlock.getZ() + ") is not a blocky smoker");
		return true;
	}

	boolean removeAllSmokers(CommandSender sender) {
		if (! sender.isOp()) {
			sender.sendMessage(ChatColor.RED + "You do not have permission to execute that command");
			return true;
		} else if (! (sender instanceof Player)) {
			sender.sendMessage(ChatColor.RED + "This command needs a target world and can only be executed in-game");
			return true;
		}
		final World world = ((Player) sender).getWorld();
		final String worldName = world.getName();
		final Map<IntLocation, SmokingBlock> blocks = allBlocks.get(worldName);
		if (blocks != null) {
			for (final SmokingBlock smokingBlock: blocks.values())
				smokingBlock.removeAllSmoke(world);
			allBlocks.remove(worldName);
			activeBlocks.remove(worldName);
		}
		sender.sendMessage(ChatColor.YELLOW + "All block smokers deleted from world " + worldName);
		return true;
	}

	boolean pauseSmokers(CommandSender sender) {
		if (! sender.isOp()) {
			sender.sendMessage(ChatColor.RED + "You do not have permission to execute that command");
			return true;
		}
		if (paused) {
			sender.sendMessage(ChatColor.RED + "Blocky smokers already paused");
			return true;
		}
		for (final Map.Entry<String, Map<IntLocation, SmokingBlock>> entry: activeBlocks.entrySet()) {
			final World world = getServer().getWorld(entry.getKey());
			for (final SmokingBlock smokingBlock: entry.getValue().values())
				smokingBlock.removeAllSmoke(world);
		}
		paused = true;
		sender.sendMessage(ChatColor.YELLOW + "All blocky smokers paused");
		return true;
	}

	boolean continueSmokers(CommandSender sender) {
		if (! sender.isOp()) {
			sender.sendMessage(ChatColor.RED + "You do not have permission to execute that command");
			return true;
		}
		if (! paused) {
			sender.sendMessage(ChatColor.RED + "Blocky smokers are not paused");
			return true;
		}
		paused = false;
		sender.sendMessage(ChatColor.YELLOW + "All blocky smokers unpaused");
		return true;
	}

	boolean inspectSmoker(CommandSender sender) {
		if (! (sender instanceof Player)) {
			sender.sendMessage(ChatColor.RED + "This command needs a target block and can only be executed in-game");
			return true;
		}
		final HashSet<Material> transparentBlocks = new HashSet<>(Arrays.asList(Material.AIR, smokeType));
		final Block targetBlock = ((Player) sender).getTargetBlock(transparentBlocks, 5);
		if ((targetBlock == null) || (targetBlock.getType() == Material.AIR) || (targetBlock.getType() == smokeType)) {
			sender.sendMessage(ChatColor.RED + "No target block");
			return true;
		}
		final World world = targetBlock.getWorld();
		final String worldName = world.getName();
		final Map<IntLocation, SmokingBlock> smokingBlocks = allBlocks.get(worldName);
		if (smokingBlocks != null) {
			final IntLocation location = new IntLocation(targetBlock.getX(), targetBlock.getY(), targetBlock.getZ());
			final SmokingBlock smokingBlock = smokingBlocks.get(location);
			if (smokingBlock != null) {
				sender.sendMessage(ChatColor.YELLOW + "Blocky smoker @ " + targetBlock.getX() + "," + targetBlock.getY() + "," + targetBlock.getZ() + " has the following settings: " + describeSmokingBlock(smokingBlock, true));
				return true;
			}
		}
		sender.sendMessage(ChatColor.RED + "The targeted block (type: " + targetBlock.getType() + ", coords: " + targetBlock.getX() + "," + targetBlock.getY() + "," + targetBlock.getZ() + ") is not a blocky smoker");
		return true;
	}

	private String describeSmokingBlock(SmokingBlock smokingBlock, boolean includeType) {
		final StringBuilder description = new StringBuilder();
		if (includeType || (smokingBlock.smokeType != smokeType))
			description.append("type: ").append(smokingBlock.smokeType.name());
		if (description.length() > 0)
			description.append(", ");
		description.append("decayChance: ").append(smokingBlock.decayChance);
		description.append(", maxDistance: ").append(smokingBlock.maxDistance);
		if (smokingBlock.densityMin == smokingBlock.densityMax)
			description.append(", density: ").append(smokingBlock.densityMin);
		else {
			description.append(", minDensity: ").append(smokingBlock.densityMin);
			description.append(", maxDensity: ").append(smokingBlock.densityMax);
		}
		if (smokingBlock.fromDirection != null) {
			if (smokingBlock.fromDirection == smokingBlock.toDirection)
				description.append(", wind: ").append(smokingBlock.fromDirection.name());
			else
				description.append(", wind: ").append(smokingBlock.fromDirection.name()).append('-').append(smokingBlock.toDirection.name());
		} else
			description.append(", wind: random");
		description.append(", randomSpread: ").append(smokingBlock.randomSpread);
		return description.toString();
	}

	private void activateWorld(World world) {
		final String worldName = world.getName();
		logger.info("[BlockySmoke] Activating world " + worldName);
		final Map<IntLocation, SmokingBlock> blocks = loadBlocks(worldName);
		if (blocks != null) {
			allBlocks.put(worldName, blocks);
			for (final Chunk chunk: world.getLoadedChunks())
				activateChunk(chunk);
		}
	}

	private void deactivateWorld(World world) {
		final String worldName = world.getName();
		logger.info("[BlockySmoke] Deactivating world " + worldName);
		final Map<IntLocation, SmokingBlock> blocks = allBlocks.remove(worldName);
		saveBlocks(worldName, blocks);
		activeBlocks.remove(worldName);
	}

	private void activateChunk(Chunk chunk) {
		if (logger.isLoggable(Level.FINE))
			logger.fine("[BlockySmoke] Activating chunk @ " + chunk.getX() + ", " + chunk.getZ());
		final String worldName = chunk.getWorld().getName();
		if (allBlocks.containsKey(worldName)) {
			final int chunkX = chunk.getX(), chunkZ = chunk.getZ();
			for (final Map.Entry<IntLocation, SmokingBlock> entry: allBlocks.get(worldName).entrySet()) {
				final IntLocation location = entry.getKey();
				if ((location.x >> 4 == chunkX) && (location.z >> 4 == chunkZ)) {
					if (logger.isLoggable(Level.FINE))
						logger.fine("[BlockySmoke] Activating smoker @ " + location);
					Map<IntLocation, SmokingBlock> activeBlocksForWorld = activeBlocks.get(worldName);
					if (activeBlocksForWorld == null) {
						activeBlocksForWorld = new HashMap<>();
						activeBlocks.put(worldName, activeBlocksForWorld);
					}
					final SmokingBlock smokingBlock = entry.getValue();
					activeBlocksForWorld.put(location, smokingBlock);

					// If we are currently paused there should be no smoke;
					// which might still exist in the world for this smoker
					if (paused)
						smokingBlock.removeAllSmoke(chunk.getWorld());
				}
			}
		}
	}

	@SuppressWarnings("unchecked") // Guaranteed by Java
	private Map<IntLocation, SmokingBlock> loadBlocks(String worldName) {
		if (logger.isLoggable(Level.FINE))
			logger.fine("[BlockySmoke] Loading blocks for world " + worldName);
		final File configDir = getDataFolder();
		final File blocksFile = new File(configDir, "smokeblocks_" + sanitizeFilename(worldName) + ".bin");
		if (blocksFile.isFile())
			try {
				final ObjectInputStream in = new ObjectInputStream(new GZIPInputStream(new FileInputStream(blocksFile)));
				try {
					final Map<IntLocation, SmokingBlock> blocks = (Map<IntLocation, SmokingBlock>) in.readObject();
					logger.info("[BlockySmoke] Loaded " + blocks.size() + " blocky smokers for world " + worldName);
					return blocks;
				} finally {
					in.close();
				}
			} catch (final IOException e) {
				logger.log(Level.SEVERE, "[BlockySmoke] I/O error while loading saved smoke blocks!", e);
				return null;
			} catch (final ClassNotFoundException e) {
				logger.log(Level.SEVERE, "[BlockySmoke] ClassNotFoundException while loading saved smoke blocks!", e);
				return null;
			}
		else
			return null;
	}

	private void saveBlocks(String worldName, Map<IntLocation, SmokingBlock> blocks) {
		logger.info("[BlockySmoke] Saving " + blocks.size() + " blocky smokers for world " + worldName);
		final File configDir = getDataFolder();
		if (! configDir.isDirectory())
			configDir.mkdirs();
		final File blocksFile = new File(configDir, "smokeblocks_" + sanitizeFilename(worldName) + ".bin");
		if ((blocks != null) && (! blocks.isEmpty()))
			try {
				final ObjectOutputStream out = new ObjectOutputStream(new GZIPOutputStream(new FileOutputStream(blocksFile)));
				try {
					out.writeObject(blocks);
				} finally {
					out.close();
				}
			} catch (final IOException e) {
				logger.log(Level.SEVERE, "[BlockySmoke] I/O error while saving smoke blocks; smoke block data not saved!", e);
			}
		else if (blocksFile.isFile())
			if (! blocksFile.delete())
				logger.severe("[BlockySmoke] Could not delete smoke block data file " + blocksFile.getAbsolutePath() + "!");
	}

	private String sanitizeFilename(String dirtyFilename) {
		final StringBuilder sb = new StringBuilder(dirtyFilename.length());
		for (int i = 0; i < dirtyFilename.length(); i++) {
			final char c = dirtyFilename.charAt(i);
			if (Character.isLetterOrDigit(c))
				sb.append(c);
			else
				sb.append('_');
		}
		return sb.toString();
	}

}
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

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * The command executor for the BlockySmoke plugin.
 * 
 * @author Pepijn Schmitz
 */
public class BlockySmokeCommandExecutor implements CommandExecutor {
    public BlockySmokeCommandExecutor(BlockySmokePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("createsmoker")) {
            return plugin.createSmokingBlock(sender, args);
        } else if (command.getName().equalsIgnoreCase("removesmoker")) {
            return plugin.removeSmoker(sender);
        } else if (command.getName().equalsIgnoreCase("removeallsmokers")) {
            return plugin.removeAllSmokers(sender);
        } else if (command.getName().equalsIgnoreCase("pausesmokers")) {
            return plugin.pauseSmokers(sender);
        } else if (command.getName().equalsIgnoreCase("continuesmokers")) {
            return plugin.continueSmokers(sender);
        } else if (command.getName().equalsIgnoreCase("inspectsmoker")) {
            return plugin.inspectSmoker(sender);
        }
        return false;
    }

    private final BlockySmokePlugin plugin;
}
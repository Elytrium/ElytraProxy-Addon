/*
 * Copyright (C) 2021 - 2023 Elytrium
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.elytrium.velocitytools.commands;

import com.google.common.collect.ImmutableList;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.elytrium.velocitytools.VelocityTools;
import net.kyori.adventure.text.Component;

public class VelocityToolsCommand implements SimpleCommand {

  public final VelocityTools plugin;

  public VelocityToolsCommand(VelocityTools plugin) {
    this.plugin = plugin;
  }

  @Override
  public List<String> suggest(SimpleCommand.Invocation invocation) {
    CommandSource source = invocation.source();
    String[] args = invocation.arguments();

    if (args.length == 0) {
      return this.getSubCommands()
          .filter(cmd -> source.hasPermission("velocitytools.admin." + cmd))
          .collect(Collectors.toList());
    } else if (args.length == 1) {
      return this.getSubCommands()
          .filter(cmd -> source.hasPermission("velocitytools.admin." + cmd))
          .filter(str -> str.regionMatches(true, 0, args[0], 0, args[0].length()))
          .collect(Collectors.toList());
    }

    return ImmutableList.of();
  }

  @Override
  public void execute(SimpleCommand.Invocation invocation) {
    CommandSource source = invocation.source();
    String[] args = invocation.arguments();

    if (args.length == 1) {
      if (args[0].equalsIgnoreCase("reload") && source.hasPermission("velocitytools.admin.reload")) {
        this.plugin.reload();
        source.sendMessage(Component.text("§aСonfig reloaded successfully!"));
      }

      return;
    }

    this.showHelp(source);
  }

  private void showHelp(CommandSource source) {
    source.sendMessage(Component.text("§eThis server is using VelocityTools"));
    source.sendMessage(Component.text("§e(c) 2021 - 2023 Elytrium"));
    source.sendMessage(Component.text("§ahttps://elytrium.net/github/"));
    source.sendMessage(Component.text("§r"));
    source.sendMessage(Component.text("§fAvailable subcommands:"));
    // Java moment
    this.getSubCommands()
        .filter(cmd -> source.hasPermission("limboauth.admin." + cmd))
        .forEach(cmd -> {
          if (cmd.equals("reload")) {
            source.sendMessage(Component.text("    §a/velocitytools reload §8- §eReload config"));
          }
        });
  }

  private Stream<String> getSubCommands() {
    return Stream.of("reload");
  }
}

/*
 * Copyright (C) 2021 Elytrium
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

package net.elytrium.elytraproxy_addon;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.LegacyChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import net.elytrium.elytraproxy.ElytraProxy;
import net.elytrium.elytraproxy.database.MySqlDatabase;
import net.elytrium.elytraproxy_addon.commands.HubCommand;
import net.elytrium.elytraproxy_addon.commands.LinkCommand;
import net.elytrium.elytraproxy_addon.commands.ReloadCommand;
import net.elytrium.elytraproxy_addon.config.Settings;
import java.nio.file.Path;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;

@Plugin(
  id = "elytraproxy_addon",
  name = "ElytraProxy addon",
  version = "1.1-SNAPSHOT",
  description = "ElytraProxy addon",
  url = "https://ely.su/",
  authors = {"mdxd44", "hevav"}
)
public class Main {

  private final ProxyServer server;
  private final Path dataDirectory;
  private final ElytraProxy getElytraProxy;
  private long getTotalBlockedConnections;
  private long cachedBots;
  public MySqlDatabase mySqlDatabase;

  @Inject
  public Main(ProxyServer server, @DataDirectory Path dataDirectory,
              ElytraProxy getElytraProxy) {
    this.server = server;
    this.dataDirectory = dataDirectory;
    this.getElytraProxy = getElytraProxy;
  }

  @Subscribe
  public void onProxyInitialization(ProxyInitializeEvent event) throws SQLException {
    reload();

    mySqlDatabase = new MySqlDatabase(Settings.IMP.SQL.HOSTNAME, Settings.IMP.SQL.DATABASE, Settings.IMP.SQL.USER, Settings.IMP.SQL.PASSWORD);
    mySqlDatabase.makeTable("users", ImmutableMap.of("uuid", "VARCHAR(36)"));

    server.getCommandManager().register("hub", new HubCommand(server));
    server.getCommandManager().register("lobby", new HubCommand(server));

    server.getCommandManager().register("link", new LinkCommand(this));

    server.getCommandManager().register("elytraproxy_addon_reload", new ReloadCommand(this));

    server.getChannelRegistrar().register(new LegacyChannelIdentifier("WDL|INIT")); // legacy
    server.getChannelRegistrar().register(MinecraftChannelIdentifier.create("wdl", "init"));
    server.getChannelRegistrar().register(new LegacyChannelIdentifier("PERMISSIONREPL")); // legacy
    server.getChannelRegistrar().register(MinecraftChannelIdentifier.create("permissionrepl", ""));
    startIncrementSiteBotCounterIfNeeded();
  }

  public void startIncrementSiteBotCounterIfNeeded() {
    new Timer().scheduleAtFixedRate(new TimerTask() {
      @Override
      public void run() {
        getTotalBlockedConnections = getElytraProxy.getStatistics().getBlockedConnections();
        try {
          if (!(getTotalBlockedConnections == 0) && cachedBots < getTotalBlockedConnections) {
            long diff = getTotalBlockedConnections - cachedBots;
            try {
              List<NameValuePair> request = Arrays.asList(
                  new BasicNameValuePair("masterKey", Settings.IMP.MASTER_KEY),
                  new BasicNameValuePair("increment", Long.toString(diff))
              );
              CloseableHttpClient httpClient = HttpClients.createDefault();
              HttpPost post = new HttpPost(Settings.IMP.URL_FOR_BOT_COUNTER);
              post.setEntity(new UrlEncodedFormEntity(request, StandardCharsets.UTF_8));
              CloseableHttpResponse httpResponse = httpClient.execute(post);
              cachedBots = getTotalBlockedConnections;
            } catch (IOException e) {
              e.printStackTrace();
            }
          }
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    }, 60000, 60000);
  }

  public void reload() {
    try {
      Settings.IMP.reload(new File(dataDirectory.toFile(), "config.yml"));
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Subscribe
  public void onProxyShutdown(ProxyShutdownEvent event) {
    server.getChannelRegistrar().unregister(new LegacyChannelIdentifier("WDL|INIT")); // legacy
    server.getChannelRegistrar().unregister(MinecraftChannelIdentifier.create("wdl", "init"));
    server.getChannelRegistrar().unregister(new LegacyChannelIdentifier("PERMISSIONREPL")); // legacy
    server.getChannelRegistrar().unregister(MinecraftChannelIdentifier.create("permissionrepl", ""));
  }

  @Subscribe
  public void onPluginMessage(PluginMessageEvent e) {
    Player p = (Player) e.getSource();

    if ("WDL|INIT".equalsIgnoreCase(e.getIdentifier().getId())
        || "wdl:init".equalsIgnoreCase(e.getIdentifier().getId())) {
      p.disconnect(LegacyComponentSerializer.legacyAmpersand().deserialize(Settings.IMP.WDL_KICK.replace("{NL}", "\n")));
    }

    if (("PERMISSIONSREPL".equalsIgnoreCase(e.getIdentifier().getId())
        || "permissionrepl".equalsIgnoreCase(e.getIdentifier().getId()))
            && new String(e.getData()).contains("mod.worlddownloader")) {
      p.disconnect(LegacyComponentSerializer.legacyAmpersand().deserialize(Settings.IMP.WDL_KICK.replace("{NL}", "\n")));
    }
  }
}

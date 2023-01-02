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

package net.elytrium.velocitytools.hooks;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.connection.MinecraftSessionHandler;
import com.velocitypowered.proxy.connection.backend.BackendPlaySessionHandler;
import com.velocitypowered.proxy.connection.backend.VelocityServerConnection;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.protocol.MinecraftPacket;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.packet.PluginMessage;
import com.velocitypowered.proxy.protocol.util.PluginMessageUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.function.Supplier;
import net.elytrium.java.commons.reflection.ReflectionException;
import net.elytrium.velocitytools.Settings;

class PluginMessageHook extends PluginMessage implements PacketHook {

  protected static MethodHandle SERVER_CONNECTION_FIELD;

  private final boolean enabled = Settings.IMP.TOOLS.BRAND_CHANGER.REWRITE_IN_GAME;
  private final String inGameBrand = Settings.IMP.TOOLS.BRAND_CHANGER.IN_GAME_BRAND;

  @Override
  public boolean handle(MinecraftSessionHandler handler) {
    if (handler instanceof BackendPlaySessionHandler && this.enabled && PluginMessageUtil.isMcBrand(this)) {
      try {
        ConnectedPlayer player = ((VelocityServerConnection) SERVER_CONNECTION_FIELD.invoke(handler)).getPlayer();
        player.getConnection().write(this.rewriteMinecraftBrand(this, player.getProtocolVersion()));
        return true;
      } catch (Throwable e) {
        throw new ReflectionException(e);
      }
    }

    return super.handle(handler);
  }

  private PluginMessage rewriteMinecraftBrand(PluginMessage message, ProtocolVersion protocolVersion) {
    String currentBrand = PluginMessageUtil.readBrandMessage(message.content());
    String rewrittenBrand = MessageFormat.format(this.inGameBrand, currentBrand);
    ByteBuf rewrittenBuf = Unpooled.buffer();
    if (protocolVersion.compareTo(ProtocolVersion.MINECRAFT_1_8) >= 0) {
      ProtocolUtils.writeString(rewrittenBuf, rewrittenBrand);
    } else {
      rewrittenBuf.writeCharSequence(rewrittenBrand, StandardCharsets.UTF_8);
    }

    return new PluginMessage(message.getChannel(), rewrittenBuf);
  }

  @Override
  public Supplier<MinecraftPacket> getHook() {
    return PluginMessageHook::new;
  }

  @Override
  public Class<? extends MinecraftPacket> getType() {
    return PluginMessage.class;
  }

  @Override
  public Class<? extends MinecraftPacket> getHookClass() {
    return this.getClass();
  }
}

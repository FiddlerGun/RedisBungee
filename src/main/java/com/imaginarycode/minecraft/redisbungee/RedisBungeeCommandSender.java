/**
 * Copyright © 2013 tuxed <write@imaginarycode.com>
 * This work is free. You can redistribute it and/or modify it under the
 * terms of the Do What The Fuck You Want To Public License, Version 2,
 * as published by Sam Hocevar. See http://www.wtfpl.net/ for more details.
 */
package com.imaginarycode.minecraft.redisbungee;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;

import java.util.Collection;

/**
 * This class is the CommandSender that RedisBungee uses to dispatch commands to BungeeCord.
 * <p/>
 * It inherits all permissions of the console command sender. Sending messages and modifying permissions are no-ops.
 *
 * @author tuxed
 * @since 0.2.3
 */
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RedisBungeeCommandSender implements CommandSender {
    @Override
    public String getName() {
        return "RedisBungee";
    }

    @Override
    public void sendMessage(String s) {
        // no-op
    }

    @Override
    public void sendMessages(String... strings) {
        // no-op
    }

    @Override
    public Collection<String> getGroups() {
        return ProxyServer.getInstance().getConsole().getGroups();
    }

    @Override
    public void addGroups(String... strings) {
        // no-op
    }

    @Override
    public void removeGroups(String... strings) {
        // no-op
    }

    @Override
    public boolean hasPermission(String s) {
        return ProxyServer.getInstance().getConsole().hasPermission(s);
    }

    @Override
    public void setPermission(String s, boolean b) {
        // no-op
    }
}

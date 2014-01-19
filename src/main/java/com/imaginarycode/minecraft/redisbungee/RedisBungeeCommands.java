/**
 * Copyright © 2013 tuxed <write@imaginarycode.com>
 * This work is free. You can redistribute it and/or modify it under the
 * terms of the Do What The Fuck You Want To Public License, Version 2,
 * as published by Sam Hocevar. See http://www.wtfpl.net/ for more details.
 */
package com.imaginarycode.minecraft.redisbungee;

import com.google.common.base.Joiner;
import com.google.common.collect.Multimap;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.plugin.Command;

import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.TreeSet;

/**
 * This class contains subclasses that are used for the commands RedisBungee overrides or includes: /glist, /find and /lastseen.
 * <p/>
 * All classes use the {@link RedisBungeeAPI}.
 *
 * @author tuxed
 * @since 0.2.3
 */
class RedisBungeeCommands {
    private static final String NO_PLAYER_SPECIFIED = ChatColor.RED + "You must specify a player name.";
    private static final String PLAYER_NOT_FOUND = ChatColor.RED + "No such player found.";
    private static final String NO_COMMAND_SPECIFIED = ChatColor.RED + "You must specify a command to be run.";

    public static class GlistCommand extends Command {
        GlistCommand() {
            super("glist", "bungeecord.command.list", "redisbungee");
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            int count = RedisBungee.getApi().getPlayerCount();
            String playersOnline = ChatColor.YELLOW + String.valueOf(count) + " player(s) are currently online.";
            if (args.length > 0 && args[0].equals("showall")) {
                if (RedisBungee.getConfiguration().getBoolean("canonical-glist", true)) {
                    Multimap<String, String> serverToPlayers = RedisBungee.getApi().getServerToPlayers();
                    for (String server : new TreeSet<>(serverToPlayers.keySet())) {
                        sender.sendMessage(ChatColor.GREEN + "[" + server + "] " + ChatColor.YELLOW +
                                "(" + serverToPlayers.get(server).size() + "): " + ChatColor.WHITE +
                                Joiner.on(", ").join(serverToPlayers.get(server)));
                    }
                } else {
                    sender.sendMessage(ChatColor.YELLOW + "Players: " + Joiner.on(", ").join(RedisBungee.getApi().getPlayersOnline()));
                }
                sender.sendMessage(playersOnline);
            } else {
                sender.sendMessage(playersOnline);
                sender.sendMessage(ChatColor.YELLOW + "To see all players online, use /glist showall.");
            }
        }
    }

    public static class FindCommand extends Command {
        FindCommand() {
            super("find", "bungeecord.command.find");
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            if (args.length > 0) {
                ServerInfo si = RedisBungee.getApi().getServerFor(args[0]);
                if (si != null) {
                    sender.sendMessage(ChatColor.BLUE + args[0] + " is on " + si.getName() + ".");
                } else {
                    sender.sendMessage(PLAYER_NOT_FOUND);
                }
            } else {
                sender.sendMessage(NO_PLAYER_SPECIFIED);
            }
        }
    }

    public static class LastSeenCommand extends Command {
        LastSeenCommand() {
            super("lastseen", "redisbungee.command.lastseen");
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            if (args.length > 0) {
                long secs = RedisBungee.getApi().getLastOnline(args[0]);
                if (secs == 0) {
                    sender.sendMessage(ChatColor.GREEN + args[0] + " is currently online.");
                } else if (secs != -1) {
                    sender.sendMessage(ChatColor.BLUE + " was last online on " + new SimpleDateFormat().format(secs) + ".");
                } else {
                    sender.sendMessage(ChatColor.RED + args[0] + " has never been online.");
                }
            } else {
                sender.sendMessage(NO_PLAYER_SPECIFIED);
            }
        }
    }

    public static class IpCommand extends Command {
        IpCommand() {
            super("ip", "redisbungee.command.ip", "playerip");
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            if (args.length > 0) {
                InetAddress ia = RedisBungee.getApi().getPlayerIp(args[0]);
                if (ia != null) {
                    sender.sendMessage(ChatColor.GREEN + args[0] + " is connected from " + ia.toString() + ".");
                } else {
                    sender.sendMessage(PLAYER_NOT_FOUND);
                }
            } else {
                sender.sendMessage(NO_PLAYER_SPECIFIED);
            }
        }
    }

    public static class SendToAll extends Command {
        SendToAll() {
            super("sendtoall", "redisbungee.command.sendtoall");
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            if (args.length > 0) {
                String command = Joiner.on(" ").skipNulls().join(args);
                RedisBungee.getApi().sendProxyCommand(command);
                sender.sendMessage(ChatColor.GREEN + "Sent the command /" + command + " to all proxies.");
            } else {
                sender.sendMessage(NO_COMMAND_SPECIFIED);
            }
        }
    }

    public static class ServerId extends Command {
        ServerId() {
            super("serverid", "redisbungee.command.serverid");
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            sender.sendMessage(ChatColor.YELLOW + "You are on " + RedisBungee.getApi().getServerId() + ".");
        }
    }
}

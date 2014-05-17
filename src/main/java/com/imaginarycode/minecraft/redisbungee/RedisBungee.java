/**
 * Copyright © 2013 tuxed <write@imaginarycode.com>
 * This work is free. You can redistribute it and/or modify it under the
 * terms of the Do What The Fuck You Want To Public License, Version 2,
 * as published by Sam Hocevar. See http://www.wtfpl.net/ for more details.
 */
package com.imaginarycode.minecraft.redisbungee;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.io.ByteStreams;
import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.imaginarycode.minecraft.redisbungee.pubsub.GenericPubSubListener;
import com.imaginarycode.minecraft.redisbungee.util.UUIDTranslator;
import lombok.Getter;
import lombok.NonNull;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.exceptions.JedisException;

import java.io.*;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * The RedisBungee plugin.
 * <p>
 * The only function of interest is {@link #getApi()}, which exposes some functions in this class.
 */
public final class RedisBungee extends Plugin {
    private static Configuration configuration;
    @Getter
    private JedisPool pool;
    @Getter
    private RedisBungeeEventConsumer consumer;
    @Getter
    private UUIDTranslator uuidTranslator;
    @Getter
    private static Gson gson = new Gson();
    @Getter
    private static JsonParser jsonParser = new JsonParser();
    private static RedisBungeeAPI api;
    private static PubSubListener psl = null;
    private List<String> serverIds;
    private int globalCount;

    /**
     * Fetch the {@link RedisBungeeAPI} object created on plugin start.
     *
     * @return the {@link RedisBungeeAPI} object
     */
    public static RedisBungeeAPI getApi() {
        return api;
    }

    static Configuration getConfiguration() {
        return configuration;
    }

    final List<String> getServerIds() {
        return serverIds;
    }

    final List<String> getCurrentServerIds() {
        Jedis jedis = pool.getResource();
        try {
            ImmutableList.Builder<String> servers = ImmutableList.builder();
            Map<String, String> heartbeats = jedis.hgetAll("heartbeats");
            for (Map.Entry<String, String> entry : heartbeats.entrySet()) {
                try {
                    long stamp = Long.valueOf(entry.getValue());
                    if (System.currentTimeMillis() < stamp + 30000)
                        servers.add(entry.getKey());
                } catch (NumberFormatException ignored) {
                }
            }
            return servers.build();
        } catch (JedisConnectionException e) {
            getLogger().log(Level.SEVERE, "Unable to fetch all server IDs", e);
            return Collections.singletonList(configuration.getString("server-id"));
        } finally {
            pool.returnResource(jedis);
        }
    }

    static PubSubListener getPubSubListener() {
        return psl;
    }

    final Multimap<String, UUID> serversToPlayers() {
        ImmutableMultimap.Builder<String, UUID> multimapBuilder = ImmutableMultimap.builder();
        for (UUID p : getPlayers()) {
            ServerInfo si = getServerFor(p);
            if (si != null)
                multimapBuilder = multimapBuilder.put(si.getName(), p);
        }
        return multimapBuilder.build();
    }

    final int getCount() {
        return globalCount;
    }

    final int getCurrentCount() {
        int c = getProxy().getOnlineCount();
        if (pool != null) {
            Jedis rsc = pool.getResource();
            try {
                List<String> serverIds = getServerIds();
                Map<String, String> counts = rsc.hgetAll("playerCounts");
                for (Map.Entry<String, String> entry : counts.entrySet()) {
                    if (!serverIds.contains(entry.getKey()))
                        continue;

                    if (entry.getKey().equals(configuration.getString("server-id"))) continue;

                    try {
                        c += Integer.valueOf(entry.getValue());
                    } catch (NumberFormatException e) {
                        rsc.hset("playerCounts", entry.getKey(), "0");
                    }
                }
            } catch (JedisConnectionException e) {
                // Redis server has disappeared!
                getLogger().log(Level.SEVERE, "Unable to get connection from pool - did your Redis server go away?", e);
                pool.returnBrokenResource(rsc);
                throw new RuntimeException("Unable to get total player count", e);
            } finally {
                pool.returnResource(rsc);
            }
        }
        return c;
    }

    final Set<UUID> getLocalPlayers() {
        ImmutableSet.Builder<UUID> setBuilder = ImmutableSet.builder();
        for (ProxiedPlayer pp : getProxy().getPlayers())
            setBuilder = setBuilder.add(pp.getUniqueId());
        return setBuilder.build();
    }

    final Set<String> getLocalPlayersAsUuidStrings() {
        ImmutableSet.Builder<String> setBuilder = ImmutableSet.builder();
        for (ProxiedPlayer pp : getProxy().getPlayers())
            setBuilder = setBuilder.add(pp.getUniqueId().toString());
        return setBuilder.build();
    }

    final Set<UUID> getPlayers() {
        ImmutableSet.Builder<UUID> setBuilder = ImmutableSet.<UUID>builder().addAll(getLocalPlayers());
        if (pool != null) {
            Jedis rsc = pool.getResource();
            try {
                List<String> keys = new ArrayList<>();
                for (String i : getServerIds()) {
                    if (i.equals(configuration.getString("server-id")))
                        continue;

                    keys.add("server:" + i + ":usersOnline");
                }
                if (!keys.isEmpty()) {
                    Set<String> users = rsc.sunion(keys.toArray(new String[keys.size()]));
                    if (users != null && !users.isEmpty()) {
                        for (String user : users) {
                            if (UUIDTranslator.UUID_PATTERN.matcher(user).find()) {
                                setBuilder = setBuilder.add(UUID.fromString(user));
                            }
                        }
                    }
                }
            } catch (JedisConnectionException e) {
                // Redis server has disappeared!
                getLogger().log(Level.SEVERE, "Unable to get connection from pool - did your Redis server go away?", e);
                pool.returnBrokenResource(rsc);
                throw new RuntimeException("Unable to get all players online", e);
            } finally {
                pool.returnResource(rsc);
            }
        }
        return setBuilder.build();
    }

    final Set<UUID> getPlayersOnServer(@NonNull String server) {
        checkArgument(getProxy().getServerInfo(server) != null, "server doesn't exist");
        return ImmutableSet.copyOf(serversToPlayers().get(server));
    }

    final ServerInfo getServerFor(@NonNull UUID uuid) {
        ServerInfo server = null;
        if (getProxy().getPlayer(uuid) != null) return getProxy().getPlayer(uuid).getServer().getInfo();
        if (pool != null) {
            Jedis tmpRsc = pool.getResource();
            try {
                if (tmpRsc.hexists("player:" + uuid, "server"))
                    server = getProxy().getServerInfo(tmpRsc.hget("player:" + uuid, "server"));
            } catch (JedisConnectionException e) {
                // Redis server has disappeared!
                getLogger().log(Level.SEVERE, "Unable to get connection from pool - did your Redis server go away?", e);
                pool.returnBrokenResource(tmpRsc);
                throw new RuntimeException("Unable to get server for " + uuid, e);
            } finally {
                pool.returnResource(tmpRsc);
            }
        }
        return server;
    }

    final long getLastOnline(@NonNull UUID uuid) {
        long time = -1L;
        if (getProxy().getPlayer(uuid) != null) return 0;
        if (pool != null) {
            Jedis tmpRsc = pool.getResource();
            try {
                if (tmpRsc.hexists("player:" + uuid, "online"))
                    try {
                        time = Long.valueOf(tmpRsc.hget("player:" + uuid, "online"));
                    } catch (NumberFormatException e) {
                        getLogger().info("I found a funny number for when " + uuid + " was last online!");
                        boolean found = false;
                        for (String proxyId : getServerIds()) {
                            if (proxyId.equals(configuration.getString("server-id"))) continue;
                            if (tmpRsc.sismember("server:" + proxyId + ":usersOnline", uuid.toString())) {
                                found = true;
                                break;
                            }
                        }
                        String value = "0";
                        if (!found) {
                            value = String.valueOf(System.currentTimeMillis());
                            getLogger().info(uuid + " isn't online. Setting to current time.");
                        } else {
                            getLogger().info(uuid + " is online. Setting to 0. Please check your BungeeCord instances.");
                            getLogger().info("If they are working properly, and this error does not resolve in a few minutes, please let Tux know!");
                        }
                        tmpRsc.hset("player:" + uuid, "online", value);
                    }
            } catch (JedisConnectionException e) {
                // Redis server has disappeared!
                getLogger().log(Level.SEVERE, "Unable to get connection from pool - did your Redis server go away?", e);
                pool.returnBrokenResource(tmpRsc);
                throw new RuntimeException("Unable to get last time online for " + uuid, e);
            } finally {
                pool.returnResource(tmpRsc);
            }
        }
        return time;
    }

    final InetAddress getIpAddress(@NonNull UUID uuid) {
        if (getProxy().getPlayer(uuid) != null)
            return getProxy().getPlayer(uuid).getAddress().getAddress();
        InetAddress ia = null;
        if (pool != null) {
            Jedis tmpRsc = pool.getResource();
            try {
                if (tmpRsc.hexists("player:" + uuid, "ip"))
                    ia = InetAddress.getByName(tmpRsc.hget("player:" + uuid, "ip"));
            } catch (JedisConnectionException e) {
                // Redis server has disappeared!
                getLogger().log(Level.SEVERE, "Unable to get connection from pool - did your Redis server go away?", e);
                pool.returnBrokenResource(tmpRsc);
                throw new RuntimeException("Unable to fetch IP address for " + uuid, e);
            } catch (UnknownHostException ignored) {
                // Best to just return null
            } finally {
                pool.returnResource(tmpRsc);
            }
        }
        return ia;
    }

    final void sendProxyCommand(@NonNull String proxyId, @NonNull String command) {
        checkArgument(getServerIds().contains(proxyId) || proxyId.equals("allservers"), "proxyId is invalid");
        Jedis jedis = pool.getResource();
        try {
            jedis.publish("redisbungee-" + proxyId, command);
        } catch (JedisConnectionException e) {
            // Redis server has disappeared!
            getLogger().log(Level.SEVERE, "Unable to get connection from pool - did your Redis server go away?", e);
            pool.returnBrokenResource(jedis);
            throw new RuntimeException("Unable to publish command", e);
        } finally {
            pool.returnResource(jedis);
        }
    }

    @Override
    public void onEnable() {
        try {
            loadConfig();
        } catch (IOException e) {
            throw new RuntimeException("Unable to load/save config", e);
        } catch (JedisConnectionException e) {
            throw new RuntimeException("Unable to connect to your Redis server!", e);
        }
        if (pool != null) {
            Jedis tmpRsc = pool.getResource();
            try {
                tmpRsc.hset("playerCounts", configuration.getString("server-id"), "0"); // reset
                tmpRsc.hset("heartbeats", configuration.getString("server-id"), String.valueOf(System.currentTimeMillis()));
            } finally {
                pool.returnResource(tmpRsc);
            }
            serverIds = getCurrentServerIds();
            globalCount = getCurrentCount();
            uuidTranslator = new UUIDTranslator(this);
            getProxy().getScheduler().schedule(this, new Runnable() {
                @Override
                public void run() {
                    Jedis rsc = pool.getResource();
                    try {
                        rsc.hset("playerCounts", configuration.getString("server-id"), String.valueOf(getProxy().getOnlineCount()));
                        rsc.hset("heartbeats", configuration.getString("server-id"), String.valueOf(System.currentTimeMillis()));
                    } catch (JedisConnectionException e) {
                        // Redis server has disappeared!
                        getLogger().log(Level.SEVERE, "Unable to update proxy counts - did your Redis server go away?", e);
                        pool.returnBrokenResource(rsc);
                    } finally {
                        pool.returnResource(rsc);
                    }
                    serverIds = getCurrentServerIds();
                    globalCount = getCurrentCount();
                }
            }, 0, 3, TimeUnit.SECONDS);
            consumer = new RedisBungeeEventConsumer(this);
            new Thread(consumer, "RedisBungee Consumer Thread").start();
            getProxy().getPluginManager().registerCommand(this, new RedisBungeeCommands.GlistCommand(this));
            getProxy().getPluginManager().registerCommand(this, new RedisBungeeCommands.FindCommand(this));
            getProxy().getPluginManager().registerCommand(this, new RedisBungeeCommands.LastSeenCommand(this));
            getProxy().getPluginManager().registerCommand(this, new RedisBungeeCommands.IpCommand(this));
            getProxy().getPluginManager().registerCommand(this, new RedisBungeeCommands.SendToAll(this));
            getProxy().getPluginManager().registerCommand(this, new RedisBungeeCommands.ServerId(this));
            getProxy().getPluginManager().registerCommand(this, new RedisBungeeCommands.ServerIds());
            getProxy().getPluginManager().registerListener(this, new RedisBungeeListener(this));
            api = new RedisBungeeAPI(this);
            psl = new PubSubListener();
            getProxy().getScheduler().runAsync(this, psl);
            getProxy().getScheduler().schedule(this, new Runnable() {
                @Override
                public void run() {
                    Jedis tmpRsc = pool.getResource();
                    try {
                        Set<String> players = getLocalPlayersAsUuidStrings();
                        for (String member : tmpRsc.smembers("server:" + configuration.getString("server-id") + ":usersOnline"))
                            if (!players.contains(member)) {
                                // Are they simply on a different proxy?
                                boolean found = false;
                                for (String proxyId : getServerIds()) {
                                    if (proxyId.equals(configuration.getString("server-id"))) continue;
                                    if (tmpRsc.sismember("server:" + proxyId + ":usersOnline", member)) {
                                        // Just clean up the set.
                                        found = true;
                                        break;
                                    }
                                }
                                if (!found) {
                                    RedisUtil.cleanUpPlayer(member, tmpRsc);
                                    getLogger().warning("Player found in set that was not found locally and globally: " + member);
                                } else {
                                    tmpRsc.srem("server:" + configuration.getString("server-id") + ":usersOnline", member);
                                    getLogger().warning("Player found in set that was not found locally, but is on another proxy: " + member);
                                }
                            }
                    } finally {
                        pool.returnResource(tmpRsc);
                    }
                }
            }, 0, 3, TimeUnit.MINUTES);
        }
        getProxy().registerChannel("RedisBungee");
    }

    @Override
    public void onDisable() {
        if (pool != null) {
            // Poison the PubSub listener
            getProxy().getScheduler().cancel(this);
            getLogger().info("Waiting for consumer to finish writing data...");
            consumer.stop();
            Jedis tmpRsc = pool.getResource();
            try {
                tmpRsc.hdel("playerCounts", configuration.getString("server-id"), "0");
                if (tmpRsc.scard("server:" + configuration.getString("server-id") + ":usersOnline") > 0) {
                    for (String member : tmpRsc.smembers("server:" + configuration.getString("server-id") + ":usersOnline"))
                        RedisUtil.cleanUpPlayer(member, tmpRsc);
                }
                tmpRsc.hdel("heartbeats", configuration.getString("server-id"));
            } finally {
                pool.returnResource(tmpRsc);
            }
            pool.destroy();
        }
    }

    private void loadConfig() throws IOException, JedisConnectionException {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdir();
        }

        File file = new File(getDataFolder(), "config.yml");

        if (!file.exists()) {
            file.createNewFile();
            try (InputStream in = getResourceAsStream("example_config.yml");
                 OutputStream out = new FileOutputStream(file)) {
                ByteStreams.copy(in, out);
            }
        }

        configuration = ConfigurationProvider.getProvider(YamlConfiguration.class).load(file);

        String redisServer = configuration.getString("redis-server", "localhost");
        int redisPort = configuration.getInt("redis-port", 6379);
        String redisPassword = configuration.getString("redis-password");

        if (redisPassword != null && (redisPassword.equals("") || redisPassword.equals("none"))) {
            redisPassword = null;
        }

        // Configuration sanity checks.
        if (configuration.get("server-id") == null || configuration.getString("server-id").equals("")) {
            throw new RuntimeException("server-id is not specified in the configuration or is empty");
        }

        if (redisServer != null) {
            if (!redisServer.equals("")) {
                JedisPoolConfig config = new JedisPoolConfig();
                config.setMaxTotal(configuration.getInt("max-redis-connections", -1));
                pool = new JedisPool(config, redisServer, redisPort, 0, redisPassword);
                // Test the connection
                Jedis rsc = null;
                try {
                    rsc = pool.getResource();
                    rsc.exists(String.valueOf(System.currentTimeMillis()));
                    // If that worked, now we can check for an existing, alive Bungee:
                    File crashFile = new File(getDataFolder(), "restarted_from_crash.txt");
                    if (crashFile.exists())
                        crashFile.delete();
                    else if (rsc.hexists("heartbeat", configuration.getString("server-id"))) {
                        getLogger().severe("You have launched a possible imposter BungeeCord instance. Another instance is already running.");
                        getLogger().severe("For data consistency reasons, RedisBungee will now disable itself.");
                        getLogger().severe("If this instance is coming up from a crash, create a file in your RedisBungee plugins directory with the name 'restarted_from_crash.txt' and RedisBungee will not perform this check.");
                        throw new RuntimeException("Possible imposter instance!");
                    }
                    getLogger().log(Level.INFO, "Successfully connected to Redis.");
                } catch (JedisConnectionException e) {
                    if (rsc != null)
                        pool.returnBrokenResource(rsc);
                    pool.destroy();
                    pool = null;
                    rsc = null;
                    throw e;
                } finally {
                    if (rsc != null && pool != null) {
                        pool.returnResource(rsc);
                    }
                }
            }
        } else {
            throw new RuntimeException("No redis server specified!");
        }
    }

    class PubSubListener implements Runnable {
        private Jedis rsc;
        private GenericPubSubListener listener;

        private PubSubListener() {
        }

        @Override
        public void run() {
            try {
                rsc = pool.getResource();
                listener = new GenericPubSubListener(RedisBungee.this);
                rsc.subscribe(listener, "redisbungee-" + configuration.getString("server-id"), "redisbungee-allservers", "redisbungee-firehose");
            } catch (JedisException | ClassCastException ignored) {
            }
        }

        public void addChannel(String... channel) {
            listener.subscribe(channel);
        }

        public void removeChannel(String... channel) {
            listener.unsubscribe(channel);
        }
    }
}

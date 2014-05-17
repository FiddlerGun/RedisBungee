package com.imaginarycode.minecraft.redisbungee.pubsub;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.imaginarycode.minecraft.redisbungee.RedisBungee;
import com.imaginarycode.minecraft.redisbungee.events.firehose.PlayerChangedServerNetworkEvent;
import com.imaginarycode.minecraft.redisbungee.events.firehose.PlayerJoinedNetworkEvent;
import com.imaginarycode.minecraft.redisbungee.events.firehose.PlayerQuitNetworkEvent;
import com.imaginarycode.minecraft.redisbungee.events.pubsub.PubSubMessageEvent;
import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.plugin.Event;
import redis.clients.jedis.JedisPubSub;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RequiredArgsConstructor
public class GenericPubSubListener extends JedisPubSub {
    private final RedisBungee plugin;
    private final ExecutorService executor = Executors.newCachedThreadPool(new ThreadFactoryBuilder().setDaemon(true).setNameFormat("RedisBungee PubSub Handler - #%d").build());

    @Override
    public void onMessage(final String s, final String s2) {
        if (s2.trim().length() == 0) return;

        if (s.equals("redisbungee-firehose")) {
            // Firehose pubsub events are handled differently, they are events.
            // Spawn a worker thread to deal with it.
            executor.submit(new Runnable() {
                @Override
                public void run() {
                    JsonElement element = RedisBungee.getJsonParser().parse(s2);

                    if (!element.isJsonObject())
                        return;

                    JsonObject object = element.getAsJsonObject();

                    Event event;

                    switch (object.get("type").getAsString()) {
                        case "login":
                            event = new PlayerJoinedNetworkEvent(UUID.fromString(object.get("uuid").getAsString()));
                            break;
                        case "logoff":
                            event = new PlayerQuitNetworkEvent(UUID.fromString(object.get("uuid").getAsString()));
                            break;
                        case "server":
                            ServerInfo info = plugin.getProxy().getServerInfo(object.get("server").getAsString());
                            if (info == null)
                                return;
                            event = new PlayerChangedServerNetworkEvent(UUID.fromString(object.get("uuid").getAsString()),
                                    info);
                            break;
                        default:
                            return;
                    }

                    plugin.getProxy().getPluginManager().callEvent(event);
                }
            });
        } else {
            executor.submit(new Runnable() {
                @Override
                public void run() {
                    plugin.getProxy().getPluginManager().callEvent(new PubSubMessageEvent(s, s2));
                }
            });
        }
    }

    @Override
    public void onPMessage(String s, String s2, String s3) {
    }

    @Override
    public void onSubscribe(String s, int i) {
    }

    @Override
    public void onUnsubscribe(String s, int i) {
    }

    @Override
    public void onPUnsubscribe(String s, int i) {
    }

    @Override
    public void onPSubscribe(String s, int i) {
    }
}

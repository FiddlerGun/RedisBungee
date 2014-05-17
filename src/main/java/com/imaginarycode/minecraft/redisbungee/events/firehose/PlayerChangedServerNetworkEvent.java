package com.imaginarycode.minecraft.redisbungee.events.firehose;

import lombok.Getter;
import net.md_5.bungee.api.config.ServerInfo;

import java.util.UUID;

public class PlayerChangedServerNetworkEvent extends AbstractNetworkEvent {
    @Getter
    private final ServerInfo server;

    public PlayerChangedServerNetworkEvent(UUID player, ServerInfo server) {
        super(player);
        this.server = server;
    }
}

package com.imaginarycode.minecraft.redisbungee.events.firehose;

import java.util.UUID;

public class PlayerJoinedNetworkEvent extends AbstractNetworkEvent {
    public PlayerJoinedNetworkEvent(UUID player) {
        super(player);
    }
}

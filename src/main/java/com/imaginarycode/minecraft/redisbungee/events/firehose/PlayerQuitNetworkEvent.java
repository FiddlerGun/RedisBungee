package com.imaginarycode.minecraft.redisbungee.events.firehose;

import java.util.UUID;

public class PlayerQuitNetworkEvent extends AbstractNetworkEvent {
    public PlayerQuitNetworkEvent(UUID player) {
        super(player);
    }
}

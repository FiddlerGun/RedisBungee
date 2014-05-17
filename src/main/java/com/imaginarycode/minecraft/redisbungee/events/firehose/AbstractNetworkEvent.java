package com.imaginarycode.minecraft.redisbungee.events.firehose;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.md_5.bungee.api.plugin.Event;

import java.util.UUID;

@AllArgsConstructor
public abstract class AbstractNetworkEvent extends Event {
    @Getter
    private final UUID player;
}

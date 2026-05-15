package com.example.vibecraftmod.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.nio.charset.StandardCharsets;

public record VibeCraftInputPayload(String json, int protocolVersion) implements CustomPayload {

        public static final int PROTOCOL_VERSION = 1;
        public static final CustomPayload.Id<VibeCraftInputPayload> ID =
            new CustomPayload.Id<>(Identifier.of("vibecraft", "input"));

        public static final PacketCodec<RegistryByteBuf, VibeCraftInputPayload> CODEC =
            PacketCodec.of(
                (value, buf) -> {
                buf.writeBytes(value.json().getBytes(StandardCharsets.UTF_8));
                },
                buf -> {
                byte[] bytes = new byte[buf.readableBytes()];
                buf.readBytes(bytes);
                return new VibeCraftInputPayload(new String(bytes, StandardCharsets.UTF_8), PROTOCOL_VERSION);
                });

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
}

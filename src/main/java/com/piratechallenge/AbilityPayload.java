package com.piratechallenge;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record AbilityPayload(int id) implements CustomPayload {
    public static final CustomPayload.Id<AbilityPayload> ID = new CustomPayload.Id<>(Identifier.of(PirateChallenge.MOD_ID, "ability"));
    public static final PacketCodec<RegistryByteBuf, AbilityPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, AbilityPayload::id,
            AbilityPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
}

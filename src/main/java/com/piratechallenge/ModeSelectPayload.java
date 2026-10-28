package com.piratechallenge;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ModeSelectPayload(int dummy) implements CustomPayload {
    public static final CustomPayload.Id<ModeSelectPayload> ID = new CustomPayload.Id<>(Identifier.of(PirateChallenge.MOD_ID, "mode_select"));
    public static final PacketCodec<RegistryByteBuf, ModeSelectPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, ModeSelectPayload::dummy,
            ModeSelectPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
}

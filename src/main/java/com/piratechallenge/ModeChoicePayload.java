package com.piratechallenge;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ModeChoicePayload(boolean hard) implements CustomPayload {
    public static final CustomPayload.Id<ModeChoicePayload> ID = new CustomPayload.Id<>(Identifier.of(PirateChallenge.MOD_ID, "mode_choice"));
    public static final PacketCodec<RegistryByteBuf, ModeChoicePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.BOOL, ModeChoicePayload::hard,
            ModeChoicePayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
}

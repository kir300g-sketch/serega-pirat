package com.piratechallenge;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Клиент -> сервер (Минёр): сапёр пройден (won = true) или подорвались (false). */
public record MinerResultPayload(boolean won) implements CustomPayload {
    public static final CustomPayload.Id<MinerResultPayload> ID = new CustomPayload.Id<>(Identifier.of(PirateChallenge.MOD_ID, "miner_result"));
    public static final PacketCodec<RegistryByteBuf, MinerResultPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.BOOL, MinerResultPayload::won,
            MinerResultPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
}

package com.piratechallenge;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Клиент -> сервер: итог шахматной партии (1 победа, 0 ничья, -1 поражение). */
public record ChessResultPayload(int result) implements CustomPayload {
    public static final CustomPayload.Id<ChessResultPayload> ID = new CustomPayload.Id<>(Identifier.of(PirateChallenge.MOD_ID, "chess_result"));
    public static final PacketCodec<RegistryByteBuf, ChessResultPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, ChessResultPayload::result,
            ChessResultPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
}

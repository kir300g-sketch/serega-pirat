package com.piratechallenge;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Сервер -> клиент (ФП АМ): мана, перезарядки навыков (тики), щит Counterspell, убито хейтеров. */
public record AmStatePayload(int mana, int cd1, int cd2, int cd3, int counter, int kills) implements CustomPayload {
    public static final CustomPayload.Id<AmStatePayload> ID = new CustomPayload.Id<>(Identifier.of(PirateChallenge.MOD_ID, "am_state"));
    public static final PacketCodec<RegistryByteBuf, AmStatePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, AmStatePayload::mana,
            PacketCodecs.VAR_INT, AmStatePayload::cd1,
            PacketCodecs.VAR_INT, AmStatePayload::cd2,
            PacketCodecs.VAR_INT, AmStatePayload::cd3,
            PacketCodecs.VAR_INT, AmStatePayload::counter,
            PacketCodecs.VAR_INT, AmStatePayload::kills,
            AmStatePayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
}

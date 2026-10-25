package com.piratechallenge;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Клиент -> сервер (ФП АМ): применить навык (0 Mana Break, 1 Blink, 2 Counterspell, 3 Mana Void) в точку курсора. */
public record SkillPayload(int skill, double x, double y, double z) implements CustomPayload {
    public static final CustomPayload.Id<SkillPayload> ID = new CustomPayload.Id<>(Identifier.of(PirateChallenge.MOD_ID, "skill"));
    public static final PacketCodec<RegistryByteBuf, SkillPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, SkillPayload::skill,
            PacketCodecs.DOUBLE, SkillPayload::x,
            PacketCodecs.DOUBLE, SkillPayload::y,
            PacketCodecs.DOUBLE, SkillPayload::z,
            SkillPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
}

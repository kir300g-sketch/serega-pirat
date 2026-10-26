package com.piratechallenge;

import com.piratechallenge.effect.EffectUtil;
import net.minecraft.entity.attribute.EntityAttributeModifier.Operation;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;

/**
 * Вечные дебафы, которые копятся с количеством прослушанных треков.
 * Чтобы добавить новый — просто допиши ещё один Debuff в список ALL.
 */
public final class GlobalDebuffs {
    public interface Apply { void set(ServerPlayerEntity p, boolean on); }

    public record Debuff(int threshold, String name, String description, Apply apply) {}

    public static final List<Debuff> ALL = List.of(
            new Debuff(5, "Апатия",
                    "Регулярное слушание сереги пирата заставляет вас чувствовать себя хуево.",
                    (p, on) -> {
                        if (on) {
                            EffectUtil.ensure(p, EntityAttributes.GENERIC_ATTACK_DAMAGE, EffectUtil.id("apathy_damage"), -1.5, Operation.ADD_VALUE);
                            EffectUtil.ensure(p, EntityAttributes.GENERIC_MOVEMENT_SPEED, EffectUtil.id("apathy_speed"), -0.15, Operation.ADD_MULTIPLIED_TOTAL);
                        } else {
                            EffectUtil.remove(p, EntityAttributes.GENERIC_ATTACK_DAMAGE, EffectUtil.id("apathy_damage"));
                            EffectUtil.remove(p, EntityAttributes.GENERIC_MOVEMENT_SPEED, EffectUtil.id("apathy_speed"));
                        }
                    }),
            new Debuff(10, "Депрессия",
                    "Вас накрывает депрессия. Навязчивые мысли будут посещать вас всё чаще.",
                    (p, on) -> {})
    );

    public static void tick(ServerPlayerEntity p, int listened) {
        if (p.age % 20 != 0) return;
        for (Debuff d : ALL) d.apply().set(p, listened >= d.threshold());
    }

    private GlobalDebuffs() {}
}

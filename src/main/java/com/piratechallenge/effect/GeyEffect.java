package com.piratechallenge.effect;

import com.piratechallenge.ChallengeSave;
import com.piratechallenge.PlayerData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * «Гей»: санкция на 3 трека вперёд (сатира на ст. 6.21 КоАП РФ). Пока санкции действуют (этот трек и 3 следующих),
 * за тобой охотятся «настоящие мужики» (см. Men). Здесь — начало санкций и усиленная погоня.
 */
public class GeyEffect implements TrackEffect {
    @Override
    public void start(ServerPlayerEntity p, PlayerData d) {
        d.sanctionTracks = 3;
        ChallengeSave.get(p.getServer()).markDirty();
        p.sendMessage(Text.literal("§c[РФ, ст. 6.21] На вас наложена санкция на 3 трека вперёд: максимум здоровья −2 сердца, копание медленнее, и «настоящие мужики» будут вас искать."), false);
        for (int i = 0; i < 4; i++) Men.spawn(p, d);
    }

    @Override
    public void tick(ServerPlayerEntity p, PlayerData d, int elapsed, int total) {
        Men.tick(p, d, elapsed, total, 10);
    }

    @Override
    public void end(ServerPlayerEntity p, PlayerData d) {
        p.sendMessage(Text.literal("§7Санкции действуют ещё на " + d.sanctionTracks + " трека."), false);
    }
}

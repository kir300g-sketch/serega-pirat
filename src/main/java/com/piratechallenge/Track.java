package com.piratechallenge;

/**
 * Список треков. Чтобы добавить новый: добавь строку сюда, положи .ogg с именем soundId
 * в assets/pirate_challenge/sounds/, добавь запись в sounds.json и класс эффекта в ChallengeManager.
 *
 * seconds — ДЛИТЕЛЬНОСТЬ ТРЕКА В СЕКУНДАХ. Поставь реальную длину своего файла!
 */
public enum Track {
    VIBEMAN("vibeman", "Вайбмен",
            "Вы ловите дикий вайб танцпола! Мобы пляшут, хотбар скачет, а стоять на месте нельзя — получите урон.",
            188, 0xFF55FF),
    TP_NA_AME("tp_na_ame", "ТП на аме",
            "Вы получили священный Radiance! Жар выжигает всё вокруг, вы быстрее, выше, сильнее и неуязвимее.",
            156, 0xFFD700),
    MOY_TOPOR("moy_topor", "Мой топор",
            "Вы в ярости! Управление путается, кнопки жмутся сами, а первый попавшийся моб — ваша цель до самого конца.",
            114, 0xFF2222),
    KACHALKA("kachalka", "Качалка",
            "Вы огромны! Один удар ломает 10×10 блоков. Приседайте, чтобы качаться, иначе потеряете форму и умрёте.",
            166, 0xFF8800),
    V_ETOY_TRAVE("v_etoy_trave", "В этой траве",
            "Вас дико накрывает... Мир плывёт, вокруг фантомные монстры. После отходняка будет похмелье.",
            132, 0x55FF55),
    YA_VZLETAYU_VVERH("ya_vzletayu_vverh", "Я взлетаю вверх",
            "Вы летите до самого космоса! Отстреливайтесь от хейтеров, а в конце сделайте ватер-дроп!",
            159, 0xFFE066),
    RUSSELL_CROWE("russell_crowe", "Рассел Кроу",
            "Вы дед инсайд и одиночка. Мир серый, волосы закрывают глаза, а каждые 20 секунд всё чернеет.",
            122, 0xAAAAAA),
    NATALIA("natalia", "Наталия",
            "Вы убийца-псих! Убейте 30 душ (жителей), пока играет трек. Голод сильнее, ешьте сырое мясо.",
            209, 0xAA0000),
    ZOMBIE_APOCALYPSE("zombie_apocalypse", "Зомби Апокалипсис",
            "Начался зомби-апокалипсис! Три волны: сначала их мало, потом больше, а в конце — целая орда. Выживи.",
            164, 0x66AA33),
    THIEF("vor", "Ну и что, что я вор?",
            "На вас балаклава, обзор почти нулевой. Крадите у жителей по ПКМ (5 сек), не попадитесь — иначе приедет полиция.",
            176, 0x333333),
    ORANGE("apelsin", "Апельсин",
            "Вы — апельсин! Двигаетесь только прыжками (прыжок + WASD = 1 блок). У вас 9 долек, мобы грызут одну за 3 секунды.",
            177, 0xFF8800);

    public final String soundId;
    public final String title;
    public final String description;
    public final int seconds;
    public final int color;

    private static final Track[] VALUES = values();

    Track(String soundId, String title, String description, int seconds, int color) {
        this.soundId = soundId;
        this.title = title;
        this.description = description;
        this.seconds = seconds;
        this.color = color;
    }

    public static Track byId(int id) {
        return (id < 0 || id >= VALUES.length) ? null : VALUES[id];
    }
}

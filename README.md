# Pirate Challenge (Minecraft Fabric 1.21.1)

Челлендж-мод: каждые 30 секунд передышки включается случайный трек Сереги Пирата (без повторов),
и у каждого трека свой баф/дебаф. На экране — название трека, описание, таймер и счётчик прослушанных треков
(цифры краснеют с ростом). После 5 треков — вечная «Апатия», после 10 — «Выгорание» (список расширяется в `GlobalDebuffs.java`).

## Что нужно сделать
1. Положи свои треки в `src/main/resources/assets/pirate_challenge/sounds/` в формате **.ogg (Vorbis)**:
   `vibeman.ogg, tp_na_ame.ogg, moy_topor.ogg, kachalka.ogg, v_etoy_trave.ogg, ya_vzletayu_vverh.ogg, russell_crowe.ogg, natalia.ogg`
2. В `Track.java` поставь реальную длительность каждого трека в секундах (`seconds`) — эффект длится ровно столько.
3. Залей проект на GitHub — Actions (`.github/workflows/build.yml`) соберёт `.jar`, он будет в Artifacts.
   Локально: установи Gradle 8.10+ и запусти `gradle build` (или открой в IntelliJ IDEA). Нужна Java 21.
4. `.jar` из `build/libs/` + Fabric API кидай в папку `mods`.

## Структура
- `Track.java` — треки, названия, описания, длительность
- `ChallengeManager.java` — цикл трек → 30 сек отдых → трек, дебафы, сохранение
- `effect/*Effect.java` — серверная логика каждого трека
- `client/HudOverlay.java` — экранные эффекты, заголовки, счётчик
- `client/PirateChallengeClient.java` + `mixin/KeyboardInputMixin.java` — путаница управления (Мой топор)
- `RadianceItem.java` — Радик

## Добавить новый трек
1. строка в `Track.java`, 2. запись в `sounds.json`, 3. `.ogg`, 4. класс `XxxEffect implements TrackEffect`
и регистрация в `ChallengeManager`, 5. оверлей в `HudOverlay.overlay(...)`.

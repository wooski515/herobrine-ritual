package com.herobrine.ritual;

/**
 * Види Херобріна. Кожен має свій скін, ритуал, умови природного спавну, пасивку та активну здібність.
 * ВАЖЛИВО: нові види додавати тільки в кінець (у збереженнях зберігається порядковий номер).
 */
public enum HerobrineVariant {
    CLASSIC("classic", 400, 0x00A8A8, 0xFFFFFF),
    NETHER("nether", 500, 0x96201A, 0xFFD23C),
    ENDER("ender", 300, 0x28123C, 0xE65AFF),
    FROST("frost", 600, 0x78B4E6, 0x5ADCFF),
    FOREST("forest", 500, 0x326E2D, 0xC8FF50),
    GHOST("ghost", 700, 0x9698AC, 0xAAFFC8),
    STORM("storm", 450, 0x303446, 0xFFFF8C),
    DESERT("desert", 500, 0xD2B478, 0xFFAA28),
    OCEAN("ocean", 500, 0x1E7878, 0x78FFF0),
    MINER("miner", 400, 0xE66E1E, 0xFFFFFF);

    private final String id;
    private final int skillCooldownTicks;
    private final int primaryColor;
    private final int eyeColor;

    HerobrineVariant(String id, int skillCooldownTicks, int primaryColor, int eyeColor) {
        this.id = id;
        this.skillCooldownTicks = skillCooldownTicks;
        this.primaryColor = primaryColor;
        this.eyeColor = eyeColor;
    }

    public String id() {
        return id;
    }

    public int skillCooldownTicks() {
        return skillCooldownTicks;
    }

    public int primaryColor() {
        return primaryColor;
    }

    public int eyeColor() {
        return eyeColor;
    }

    public String translationKey() {
        return "variant.herobrine_ritual." + id;
    }

    /** Тег на гравці: «цей вид уже пійманий хоча б раз» (для Херодексу). */
    public String caughtTag() {
        return "herobrine_caught_" + id;
    }

    /** Старий тег із версії 1.x (один вид на гравця). Зараз використовується лише для Херодексу. */
    public String legacyOwnerTag() {
        return "herobrine_owned_" + id;
    }

    public static HerobrineVariant byOrdinal(int ordinal) {
        HerobrineVariant[] values = values();
        return (ordinal >= 0 && ordinal < values.length) ? values[ordinal] : CLASSIC;
    }
}

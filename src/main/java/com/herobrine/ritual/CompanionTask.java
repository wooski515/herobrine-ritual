package com.herobrine.ritual;

/** Робота, яку виконує компаньйон. */
public enum CompanionTask {
    /** Іде за власником, захищає його. */
    FOLLOW("follow"),
    /** Сидить на місці. */
    STAY("stay"),
    /** Охороняє точку: б'є ворожих мобів у радіусі, потім повертається на пост. */
    GUARD("guard"),
    /** Добуває руду (і прокладає штольню, коли руди поруч немає). */
    MINE("mine"),
    /** Збирає предмети з землі / з сундука-джерела та носить їх у сундук-склад. */
    HAUL("haul");

    private final String id;

    CompanionTask(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public String translationKey() {
        return "task.herobrine_ritual." + id;
    }

    public CompanionTask next() {
        CompanionTask[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static CompanionTask byOrdinal(int ordinal) {
        CompanionTask[] values = values();
        return (ordinal >= 0 && ordinal < values.length) ? values[ordinal] : FOLLOW;
    }
}

package zcylas.totality.api.rpg.rest;

/**
 * Player-facing Short Rest duration choices, expressed in Minecraft time.
 * 1 MC hour = 1000 ticks. ONE_HOUR is kept around for future content
 * (e.g. reading a book one chapter per Short Rest) even though THIRTY_MIN
 * is the more typical choice.
 */
public enum ShortRestLength {
    THIRTY_MIN(500),
    ONE_HOUR(1000),
    TWO_HOURS(2000);

    private final int ticks;

    ShortRestLength(int ticks) {
        this.ticks = ticks;
    }

    public int getTicks() {
        return ticks;
    }
}

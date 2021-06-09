import java.time.Instant;

public class MovementInfo {

    private final String description;
    private final Instant timestamp;
    private final int value;
    private final int balanceAfter;

    public MovementInfo(int value, String description, Instant timestamp, int balanceAfter) {
        this.value = value;
        this.description = description;
        this.timestamp = timestamp;
        this.balanceAfter = balanceAfter;
    }

    public String getDescription() {
        return description;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public int getValue() {
        return value;
    }

    public int getBalanceAfter() {
        return balanceAfter;
    }
}

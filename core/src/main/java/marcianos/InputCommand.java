package marcianos;

/** Per-frame input state consumed by simulation logic. */
public final class InputCommand {
    private static final InputCommand NONE = new InputCommand(false, false, false, false, false, false);

    private final boolean rotateLeft;
    private final boolean rotateRight;
    private final boolean thrust;
    private final boolean fire;
    private final boolean shield;
    private final boolean hyperspace;

    public InputCommand(boolean rotateLeft, boolean rotateRight, boolean thrust,
                        boolean fire, boolean shield, boolean hyperspace) {
        this.rotateLeft = rotateLeft;
        this.rotateRight = rotateRight;
        this.thrust = thrust;
        this.fire = fire;
        this.shield = shield;
        this.hyperspace = hyperspace;
    }

    public static InputCommand none() {
        return NONE;
    }

    public boolean rotateLeft() { return rotateLeft; }
    public boolean rotateRight() { return rotateRight; }
    public boolean thrust() { return thrust; }
    public boolean fire() { return fire; }
    public boolean shield() { return shield; }
    public boolean hyperspace() { return hyperspace; }
}

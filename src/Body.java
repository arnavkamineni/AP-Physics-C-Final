import java.awt.Color;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Represents a gravitational body in the three-body simulation.
 * Holds mass, kinematic state, a force accumulator, a color-coded trail,
 * and a display name.  Mutable state is managed here; integration logic
 * lives in the simulation panel.
 */
public class Body {

    /** Maximum number of trail positions retained. */
    public static final int MAX_TRAIL = 500;

    // --- Identity / appearance ---
    private final String name;
    private final Color  color;
    private final double mass;       // kilograms (simulation units)
    private final double radius;     // display radius in pixels

    // --- Kinematic state ---
    private Vector2D position;
    private Vector2D velocity;

    // --- Force accumulator (reset each integration step) ---
    private Vector2D force;

    // --- Trail (ring buffer of past positions) ---
    private final Deque<Vector2D> trail;

    // --- Saved initial conditions for reset ---
    private final Vector2D initialPosition;
    private final Vector2D initialVelocity;

    /**
     * Constructs a Body with all parameters specified.
     *
     * @param name     display name
     * @param color    display colour
     * @param mass     mass in simulation units
     * @param radius   display radius in pixels
     * @param position initial position
     * @param velocity initial velocity
     */
    public Body(String name, Color color, double mass, double radius,
                Vector2D position, Vector2D velocity) {
        this.name            = name;
        this.color           = color;
        this.mass            = mass;
        this.radius          = radius;
        this.position        = position;
        this.velocity        = velocity;
        this.force           = Vector2D.ZERO;
        this.trail           = new ArrayDeque<>(MAX_TRAIL);
        this.initialPosition = position;
        this.initialVelocity = velocity;
    }

    // -------------------------------------------------------------------------
    // Physics helpers
    // -------------------------------------------------------------------------

    /** Clears the accumulated force to zero. Call at the start of each step. */
    public void resetForce() {
        force = Vector2D.ZERO;
    }

    /** Adds a force vector to the accumulator. */
    public void applyForce(Vector2D f) {
        force = force.add(f);
    }

    /** Returns the current accumulated force (do not modify directly). */
    public Vector2D getForce() {
        return force;
    }

    /**
     * Records the current position into the trail buffer.
     * Evicts the oldest entry when the buffer is full.
     */
    public void recordTrail() {
        if (trail.size() >= MAX_TRAIL) {
            trail.pollFirst();
        }
        trail.addLast(new Vector2D(position.x, position.y));
    }

    /** Clears all trail history. */
    public void clearTrail() {
        trail.clear();
    }

    /** Resets position, velocity, force, and trail to initial conditions. */
    public void reset() {
        position = initialPosition;
        velocity = initialVelocity;
        force    = Vector2D.ZERO;
        trail.clear();
    }

    // -------------------------------------------------------------------------
    // Getters / setters
    // -------------------------------------------------------------------------

    public String getName()   { return name;   }
    public Color  getColor()  { return color;  }
    public double getMass()   { return mass;   }
    public double getRadius() { return radius; }

    public Vector2D getPosition() { return position; }
    public Vector2D getVelocity() { return velocity; }

    public void setPosition(Vector2D p) { position = p; }
    public void setVelocity(Vector2D v) { velocity = v; }

    /**
     * Returns a snapshot of the trail as an array ordered oldest→newest.
     * The returned array is a copy; callers may freely iterate it.
     */
    public Vector2D[] getTrailSnapshot() {
        return trail.toArray(new Vector2D[0]);
    }

    /** Returns the number of trail entries currently stored. */
    public int getTrailSize() {
        return trail.size();
    }

    @Override
    public String toString() {
        return String.format("Body[%s m=%.2e pos=%s vel=%s]",
            name, mass, position, velocity);
    }
}

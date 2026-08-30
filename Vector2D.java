/**
 * Immutable 2D vector class supporting standard vector operations.
 * Used throughout the physics simulation for positions, velocities, and forces.
 */
public final class Vector2D {

    public static final Vector2D ZERO = new Vector2D(0.0, 0.0);

    public final double x;
    public final double y;

    public Vector2D(double x, double y) {
        this.x = x;
        this.y = y;
    }

    /** Returns the vector sum of this and other. */
    public Vector2D add(Vector2D other) {
        return new Vector2D(this.x + other.x, this.y + other.y);
    }

    /** Returns the vector difference this - other. */
    public Vector2D sub(Vector2D other) {
        return new Vector2D(this.x - other.x, this.y - other.y);
    }

    /** Returns this vector scaled by scalar. */
    public Vector2D scale(double scalar) {
        return new Vector2D(this.x * scalar, this.y * scalar);
    }

    /** Returns the Euclidean magnitude of this vector. */
    public double magnitude() {
        return Math.sqrt(this.x * this.x + this.y * this.y);
    }

    /** Returns the squared magnitude (avoids sqrt; useful for comparisons). */
    public double magnitudeSq() {
        return this.x * this.x + this.y * this.y;
    }

    /**
     * Returns the unit vector in the same direction.
     * Returns ZERO if the magnitude is essentially zero.
     */
    public Vector2D normalize() {
        double mag = magnitude();
        if (mag < 1e-12) {
            return ZERO;
        }
        return new Vector2D(this.x / mag, this.y / mag);
    }

    /** Returns the dot product of this and other. */
    public double dot(Vector2D other) {
        return this.x * other.x + this.y * other.y;
    }

    /** Returns the 2D cross product magnitude (z-component of 3D cross product). */
    public double cross(Vector2D other) {
        return this.x * other.y - this.y * other.x;
    }

    /** Returns the Euclidean distance from this point to other. */
    public double distanceTo(Vector2D other) {
        return this.sub(other).magnitude();
    }

    /** Returns a new vector with x and y negated. */
    public Vector2D negate() {
        return new Vector2D(-this.x, -this.y);
    }

    /** Linear interpolation between this and other at parameter t in [0,1]. */
    public Vector2D lerp(Vector2D other, double t) {
        return new Vector2D(
            this.x + (other.x - this.x) * t,
            this.y + (other.y - this.y) * t
        );
    }

    @Override
    public String toString() {
        return String.format("Vector2D(%.4f, %.4f)", x, y);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Vector2D)) return false;
        Vector2D other = (Vector2D) obj;
        return Double.compare(this.x, other.x) == 0 && Double.compare(this.y, other.y) == 0;
    }

    @Override
    public int hashCode() {
        return 31 * Double.hashCode(x) + Double.hashCode(y);
    }
}

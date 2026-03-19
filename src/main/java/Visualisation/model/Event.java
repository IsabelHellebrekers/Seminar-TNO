package Visualisation.model;

/**
 * Represents a single simulation event that can be applied to a {@link SimState}.
 *
 * @author 621349it Ies Timmerarends
 * @author 612348ih Isabel Hellebrekers
 * @author 631426ls Lena Stiebing
 * @author 661267eb Eeke Bavelaar
 */
public interface Event {

    /**
     * Returns the simulation day on which this event occurs.
     *
     * @return the day index
     */
    int time();

    /**
     * Returns the type of this event.
     *
     * @return the event type
     */
    EventType type();

    /**
     * Returns a human-readable label for this event.
     *
     * @return event label string
     */
    String label();

    /**
     * Apply this event to the given simulation state, mutating it in place.
     *
     * @param state the simulation state to update
     */
    void apply(SimState state);
}

package Visualisation.model;

public interface Event {
    int time();
    EventType type();
    String label();
    void apply(SimState state);
}

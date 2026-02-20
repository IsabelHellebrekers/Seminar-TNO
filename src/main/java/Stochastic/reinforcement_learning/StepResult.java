package Stochastic.reinforcement_learning;

public final class StepResult {
    public final State nextState;
    public final double reward;
    public final boolean done;

    public StepResult(State nextState, double reward, boolean done) {
        this.nextState = nextState;
        this.reward = reward;
        this.done = done;
    }
}
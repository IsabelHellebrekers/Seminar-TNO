package Stochastic.reinforcement_learning;

public record StepResult(State nextState, double reward, boolean done) {
}
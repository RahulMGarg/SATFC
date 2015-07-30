package ca.ubc.cs.beta.stationpacking.solvers.termination;

/**
 * Created by newmanne on 29/07/15.
 */
public class NeverEndingTerminationCriterion implements ITerminationCriterion {

    @Override
    public double getRemainingTime() {
        return Double.MAX_VALUE;
    }

    @Override
    public boolean hasToStop() {
        return false;
    }

    @Override
    public void notifyEvent(double aTime) {

    }
}

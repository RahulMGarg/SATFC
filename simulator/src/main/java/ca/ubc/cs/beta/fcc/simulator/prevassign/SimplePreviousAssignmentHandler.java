package ca.ubc.cs.beta.fcc.simulator.prevassign;

import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.utils.StationPackingUtils;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Created by newmanne on 2016-09-30.
 */
public class SimplePreviousAssignmentHandler implements IPreviousAssignmentHandler {

    private Map<Integer, Integer> assignment;
    private final IConstraintManager constraintManager;

    /**
     * Construct a previous assignment handler.
     */
    public SimplePreviousAssignmentHandler(IConstraintManager constraintManager) {
        this.assignment = new HashMap<>();
        this.constraintManager = constraintManager;
    }

    @Override
    public Map<Integer, Integer> getPreviousAssignment() {
        return new HashMap<>(assignment);
    }

    @Override
    public Map<Integer, Integer> getPreviousAssignment(Map<Integer, Set<Integer>> domains) {
        final Map<Integer, Integer> returnedAssignment = new HashMap<>();
        final Set<Integer> commonStations = Sets.intersection(assignment.keySet(), domains.keySet());
        for (final int stationID : commonStations) {
            final int assignedChannel = assignment.get(stationID);
            if (domains.get(stationID).contains(assignedChannel)) {
                returnedAssignment.put(stationID, assignedChannel);
            }
        }
        return returnedAssignment;
    }

    /**
     * Merge the new assignment into what exists already
     */
    @Override
    public void updatePreviousAssignment(Map<Integer, Integer> newAssignment) {
        Preconditions.checkState(constraintManager.isSatisfyingAssignment(StationPackingUtils.channelToStationFromStationToChannel(newAssignment)), "Trying to merge UNSAT assignment into previous assignment! (%s)", newAssignment);
        final int oldSize = assignment.size();
        for (final Map.Entry<Integer, Integer> entry : newAssignment.entrySet()) {
            // Either change a station's previous value, or else add a new station
            assignment.put(entry.getKey(), entry.getValue());
        }
        Preconditions.checkState(assignment.size() >= oldSize, "Assignment shrunk from %s to %s stations", oldSize, assignment.size());
        Preconditions.checkState(constraintManager.isSatisfyingAssignment(StationPackingUtils.channelToStationFromStationToChannel(assignment)), "Updated previous assignment is not SAT!!! (%s)", assignment);
    }

}


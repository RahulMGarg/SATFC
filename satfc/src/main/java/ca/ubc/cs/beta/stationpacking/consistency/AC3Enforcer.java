package ca.ubc.cs.beta.stationpacking.consistency;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.tuple.Pair;
import org.jgrapht.alg.NeighborIndex;
import org.jgrapht.graph.DefaultEdge;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.solvers.componentgrouper.ConstraintGrouper;
import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;
import ca.ubc.cs.beta.stationpacking.solvers.termination.cputime.CPUTimeTerminationCriterion;

import com.google.common.collect.Sets;

/**
* Created by newmanne on 10/06/15.
*/
@Slf4j
public class AC3Enforcer {

    private final IConstraintManager constraintManager;

    public AC3Enforcer(IConstraintManager constraintManager) {
        this.constraintManager = constraintManager;
    }

    /**
     * Will fail at the first indication of inconsistency.
     *
     * @param instance
     * @return
     */
    public AC3Output AC3(StationPackingInstance instance, ITerminationCriterion criterion) {
        // TODO: use the termination criterion?
        final Map<Station, Set<Integer>> reducedDomains = new HashMap<>(instance.getDomains());
        final AC3Output output = new AC3Output(reducedDomains);
        final NeighborIndex<Station, DefaultEdge> neighborIndex = new NeighborIndex<>(ConstraintGrouper.getConstraintGraph(instance.getDomains(), constraintManager));
        final LinkedBlockingQueue<Pair<Station, Station>> workList = getInterferingStationPairs(neighborIndex, instance);
        while (!workList.isEmpty()) {
            final Pair<Station, Station> pair = workList.poll();
            if (removeInconsistentValues(pair, output)) {
                final Station referenceStation = pair.getLeft();
                if (reducedDomains.get(referenceStation).isEmpty()) {
                    log.debug("Reduced a domain to empty! Problem is solved UNSAT");
                    output.setNoSolution(true);
                    return output;
                } else {
                    reenqueueAllAffectedPairs(workList, pair, neighborIndex);
                }
            }
        }
        return output;
    }

    public AC3Output AC3(StationPackingInstance instance) {
        return AC3(instance, new CPUTimeTerminationCriterion(Double.MAX_VALUE));
    }

    private void reenqueueAllAffectedPairs(Queue<Pair<Station, Station>> interferingStationPairs,
                                           Pair<Station, Station> modifiedPair, NeighborIndex<Station, DefaultEdge> neighborIndex) {
        final Station x = modifiedPair.getLeft();
        final Station y = modifiedPair.getRight();

        neighborIndex.neighborsOf(x).stream().filter(neighbor -> !neighbor.equals(y)).forEach(neighbor -> {
            interferingStationPairs.add(Pair.of(neighbor, x));
        });
    }

    private LinkedBlockingQueue<Pair<Station, Station>> getInterferingStationPairs(NeighborIndex<Station, DefaultEdge> neighborIndex,
                                                                                   StationPackingInstance instance) {
        final LinkedBlockingQueue<Pair<Station, Station>> workList = new LinkedBlockingQueue<>();
        for (Station referenceStation : instance.getStations()) {
            for (Station neighborStation : neighborIndex.neighborsOf(referenceStation)) {
                workList.add(Pair.of(referenceStation, neighborStation));
            }
        }
        return workList;
    }

    private boolean removeInconsistentValues(Pair<Station, Station> pair, AC3Output output) {
        boolean change = false;
        final Map<Station, Set<Integer>> domains = output.getReducedDomains();
        final Station x = pair.getLeft();
        final Station y = pair.getRight();
        final List<Integer> xValuesToPurge = new ArrayList<>();
        for (int vx : domains.get(x)) {
            if (channelViolatesArcConsistency(x, vx, y, domains)) {
                log.debug("Purging channel {} from station {}'s domain", vx, x.getID());
                output.setNumReducedChannels(output.getNumReducedChannels() + 1);
                xValuesToPurge.add(vx);
                change = true;
            }
        }
        domains.get(x).removeAll(xValuesToPurge);
        return change;
    }

    private boolean channelViolatesArcConsistency(Station x, int vx, Station y, Map<Station, Set<Integer>> domains) {
        return domains.get(y).stream().noneMatch(vy -> isSatisfyingAssignment(x, vx, y, vy));
    }

    private boolean isSatisfyingAssignment(Station x, int vx, Station y, int vy) {
        final Map<Integer, Set<Station>> assignment = new HashMap<>();
        assignment.put(vx, Sets.newHashSet(x));
        assignment.putIfAbsent(vy, new HashSet<>());
        assignment.get(vy).add(y);
        return constraintManager.isSatisfyingAssignment(assignment);
    }
}
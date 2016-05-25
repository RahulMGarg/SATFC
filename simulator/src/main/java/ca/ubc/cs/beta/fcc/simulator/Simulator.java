package ca.ubc.cs.beta.fcc.simulator;

import ca.ubc.cs.beta.aeatk.misc.jcommander.JCommanderHelper;
import ca.ubc.cs.beta.fcc.simulator.parameters.SimulatorParameters;
import ca.ubc.cs.beta.fcc.simulator.participation.Participation;
import ca.ubc.cs.beta.fcc.simulator.participation.ParticipationRecord;
import ca.ubc.cs.beta.fcc.simulator.solver.IFeasibilitySolver;
import ca.ubc.cs.beta.fcc.simulator.solver.problem.IProblemGenerator;
import ca.ubc.cs.beta.fcc.simulator.state.IStateSaver;
import ca.ubc.cs.beta.fcc.simulator.station.CSVStationDB;
import ca.ubc.cs.beta.fcc.simulator.station.StationDB;
import ca.ubc.cs.beta.fcc.simulator.station.StationInfo;
import ca.ubc.cs.beta.fcc.simulator.utils.SimulatorUtils;
import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.datamanagers.stations.IStationManager;
import ca.ubc.cs.beta.stationpacking.execution.SimulatorProblemReader;
import ca.ubc.cs.beta.stationpacking.execution.SimulatorProblemReader.SATFCProblem;
import ca.ubc.cs.beta.stationpacking.facade.SATFCFacadeBuilder;
import ca.ubc.cs.beta.stationpacking.facade.SATFCResult;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class Simulator {

    private static Logger log;

    public static void main(String[] args) throws Exception {
        final SimulatorParameters parameters = new SimulatorParameters();
        JCommanderHelper.parseCheckingForHelpAndVersion(args, parameters);
        // TODO: probably want to override the default log file name...
        SATFCFacadeBuilder.initializeLogging(parameters.getFacadeParameters().getLogLevel(), parameters.getFacadeParameters().logFileName);
        JCommanderHelper.logCallString(args, Simulator.class);
        log = LoggerFactory.getLogger(Simulator.class);

        parameters.setUp();

        log.info("Building solver");
        final IFeasibilitySolver solver = parameters.createSolver();

        log.info("Reading station info from file");
        final StationDB stationDB = new CSVStationDB(parameters.getInfoFile());

        final IStateSaver stateSaver = parameters.getStateSaver();

        final Prices prices;
        final ParticipationRecord participation;
        int round;
        Map<Integer, Integer> assignment;
        if (parameters.isRestore()) {
            log.info("Restoring from state");
            final IStateSaver.AuctionState auctionState = stateSaver.restoreState(stationDB);
            prices = auctionState.getPrices();
            participation = auctionState.getParticipation();
            round = auctionState.getRound() + 1;
            assignment = auctionState.getAssignment();
        } else {
            // Initialize opening prices
            log.info("Setting opening prices");
            prices = new OpeningPrices(stationDB, parameters.getBaseClockPrice());
            log.info("Figuring out participation");
            // Figure out participation
            participation = new ParticipationRecord(stationDB, parameters.getParticipationDecider(prices));
            round = 1;
            log.info("Finding an initial assignment for the non-participating stations");
            final SATFCResult initialFeasibility = solver.getFeasibilityBlocking(participation.getOnAirStations(), ImmutableMap.of());
            Preconditions.checkState(SimulatorUtils.isFeasible(initialFeasibility), "Initial non-participating stations do not have a feasible assignment! (Result was %s)", initialFeasibility.getResult());
            log.info("Found an initial assignment for the non-participating stations");
            assignment = initialFeasibility.getWitnessAssignment();
        }

        log.info("There are {} / {} stations participating", participation.getActiveStations().size(), stationDB.getStations().size());
        // Consider stations in reverse order of their values per volume
        final Comparator<StationInfo> valuePerVolumeComparator = Comparator.comparingDouble(a -> a.getValue() / a.getVolume());
        final List<StationInfo> activeStationsOrdered = Collections.synchronizedList(participation.getActiveStations().stream().sorted(valuePerVolumeComparator.reversed()).collect(Collectors.toList()));
        final Set<StationInfo> onAirStations = participation.getOnAirStations();

        while (!participation.getActiveStations().isEmpty()) {
            log.info("It is round {}", round);

            final StationInfo nextToExit = activeStationsOrdered.remove(0);
            log.info("Considering station {}", nextToExit);
            Set<StationInfo> toPack = Sets.union(onAirStations, ImmutableSet.of(nextToExit));
            final SATFCResult feasibility = solver.getFeasibilityBlocking(toPack, assignment);
            log.info("Result of considering station {} was {}", nextToExit, feasibility.getResult());
            if (SimulatorUtils.isFeasible(feasibility)) {
                log.info("Updating assignment and participation to reflect station exiting");
                assignment = feasibility.getWitnessAssignment();
                participation.setParticipation(nextToExit, Participation.EXITED);
                onAirStations.add(nextToExit);
                prices.setPrice(nextToExit, nextToExit.getValue());
                // value = volume * baseClock * gamma^n
                // so value / volume = baseClock * gamma^n same for everyone
                final double clockGammaN = nextToExit.getValue() / nextToExit.getVolume();
                log.info("Clock Gamma N is {}", clockGammaN);
                // Update prices for remaining stations - can do this in parallel
                log.info("Considering other stations to see if they froze / update their prices");
                int nProblemsToSubmit = 0;
                final Set<StationInfo> newlyFrozen = new HashSet<>();
                for (final StationInfo q : activeStationsOrdered) {
                    final Set<StationInfo> toPack2 = new HashSet<>(toPack);
                    toPack2.add(q);
                    solver.getFeasibility(toPack2, assignment, (problem, result) -> {
                        if (SimulatorUtils.isFeasible(result)) {
                            double prevPrice = prices.getPrice(q);
                            double newPrice = q.getVolume() * clockGammaN;
                            Preconditions.checkState(newPrice <= prevPrice, "Price must be decreasing! %s %s -> %s", q, prevPrice, newPrice);
                            prices.setPrice(q, newPrice);
                            log.trace("Updating price for Station {} from {} to {}", q, prevPrice, newPrice);
                        } else {
                            newlyFrozen.add(q);
                            participation.setParticipation(q, Participation.FROZEN);
                            log.info("Station {} is now frozen", q);
                        }
                    });
                    nProblemsToSubmit += 1;
                }
                log.info("Waiting for the {} submitted problems to finish", nProblemsToSubmit);
                solver.waitForAllSubmitted();
                activeStationsOrdered.removeAll(newlyFrozen);
            } else {
                participation.setParticipation(nextToExit, Participation.FROZEN);
            }
            log.info("Saving state for round {}", round);
            stateSaver.saveState(stationDB, prices, participation, assignment, round);
            round++;
        }

        solver.close();
        log.info("Finished simulation");
    }

    public interface ISATFCProblemSpecGenerator {

        default SimulatorProblemReader.SATFCProblemSpecification createProblem(Set<StationInfo> stations) {
            return createProblem(stations, ImmutableMap.of());
        }

        SimulatorProblemReader.SATFCProblemSpecification createProblem(Set<StationInfo> stations, Map<Integer, Integer> previousAssignment);

    }

    public static class OpeningPrices extends PricesImpl {

        public OpeningPrices(StationDB stationDB, double baseClockPrice) {
            super();
            for (final StationInfo s : stationDB.getStations()) {
                final double openPrice = baseClockPrice * s.getVolume();
                setPrice(s, openPrice);
            }
        }

    }

    public interface Prices {

        void setPrice(StationInfo station, Double price);
        double getPrice(StationInfo station);

    }

    public static class PricesImpl implements Prices {

        public PricesImpl() {
            prices = new ConcurrentHashMap<>();
        }

        private final Map<StationInfo, Double> prices;

        public void setPrice(StationInfo station, Double price) {
            prices.put(station, price);
        }

        public double getPrice(StationInfo stationID) {
            return prices.get(stationID);
        }

    }

}
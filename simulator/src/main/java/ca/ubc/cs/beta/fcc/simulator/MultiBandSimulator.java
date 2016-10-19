package ca.ubc.cs.beta.fcc.simulator;

import ca.ubc.cs.beta.fcc.simulator.bidprocessing.Bid;
import ca.ubc.cs.beta.fcc.simulator.bidprocessing.IStationOrderer;
import ca.ubc.cs.beta.fcc.simulator.bidprocessing.StationOrdererImpl;
import ca.ubc.cs.beta.fcc.simulator.feasibilityholder.IFeasibilityStateHolder;
import ca.ubc.cs.beta.fcc.simulator.ladder.IModifiableLadder;
import ca.ubc.cs.beta.fcc.simulator.parameters.LadderAuctionParameters;
import ca.ubc.cs.beta.fcc.simulator.parameters.MultiBandSimulatorParameter;
import ca.ubc.cs.beta.fcc.simulator.participation.Participation;
import ca.ubc.cs.beta.fcc.simulator.participation.ParticipationRecord;
import ca.ubc.cs.beta.fcc.simulator.prevassign.IPreviousAssignmentHandler;
import ca.ubc.cs.beta.fcc.simulator.prices.IPrices;
import ca.ubc.cs.beta.fcc.simulator.prices.IPricesFactory;
import ca.ubc.cs.beta.fcc.simulator.solver.IFeasibilitySolver;
import ca.ubc.cs.beta.fcc.simulator.solver.callback.SATFCCallback;
import ca.ubc.cs.beta.fcc.simulator.state.LadderAuctionState;
import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
import ca.ubc.cs.beta.fcc.simulator.stepreductions.StepReductionCoefficientCalculator;
import ca.ubc.cs.beta.fcc.simulator.unconstrained.ISimulatorUnconstrainedChecker;
import ca.ubc.cs.beta.fcc.simulator.utils.Band;
import ca.ubc.cs.beta.fcc.simulator.utils.SimulatorUtils;
import ca.ubc.cs.beta.fcc.simulator.vacancy.IVacancyCalculator;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.datamanagers.stations.IStationManager;
import ca.ubc.cs.beta.stationpacking.execution.SimulatorProblemReader;
import ca.ubc.cs.beta.stationpacking.facade.SATFCResult;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.utils.StationPackingUtils;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Sets;
import humanize.Humanize;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static java.lang.Math.max;
import static java.lang.Math.min;

/**
 * Created by newmanne on 2016-08-02.
 */
@Slf4j
public class MultiBandSimulator {

    private final IVacancyCalculator vacancyCalculator;
    private final IFeasibilityStateHolder problemMaker;
    private final IFeasibilitySolver solver;

    private final StepReductionCoefficientCalculator stepReductionCoefficientCalculator;

    private final IStationOrderer stationOrderer;
    private final IPricesFactory pricesFactory;

    private final IPreviousAssignmentHandler previousAssignmentHandler;

    private final ISimulatorUnconstrainedChecker unconstrainedChecker;

    private final LadderAuctionParameters parameters;
    private final IStationManager stationManager;
    private final IConstraintManager constraintManager;

    public MultiBandSimulator(MultiBandSimulatorParameter parameters) {
        this.parameters = parameters.getParameters();
        this.problemMaker = parameters.getProblemMaker();
        this.pricesFactory = parameters.getPricesFactory();
        this.solver = parameters.getSolver();
        this.vacancyCalculator = parameters.getVacancyCalculator();
        this.unconstrainedChecker = parameters.getUnconstrainedChecker();
        stepReductionCoefficientCalculator = new StepReductionCoefficientCalculator(this.parameters.getOpeningBenchmarkPrices());
        this.previousAssignmentHandler = parameters.getPreviousAssignmentHandler();
        this.stationOrderer = new StationOrdererImpl();
        this.stationManager = parameters.getStationManager();
        this.constraintManager = parameters.getConstraintManager();
        // TODO: calculate the interference compononent manually? (i.e. manual volume calculation)
    }


    public LadderAuctionState executeRound(LadderAuctionState previousState) {
        final int round = previousState.getRound() + 1;
        log.info("Starting round {}", round);

        final Map<IStationInfo, Double> stationPrices = new HashMap<>(previousState.getPrices());

        final double oldBaseClockPrice = previousState.getBaseClockPrice();

        final IPrices oldBenchmarkPrices = previousState.getBenchmarkPrices();
        final IPrices newBenchmarkPrices = pricesFactory.create();
        final IPrices actualPrices = pricesFactory.create();

        // TODO: Why not make these fields? Seems like you just reuse them anyways and could make method calls cleaner
        final IModifiableLadder ladder = previousState.getLadder();
        final ParticipationRecord participation = previousState.getParticipation();

        Preconditions.checkState(StationPackingUtils.weakVerify(stationManager, constraintManager, previousState.getAssignment()), "Round started on an invalid assignment!", previousState.getAssignment());
        previousAssignmentHandler.updatePreviousAssignment(previousState.getAssignment());

        log.info("Computing vacancies...");
        final ImmutableTable<IStationInfo, Band, Double> vacancies = vacancyCalculator.computeVacancies(participation.getMatching(Participation.ACTIVE), ladder, previousAssignmentHandler.getPreviousAssignment(), previousState.getBenchmarkPrices());

        log.info("Calculating reduction coefficients...");
        final ImmutableTable<IStationInfo, Band, Double> reductionCoefficients = stepReductionCoefficientCalculator.computeStepReductionCoefficients(vacancies);

        log.info("Calculating new benchmark prices...");
        // Either 5% of previous value or 1% of starting value
        final double decrement = Math.max(parameters.getR1() * oldBaseClockPrice, parameters.getR2() * parameters.getOpeningBenchmarkPrices().get(Band.OFF));
        log.debug("Decrement this round is {}", decrement);
        // This is the benchmark price for a UHF station to go off air
        final double newBaseClockPrice = oldBaseClockPrice - decrement;

        for (IStationInfo station : participation.getMatching(Participation.ACTIVE)) {
            for (Band band : station.getHomeBand().getBandsBelowInclusive()) {
                // If this station were a "comparable" UHF station, the prices for all of the moves...
                final double benchmarkValue = oldBenchmarkPrices.getPrice(station, band) - reductionCoefficients.get(station, band) * decrement;
                newBenchmarkPrices.setPrice(station, band, benchmarkValue);
            }
            for (Band band : ladder.getPossibleMoves(station)) {
                actualPrices.setPrice(station, band, benchmarkToActualPrice(station, band, newBenchmarkPrices.getPrices(station, station.getHomeBand().getBandsBelowInclusive())));
            }
        }

        log.info("Collecting bids");
        // Collect bids from bidding stations
        final Set<IStationInfo> biddingStations = participation.getMatching(Participation.BIDDING);
        final Map<IStationInfo, Bid> stationToBid = new HashMap<>();
        for (IStationInfo station : biddingStations) {
            log.debug("Asking station {} to submit a bid", station);
            final Map<Band, Double> offers = actualPrices.getOffers(station);
            log.debug("Prices are {}:", offers);
            Preconditions.checkState(offers.get(station.getHomeBand()) == 0, "Station %s is being offered money %s to go to its home band!", station, offers.get(station.getHomeBand()));
            final Bid bid = station.queryPreferredBand(offers, ladder.getStationBand(station));
            validateBid(ladder, station, bid);
            stationToBid.put(station, bid);
            log.debug("Bid: {}", bid);
        }

        log.info("Processing bids");
        final ImmutableList<IStationInfo> stationsToQueryOrdering = stationOrderer.getQueryOrder(participation.getMatching(Participation.BIDDING), actualPrices, ladder, previousState.getPrices());
        final List<IStationInfo>  stationsToQuery = new ArrayList<>(stationsToQueryOrdering);
        boolean finished = false;
        while (!finished) {
            finished = true;
            for (int i = 0; i < stationsToQuery.size(); i++) {
                // Find the first station proved to be feasible in its pre-auction band
                final IStationInfo station = stationsToQuery.get(i);
                final Band homeBand = station.getHomeBand();
                log.debug("Checking if {} is feasible on its home band of {}", station, homeBand);
                final String problemName = Joiner.on('_').join("R" + round, IFeasibilityStateHolder.BID_PROCESSING_HOME_BAND_FEASIBILITY, station.getId(), station.getHomeBand());
                final SATFCResult homeBandFeasibility = solver.getFeasibilityBlocking(problemMaker.makeProblem(station, homeBand, problemName));
                final boolean isFeasibleInHomeBand = SimulatorUtils.isFeasible(homeBandFeasibility);
                log.debug("{}", homeBandFeasibility.getResult());
                if (isFeasibleInHomeBand) {
                    finished = false;
                    // Retrieve the bid
                    final Bid bid = stationToBid.get(station);
                    log.debug("Processing {} bid of {}", station, bid);
                    boolean resortToFallbackBid = false;
                    SATFCResult moveFeasibility = null;
                    if (!Bid.isSafe(bid.getPreferredOption(), ladder.getStationBand(station), station.getHomeBand())) {
                        log.debug("Bid to move bands (without dropping out) - Need to test move feasibility");
                        final String moveProblemName = Joiner.on('_').join("R" + round, IFeasibilityStateHolder.BID_PROCESSING_MOVE_FEASIBILITY, station.getId(), bid.getPreferredOption());
                        moveFeasibility = solver.getFeasibilityBlocking(problemMaker.makeProblem(station, bid.getPreferredOption(), moveProblemName));
                        resortToFallbackBid = !SimulatorUtils.isFeasible(moveFeasibility);
                    }
                    if (resortToFallbackBid) {
                        log.debug("Not feasible in preferred option. Using fallback option");
                    }
                    final Band moveBand = resortToFallbackBid ? bid.getFallbackOption() : bid.getPreferredOption();
                    if (moveBand.equals(station.getHomeBand())) {
                        log.info("Station {} rejecting offer of {} and moving to exit (value in HB {})", station, Humanize.spellBigNumber(actualPrices.getPrice(station, ladder.getStationBand(station))), Humanize.spellBigNumber(station.getValue(station.getHomeBand())));
                        exitStation(station, Participation.EXITED_VOLUNTARILY, homeBandFeasibility.getWitnessAssignment(), participation, ladder, stationPrices);
                    } else {
                        // If an actual move is taking place
                        if (!ladder.getStationBand(station).equals(moveBand)) {
                            ladder.moveStation(station, moveBand);
                            Preconditions.checkNotNull(moveFeasibility);
                            previousAssignmentHandler.updatePreviousAssignment(moveFeasibility.getWitnessAssignment());
                        }
                        stationPrices.put(station, actualPrices.getPrice(station, moveBand));
                    }
                    stationsToQuery.remove(i);
                    break; // start a new processing loop
                }
            }
        }

        // BID STATUS UPDATING
        // For every active station, check whether the station is feasible in its pre-auction band
        final Map<IStationInfo, SATFCResult> stationToFeasibleInHomeBand = new ConcurrentHashMap<>();
        for (IStationInfo station : stationsToQuery) {
            // If you are still in this queue, you are frozen
            stationToFeasibleInHomeBand.put(station, new SATFCResult(SATResult.UNSAT, 0., 0., ImmutableMap.of()));
        }
        for (final IStationInfo stationInfo : participation.getActiveStations()) {
            if (!stationToFeasibleInHomeBand.containsKey(stationInfo)) {
                final String problemName = Joiner.on('_').join("R" + round, IFeasibilityStateHolder.BID_STATUS_UPDATING_HOME_BAND_FEASIBILITY, stationInfo.getId(), stationInfo.getHomeBand());
                solver.getFeasibility(problemMaker.makeProblem(stationInfo, stationInfo.getHomeBand(), problemName), new SATFCCallback() {
                    @Override
                    public void onSuccess(SimulatorProblemReader.SATFCProblemSpecification problem, SATFCResult result) {
                        stationToFeasibleInHomeBand.put(stationInfo, result);
                    }
                });
            }
        }
        solver.waitForAllSubmitted();

        log.info("Checking for provisional winners");
        final Map<Band, List<Map.Entry<IStationInfo, SATFCResult>>> bandListMap = stationToFeasibleInHomeBand.entrySet().stream().collect(Collectors.groupingBy(entry -> entry.getKey().getHomeBand()));
        // Do this in descending band order because this will catch the most provisional winners the earliest. E.g. flagging a UHF station as PW means it participates in VHF problems.
        // We can solve each "home band" in parallel
        for (final Band band : bandListMap.keySet().stream().sorted(Comparator.reverseOrder()).collect(Collectors.toList())) {
            final List<Map.Entry<IStationInfo, SATFCResult>> bandList = bandListMap.get(band);
            for (Map.Entry<IStationInfo, SATFCResult> entry : bandList) {
                final IStationInfo station = entry.getKey();
                boolean feasibleInHomeBand = SimulatorUtils.isFeasible(entry.getValue());
                final Participation bidStatus = participation.getParticipation(station);
                if (!feasibleInHomeBand && !bidStatus.equals(Participation.FROZEN_PROVISIONALLY_WINNING)) {
                    if (station.getHomeBand().equals(Band.UHF)) {
                        makeProvisionalWinner(participation, station, stationPrices.get(station));
                    } else {
                        // Need to do a provisional winner check
                        // Provisionally winning if cannot assign s, all exited in s's home band, and all provisionally winning with home bands above currently assigned to s's home band
                        final Set<IStationInfo> provisionalWinnerProblemStationSet = Sets.newHashSet(station);
                        provisionalWinnerProblemStationSet.addAll(
                                participation.getMatching(Participation.INACTIVE).stream()
                                        .filter(s -> ladder.getStationBand(s).equals(station.getHomeBand()))
                                        .collect(Collectors.toSet())
                        );
                        final String problemName = Joiner.on('_').join("R" + round, IFeasibilityStateHolder.PROVISIONAL_WINNER_CHECK, station.getId(), station.getHomeBand());
                        solver.getFeasibility(problemMaker.makeProblem(provisionalWinnerProblemStationSet, station.getHomeBand(), problemName), new SATFCCallback() {
                            @Override
                            public void onSuccess(SimulatorProblemReader.SATFCProblemSpecification problem, SATFCResult result) {
                                if (!SimulatorUtils.isFeasible(result)) {
                                    makeProvisionalWinner(participation, station, stationPrices.get(station));
                                }
                            }
                        });
                    }
                }
            }
            solver.waitForAllSubmitted();
        }

        // TODO: Does the order in which I perform these exited station checks matter? Ask!
        log.info("Checking for unconstrained stations");
        for (final Band band : bandListMap.keySet().stream().sorted(Comparator.reverseOrder()).collect(Collectors.toList())) {
            final List<Map.Entry<IStationInfo, SATFCResult>> bandList = bandListMap.get(band);
            for (Map.Entry<IStationInfo, SATFCResult> entry : bandList) {
                final IStationInfo station = entry.getKey();
                boolean feasibleInHomeBand = SimulatorUtils.isFeasible(entry.getValue());
                if (feasibleInHomeBand) {
                    // for every active station feasible in home band check if is not needed
                    if (unconstrainedChecker.isUnconstrained(station, ladder)) {
                        exitStation(station, Participation.EXITED_NOT_NEEDED, stationToFeasibleInHomeBand.get(station).getWitnessAssignment(), participation, ladder, stationPrices);
                    }
                }
                if (participation.isActive(station)) {
                    participation.setParticipation(station, feasibleInHomeBand ? Participation.BIDDING : Participation.FROZEN_CURRENTLY_INFEASIBLE);
                }
            }
        }

        log.info("Round {} complete", round);

        return LadderAuctionState.builder()
                .benchmarkPrices(newBenchmarkPrices)
                .participation(participation)
                .round(round)
                .assignment(previousAssignmentHandler.getPreviousAssignment())
                .ladder(ladder)
                .prices(stationPrices)
                .baseClockPrice(newBaseClockPrice)
                .vacancies(vacancies)
                .reductionCoefficients(reductionCoefficients)
                .offers(actualPrices)
                .bidProcessingOrder(stationsToQueryOrdering)
                .build();
    }

    private double benchmarkToActualPrice(IStationInfo station, Band band, Map<Band, Double> benchmarkPrices) {
        final double benchmarkHome = benchmarkPrices.get(station.getHomeBand());
        // Second arg to min is about splitting the cost of a UHF station going to your spot and you going elsewhere
        final double nonVolumeWeightedActual = max(0, min(benchmarkPrices.get(Band.OFF), benchmarkPrices.get(band) - benchmarkHome));
        // Price offers are rounded down to nearest integer
        return Math.floor(station.getVolume() * nonVolumeWeightedActual);
    }

    private void exitStation(IStationInfo station, Participation exitStatus, Map<Integer, Integer> newAssignment, ParticipationRecord participation, IModifiableLadder ladder, Map<IStationInfo, Double> stationPrices) {
        Preconditions.checkState(Participation.EXITED.contains(exitStatus), "Must be an exit Participation");
        log.info("Station {} (currently on band {}) is exiting, {}", station, ladder.getStationBand(station), exitStatus);
        participation.setParticipation(station, exitStatus);
        ladder.moveStation(station, station.getHomeBand());
        stationPrices.put(station, 0.0);
        previousAssignmentHandler.updatePreviousAssignment(newAssignment);
    }

    private void makeProvisionalWinner(ParticipationRecord participation, IStationInfo station, double price) {
        participation.setParticipation(station, Participation.FROZEN_PROVISIONALLY_WINNING);
        log.info("Station {} is now a provisional winner with a price of {}", station, Humanize.spellBigNumber(price));
    }

    // Just some sanity checks on bids
    private void validateBid(IModifiableLadder ladder, IStationInfo station, Bid bid) {
        // If the preferred option is neither its currently held option nor to drop out of the auction
        if (!Bid.isSafe(bid.getPreferredOption(), ladder.getStationBand(station), station.getHomeBand())) {
            Preconditions.checkNotNull(bid.getFallbackOption(), "Fallback option was required, but not specified!");
            Preconditions.checkState(bid.getFallbackOption().equals(ladder.getStationBand(station)) || bid.getFallbackOption().equals(station.getHomeBand()), "Must either fallback to currently held option or exit");
        }
    }

}
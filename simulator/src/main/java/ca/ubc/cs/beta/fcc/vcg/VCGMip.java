package ca.ubc.cs.beta.fcc.vcg;

import ca.ubc.cs.beta.aeatk.misc.jcommander.JCommanderHelper;
import ca.ubc.cs.beta.aeatk.misc.options.UsageTextField;
import ca.ubc.cs.beta.aeatk.options.AbstractOptions;
import ca.ubc.cs.beta.fcc.simulator.parameters.SimulatorParameters;
import ca.ubc.cs.beta.fcc.simulator.station.StationDB;
import ca.ubc.cs.beta.matroid.encoder.MaxSatEncoder;
import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.Constraint;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.datamanagers.stations.IStationManager;
import ca.ubc.cs.beta.stationpacking.execution.extendedcache.IStationDB;
import ca.ubc.cs.beta.stationpacking.facade.SATFCFacadeBuilder;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.utils.JSONUtils;
import ca.ubc.cs.beta.stationpacking.utils.LoggingOutputStream;
import ca.ubc.cs.beta.stationpacking.utils.StationPackingUtils;
import ca.ubc.cs.beta.stationpacking.utils.Watch;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParametersDelegate;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import ilog.concert.*;
import ilog.cplex.IloCplex;
import lombok.Data;
import lombok.experimental.Builder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.math.util.MathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static ca.ubc.cs.beta.stationpacking.utils.GuavaCollectors.toImmutableList;

/**
 * Created by newmanne on 2016-05-19.
 */
public class VCGMip {

    private static Logger log;

    @UsageTextField(title = "VCG Parameters", description = "VCG Parameters")
    public static class VCGParameters extends AbstractOptions {

        @ParametersDelegate
        private SimulatorParameters simulatorParameters = new SimulatorParameters();

        @Parameter(names = "-VCG-PACKING")
        List<Integer> ids = new ArrayList<>();

        @Parameter(names = "-NOT-PARTICIPATING")
        List<Integer> notParticipating = new ArrayList<>();

        @Parameter(names = "-O", required = true)
        String outputFile;

        @Parameter(names = "-MIP-TYPE")
        MIPType mipType = MIPType.VCG;

    }

    enum MIPType {
        VCG,
        SMALLEST_MAXIMAL
    }

    public static void main(String[] args) throws IloException, IOException {
        final VCGParameters q = new VCGParameters();
        JCommanderHelper.parseCheckingForHelpAndVersion(args, q);
        // TODO: probably want to override the default name...
        final SimulatorParameters parameters = q.simulatorParameters;
        SATFCFacadeBuilder.initializeLogging(parameters.getFacadeParameters().getLogLevel(), parameters.getFacadeParameters().logFileName);
        JCommanderHelper.logCallString(args, VCGMip.class);
        log = LoggerFactory.getLogger(VCGMip.class);

        parameters.setUp();

        final StationDB stationDB = parameters.getStationDB();
        final Set<Integer> use = new HashSet<>(q.ids);
        final Map<Integer, Set<Integer>> domains = parameters.getProblemGenerator().createProblem(use).getDomains();
        log.info("Looking at {} stations", domains.size());
        log.info("Using max channel {}", parameters.getMaxChannel());
        final IConstraintManager constraintManager = parameters.getConstraintManager();
        final IMIPEncoder encoder;
        if (q.mipType.equals(MIPType.VCG)) {
            encoder = new VCGMIPMaker();
        } else if (q.mipType.equals(MIPType.SMALLEST_MAXIMAL)) {
            encoder = new SmallestMaximalCardinalityMIPMaker();
        } else {
            throw new IllegalStateException();
        }
        final MIPMaker mipMaker = new MIPMaker(stationDB, parameters.getStationManager(), constraintManager, encoder);
        final MIPResult mipResult = mipMaker.solve(domains, new HashSet<>(q.notParticipating), parameters.getCutoff(), (int) parameters.getSeed());

        if (q.mipType.equals(MIPType.VCG)) {
            final double computedValue = mipResult.getAssignment().keySet().stream()
                    .filter(s -> !q.notParticipating.contains(s))
                    .mapToDouble(s -> stationDB.getStationById(s).getValue())
                    .sum();
            final double obj = mipResult.getObjectiveValue();
            if (computedValue != obj) {
                log.warn("Computed {} but obj was {} difference of {}", computedValue, obj, Math.abs(computedValue - obj));
            } else {
                log.info("Assignment matches up with objective value as expected");
            }
        }

        final String result = JSONUtils.toString(mipResult);
        FileUtils.writeStringToFile(new File(q.outputFile), result);
    }

    @Data
    @Builder
    public static class MIPResult {
        private double objectiveValue;
        private Map<Integer, Integer> assignment;
        @JsonSerialize(using = ToStringSerializer.class)
        private IloCplex.Status status;
        private Set<Integer> stations;
        private Set<Integer> notParticipating;
        private double walltime;
        private double cputime;
    }

    @Data
    public static class StationChannel {
        private final int station;
        private final int channel;
    }

    // TODO: check pairwise, might speed things up
    @Slf4j
    public static class SmallestMaximalCardinalityMIPMaker implements IMIPEncoder {

        @Override
        public void encode(Map<Integer, Set<Integer>> domains, Set<Integer> participating, Set<Integer> nonParticipating, StationDB stationDB, Table<Integer, Integer, IloIntVar> varLookup, IConstraintManager constraintManager, IloCplex cplex) throws IloException {
            // Objective
            final IloLinearIntExpr objectiveSum = cplex.linearIntExpr();
            for (final Integer station : participating) {
                final IloIntVar[] domainVars = varLookup.row(station).values().stream().toArray(IloIntVar[]::new);
                if (!nonParticipating.contains(station)) {
                    final int[] values = new int[domainVars.length];
                    Arrays.fill(values, 1);
                    objectiveSum.addTerms(values, domainVars);
                }
            }
            cplex.addMinimize(objectiveSum);

            // Greedy clauses
            final Map<Station, Set<Integer>> stationDomains = domains.entrySet().stream().collect(Collectors.toMap(e -> new Station(e.getKey()), Map.Entry::getValue));
            for (final Integer station : participating) {
                // Create the sum
                final IloLinearIntExpr domainSum = cplex.linearIntExpr();
                final IloIntVar[] domainVars = varLookup.row(station).values().stream().toArray(IloIntVar[]::new);
                final int[] values = new int[domainVars.length];
                Arrays.fill(values, 1);
                domainSum.addTerms(values, domainVars);

                for (int channel : stationDB.getStationById(station).getDomain()) {
                    final IloLinearIntExpr channelSpecificSum = cplex.linearIntExpr();
                    channelSpecificSum.add(domainSum);
                    for (StationChannel sc : MaxSatEncoder.getConstraintsForChannel(constraintManager, new Station(station), channel, stationDomains)) {
                        final IloIntVar interferingVar = varLookup.get(sc.getStation(), sc.getChannel());
                        channelSpecificSum.addTerm(1, interferingVar);
                    }
                    cplex.addGe(channelSpecificSum, 1e-6);
                }
            }
        }
    }

    @Slf4j
    public static class VCGMIPMaker implements IMIPEncoder {

        @Override
        public void encode(Map<Integer, Set<Integer>> domains, Set<Integer> participating, Set<Integer> nonParticipating, StationDB stationDB, Table<Integer, Integer, IloIntVar> varLookup, IConstraintManager constraintManager, IloCplex cplex) throws IloException {
            // Objective function
            final IloLinearNumExpr objectiveSum = cplex.linearNumExpr();
            for (final Integer station : participating) {
                final IloIntVar[] domainVars = varLookup.row(station).values().stream().toArray(IloIntVar[]::new);
                final double value = stationDB.getStationById(station).getValue();
                final double[] values = new double[domainVars.length];
                Arrays.fill(values, value);
                objectiveSum.addTerms(values, domainVars);
            }
            cplex.addMaximize(objectiveSum);
        }
    }

    public interface IMIPEncoder {

        void encode(Map<Integer, Set<Integer>> domains, Set<Integer> participating, Set<Integer> nonParticipating, StationDB stationDB, Table<Integer, Integer, IloIntVar> varLookup, IConstraintManager constraintManager, IloCplex cplex) throws IloException;

    }

    @Slf4j
    // Not thread safe. Should be reusuable.
    public static class MIPMaker {

        protected final StationDB stationDB;
        protected final IStationManager stationManager;
        protected final IConstraintManager constraintManager;
        private final IMIPEncoder encoder;
        protected IloCplex cplex;

        // Station, Channel -> Var
        protected Table<Integer, Integer, IloIntVar> varLookup;
        protected Map<IloIntVar, StationChannel> variablesDecoder;

        public MIPMaker(StationDB stationDB, IStationManager stationManager, IConstraintManager constraintManager, IMIPEncoder encoder) throws IloException {
            this.stationDB = stationDB;
            this.stationManager = stationManager;
            this.constraintManager = constraintManager;
            this.encoder = encoder;
        }

        public MIPResult solve(Map<Integer, Set<Integer>> domains, double cutoff, long seed) throws IloException {
            return solve(domains, ImmutableSet.of(), cutoff, seed);
        }

        public MIPResult solve(Map<Integer, Set<Integer>> domains, Set<Integer> nonParticipating, double cutoff, long seed) throws IloException {
            this.varLookup = HashBasedTable.create();
            this.variablesDecoder = new HashMap<>();
            this.cplex = new IloCplex();

            Watch watch = Watch.constructAutoStartWatch();

            final Set<Integer> participating = Sets.difference(domains.keySet(), nonParticipating);

            // Set up the x_{s,c} variables
            for (final Map.Entry<Integer, Set<Integer>> domainsEntry : domains.entrySet()) {
                final int station = domainsEntry.getKey();
                final List<Integer> domainList = domainsEntry.getValue().stream().sorted().collect(toImmutableList());
                final IloIntVar[] domainVars = cplex.boolVarArray(domainList.size());
                log.info("{}", station);
                for (int i = 0; i < domainList.size(); i++) {
                    final int channel = domainList.get(i);
                    final IloIntVar var = domainVars[i];
                    var.setName(Integer.toString(station) + ":" + Integer.toString(channel));
                    varLookup.put(station, channel, var);
                    variablesDecoder.put(var, new StationChannel(station, channel));
                }
            }

            // Non participating stations get exactly 1 channel, participating get 0 or 1
            for (final Integer station : domains.keySet()) {
                final IloIntVar[] domainVars = varLookup.row(station).values().stream().toArray(IloIntVar[]::new);
                if (nonParticipating.contains(station)) {
                    // Must be placed on air
                    cplex.addEq(cplex.sum(domainVars), 1);
                } else {
                    // Optionally go on at most 1 channel
                    cplex.addLe(cplex.sum(domainVars), 1);
                }
            }

            // Add the interference constraints
            int nInterference = 0;
            for (Constraint constraint : constraintManager.getAllRelevantConstraints(domains.entrySet().stream().collect(Collectors.toMap(e -> new Station(e.getKey()), Map.Entry::getValue)))) {
                final IloIntVar var1 = varLookup.get(constraint.getSource().getID(), constraint.getSourceChannel());
                final IloIntVar var2 = varLookup.get(constraint.getTarget().getID(), constraint.getTargetChannel());
                cplex.addLe(cplex.sum(var1, var2), 1);
                nInterference++;
            }
            log.info("Added {} interference constraints", nInterference);

            // Do the rest of the encoding!
            encoder.encode(domains, participating, nonParticipating, stationDB, varLookup, constraintManager, cplex);

            log.info("Encoding MIP took {} s.", watch.getElapsedTime());
            log.info("MIP has {} variables.", cplex.getNcols());
            log.info("MIP has {} constraints.", cplex.getNrows());

            // This turns off CPLEX logging.
            cplex.setOut(new LoggingOutputStream(LoggerFactory.getLogger("CPLEX"), LoggingOutputStream.LogLevel.INFO));

            //Set CPLEX's parameters.
            try {
                cplex.setParam(IloCplex.DoubleParam.TimeLimit, cutoff);
                cplex.setParam(IloCplex.LongParam.RandomSeed, (int) seed);
                cplex.setParam(IloCplex.IntParam.MIPEmphasis, IloCplex.MIPEmphasis.Optimality);
                cplex.setParam(IloCplex.Param.MIP.Tolerances.MIPGap, 0);
                cplex.setParam(IloCplex.Param.ClockType, 1); // CPU Time
            } catch (IloException e) {
                log.error("Could not set CPLEX's parameters to the desired values", e);
                throw new IllegalStateException("Could not set CPLEX's parameters to the desired values (" + e.getMessage() + ").");
            }

            watch.reset();
            watch.start();
            //Solve the MIP.
            final boolean feasible;
            try {
                feasible = cplex.solve();
            } catch (IloException e) {
                e.printStackTrace();
                log.error("CPLEX could not solve the MIP.", e);
                throw new IllegalStateException("CPLEX could not solve the MIP (" + e.getMessage() + ").");
            }

            //Gather output
            final SATResult satisfiability;
            final double runtime = watch.getElapsedTime();
            log.info("Runtime was {}", runtime);
            final Map<Integer, Integer> assignment;

            final IloCplex.Status status = cplex.getStatus();
            final double objValue = cplex.getObjValue();
            log.info("CPLEX status: {}. Objective: {}", status, objValue);

            if (status.equals(IloCplex.Status.Optimal)) {
                if (feasible) {
                    satisfiability = SATResult.SAT;
                    assignment = getAssignment();
                } else {
                    satisfiability = SATResult.UNSAT;
                    assignment = null;
                }
            } else if (status.equals(IloCplex.Status.Feasible)) {
                satisfiability = SATResult.SAT;
                //Parse the assignment.
                assignment = getAssignment();
            } else if (status.equals(IloCplex.Status.Infeasible)) {
                satisfiability = SATResult.UNSAT;
                assignment = null;
            } else if (status.equals(IloCplex.Status.Unknown)) {
                satisfiability = SATResult.TIMEOUT;
                assignment = null;
            } else {
                log.error("CPLEX has a bad post-execution status.");
                log.error(status.toString());
                satisfiability = SATResult.CRASHED;
                assignment = null;
            }

            log.info("Satisfiability is {}", satisfiability);
            if (assignment != null) {
                log.info("Verifying solution");
                StationPackingUtils.weakVerify(stationManager, constraintManager, assignment);
                log.info("Verified!");
                log.info("Assignment is {}", assignment);
                log.info("Assignment contains {} stations on air", assignment.size());
            }

//            cplex.exportModel("model.lp");
//            cplex.writeSolution("solution.lp");

            final double cpuTime = cplex.getCplexTime();

            //Wrap up.
            cplex.end();
            return MIPResult.builder()
                    .assignment(assignment)
                    .objectiveValue(objValue)
                    .status(status)
                    .stations(domains.keySet())
                    .notParticipating(nonParticipating)
                    .cputime(cpuTime)
                    .walltime(watch.getElapsedTime())
                    .build();
        }

        private Map<Integer, Integer> getAssignment() throws IloException {
            double eps = cplex.getParam(IloCplex.DoubleParam.EpInt);
            final Map<Integer, Integer> assignment = new HashMap<>();
            for (Map.Entry<IloIntVar, StationChannel> entryDecoder : variablesDecoder.entrySet()) {
                final IloIntVar variable = entryDecoder.getKey();
                try {
                    log.info("{} = {}", variable.getName(), cplex.getValue(variable));
                    if (MathUtils.equals(cplex.getValue(variable), 1, eps)) {
                        final StationChannel stationChannelPair = entryDecoder.getValue();
                        final Integer station = stationChannelPair.getStation();
                        final Integer channel = stationChannelPair.getChannel();
                        assignment.put(station, channel);
                    }
                } catch (IloException e) {
                    e.printStackTrace();
                    log.error("Could not get MIP value assignment for variable " + variable + ".", e);
                    throw new IllegalStateException("Could not get MIP value assignment for variable " + variable + " (" + e.getMessage() + ").");
                }
            }
            return assignment;
        }

    }

}

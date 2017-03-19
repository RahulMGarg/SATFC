package ca.ubc.cs.beta.stationpacking.solvers.sat.solvers.probSAT;

import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;
import ca.ubc.cs.beta.stationpacking.solvers.sat.base.CNF;
import ca.ubc.cs.beta.stationpacking.solvers.sat.base.Literal;
import ca.ubc.cs.beta.stationpacking.solvers.sat.solvers.AbstractCompressedSATSolver;
import ca.ubc.cs.beta.stationpacking.solvers.sat.solvers.base.SATSolverResult;
import ca.ubc.cs.beta.stationpacking.solvers.sat.solvers.jnalibraries.UBCSATLibrary;
import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;
import com.google.common.base.Preconditions;
import com.sun.jna.Pointer;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by peterawest on 16-11-15.
 */
@Slf4j
public class ProbSATSolver extends AbstractCompressedSATSolver {

    private UBCSATLibrary fLibrary;
    private final String probSATPath;
    private final String runsolverPath;
    private final String parameters;
//    private final int seedOffset;
//    private final String fParameters;
    private Pointer fState;
    private final Lock lock = new ReentrantLock();
    // boolean represents whether or not a solve is in progress, so that it is safe to do an interrupt
    private final AtomicBoolean isCurrentlySolving = new AtomicBoolean(false);
//    private final ProblemIncrementor problemIncrementor;
    private final String nickname;
    private double cutOff = 30;
    private SATResult satResult;
    private float walltime;


    public ProbSATSolver(String probSATPath, String runsolverPath, String parameters, String nickname) {
//        log.info("Building SimpSATSolver");
        this.probSATPath = probSATPath;
        this.runsolverPath = runsolverPath;
        this.nickname = nickname;
        this.parameters = parameters;
//        this.seedOffset = seedOffset;
        String mutableParameters = parameters;

//        log.info("Done Building SimpSATSolver");
    }

    @Override
    public SATSolverResult solve(CNF aCNF, ITerminationCriterion aTerminationCriterion, long aSeed) {
        return solve(aCNF, null, aTerminationCriterion, aSeed);
    }

    @Override
    public SATSolverResult solve(CNF aCNF, Map<Long, Boolean> aPreviousAssignment, ITerminationCriterion aTerminationCriterion, long aSeed) {

        log.info("using PROBSAT");
        try {

            // create temp files
            File tempIn = File.createTempFile("tempFile",".txt");
            tempIn.deleteOnExit();
            File tempOutPico = File.createTempFile("tempFile",".txt");
            tempOutPico.deleteOnExit();
            File tempOutRunsolver = File.createTempFile("tempFile",".txt");
            tempOutRunsolver.deleteOnExit();


            // Write problem to file
            String problem = aCNF.toDIMACS(null);
            Writer writer = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(tempIn.getCanonicalPath()), "utf-8"));
            writer.write(problem);
            writer.close();


            // Run picosat process
            Runtime rt = Runtime.getRuntime();

            File probSATDir = new File(probSATPath);
            String processString = "";
            processString = processString + runsolverPath + "/runsolver";

            cutOff = aTerminationCriterion.getRemainingTime();
            processString = processString + " -C " + Double.toString(cutOff);
            processString = processString + " -o " + tempOutPico.getCanonicalPath();
            processString = processString + " -w " + tempOutRunsolver.getCanonicalPath();
            processString = processString + " ./probSAT -a " + parameters;
            processString = processString + " " + tempIn.getCanonicalPath() + " " + Long.toString(aSeed);
            Process pr = rt.exec(processString,null,probSATDir);
            try {
            pr.waitFor();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }


            // Create list of lines from tempOut file
            List<String> picoFileLines = Files.readAllLines(Paths.get(tempOutPico.getCanonicalPath()), StandardCharsets.UTF_8);

            List<Integer> assignment = new ArrayList<Integer>();
            Pattern satPattern = Pattern.compile("s SATISFIABLE");
            Pattern unsatPattern = Pattern.compile("s UNSATISFIABLE");
            Pattern unknownPattern = Pattern.compile("s UNKNOWN");
            Pattern indeterminatePattern = Pattern.compile("INDETERMINATE");

            for (String x : picoFileLines) {
//                System.out.println(x);

                Matcher satMatcher = satPattern.matcher(x);
                Matcher unsatMatcher = unsatPattern.matcher(x);
                Matcher unknownMatcher = unknownPattern.matcher(x);
                Matcher indeterminateMatcher = indeterminatePattern.matcher(x);

                if (satMatcher.find()) {
                    satResult = SATResult.SAT;
                } else if (unsatMatcher.find()) {
                    satResult = SATResult.UNSAT;
                } else if (unknownMatcher.find() || indeterminateMatcher.find()) {
                    satResult = SATResult.TIMEOUT;
                }


                if (x.charAt(0) == "v".charAt(0)){

                    Scanner scanner = new Scanner(x.substring(1));
                    List<Integer> list = new ArrayList<Integer>();
                    while (scanner.hasNextInt()) {
                        list.add(scanner.nextInt());
                    }
                    assignment.addAll(list);
                }
            }


            // Create list of lines from tempOut file
            List<String> runsolverFileLines = Files.readAllLines(Paths.get(tempOutRunsolver.getCanonicalPath()), StandardCharsets.UTF_8);

            Pattern walltimePattern = Pattern.compile("walltime:");
            Pattern timeLimit1Pattern = Pattern.compile("runsolver_max_cpu_time_exceeded");
            Pattern timeLimit2Pattern = Pattern.compile("Maximum CPU time exceeded");
            Pattern memLimitPattern = Pattern.compile("runsolver_max_memory_limit_exceeded");

            for (String x : runsolverFileLines) {
                Matcher walltimeMatcher = walltimePattern.matcher(x);
                Matcher timeLimit1Matcher = timeLimit1Pattern.matcher(x);
                Matcher timeLimit2Matcher = timeLimit2Pattern.matcher(x);
                Matcher memLimitMatcher = memLimitPattern.matcher(x);


                if (walltimeMatcher.find()) {
                    walltime = Float.parseFloat(x.substring(walltimeMatcher.end()));
                } else if (timeLimit1Matcher.find() || timeLimit2Matcher.find()) {
                    satResult = SATResult.TIMEOUT;
                } else if (memLimitMatcher.find()) {
                    satResult = SATResult.TIMEOUT;
                }


                if (x.length() > 0 && x.charAt(0) == "v".charAt(0)){

                    Scanner scanner = new Scanner(x.substring(1));
                    List<Integer> list = new ArrayList<Integer>();
                    while (scanner.hasNextInt()) {
                        list.add(scanner.nextInt());
                    }
                    assignment.addAll(list);
                }
            }



            if (satResult == SATResult.TIMEOUT) {
                log.info("returning timeout object");
                return SATSolverResult.timeout(walltime);
            }

            tempIn.delete();
            tempOutPico.delete();
            tempOutRunsolver.delete();


            Set<Literal> literalAssignment = new HashSet<Literal>();

            for (Integer x : assignment) {
                literalAssignment.add(new Literal(Math.abs(x), (x>0)));
            }

            log.info(satResult.toString());
            log.info(SolverResult.SolvedBy.PROBSAT.toString());
//            System.out.println("sat result here is: " + satResult.toString() + "\n");
            return new SATSolverResult(satResult, walltime, literalAssignment,SolverResult.SolvedBy.PROBSAT);



        } catch (IOException e){
            log.info("io exception");
            throw new RuntimeException(e);
        }
    }

    private void checkStatus(boolean status, UBCSATLibrary library, Pointer state) {
        Preconditions.checkState(status, library.getErrorMessage(state));
    }

    @Override
    public void notifyShutdown() {
        log.info("In Picosat, notifyShutdown");
    }

    @Override
    public void interrupt() {
        log.info("In Picosat, interrupt");
    }
}
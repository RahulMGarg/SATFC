package ca.ubc.cs.beta.stationpacking.execution.parameters.solver.sat;

import ca.ubc.cs.beta.aeatk.options.AbstractOptions;
import ca.ubc.cs.beta.stationpacking.facade.SATFCFacadeBuilder;
import ca.ubc.cs.beta.stationpacking.solvers.sat.solvers.ISATSolver;
import ca.ubc.cs.beta.stationpacking.solvers.sat.solvers.nonincremental.UBCSATSolver;

/**
 * @author pcernek
 */
public class UBCSATLibSATSolverParameters extends AbstractOptions implements ISATSolverParameters {

    /**
     *
     */
    public final static String DEFAULT_SATENSTEIN = "-alg satenstein -cutoff max";
    public final static String DEFAULT_DCCA = "-alg dcca -cutoff max";

    /**
     * Optimal parameter configuration for the QCP instance distribution found by tuning SATenstein 1.0 using ParamILS in Fall 2014.
     */
    public final static String STEIN_QCP_PARAMILS = "-alg satenstein -adaptive 0 -alpha 1.3 -clausepen 0 -heuristic 5 -maxinc 10 -novnoise 0.3 -performrandomwalk 1 -pflat 0.15  -promisinglist 0 -randomwalk 4 -rfp 0.07 -rho 0.8 -sapsthresh -0.1 -scoringmeasure 1 -selectclause 1  -singleclause 1 -tabusearch 0  -varinfalse 1";

    /**
     * Optimal parameter configuration for the R3SAT instance distribution found by tuning SATenstein 1.0 using ParamILS in Fall 2014.
     */
    public final static String STEIN_R3SAT_PARAMILS = "-alg satenstein -adaptivenoisescheme 1 -adaptiveprom 0 -adaptpromwalkprob 0 -adaptwalkprob 0 -alpha 1.126 -decreasingvariable 3 -dp 0.05 -heuristic 2 -novnoise 0.5 -performalternatenovelty 1 -phi 5 -promdp 0.05 -promisinglist 0 -promnovnoise 0.5 -promphi 5 -promtheta 6 -promwp 0.01 -rho 0.17 -scoringmeasure 3 -selectclause 1 -theta 6 -tiebreaking 1 -updateschemepromlist 3 -wp 0.03 -wpwalk 0.3 -adaptive 1 -clausepen 1 -performrandomwalk 0 -singleclause 0 -smoothingscheme 1 -tabusearch 0 -varinfalse 1";

    /**
     * Optimal parameter configuration for the FAC instance distribution found by tuning SATenstein 1.0 using ParamILS in Fall 2014.
     */
    public final static String STEIN_FAC_PARAMILS = "-alg satenstein -adaptivenoisescheme 2 -adaptiveprom 0 -adaptpromwalkprob 0 -adaptwalkprob 0 -alpha 1.126 -c 0.0001 -decreasingvariable 3 -dp 0.05 -heuristic 2 -novnoise 0.5 -performalternatenovelty 1 -phi 5 -promdp 0.05 -promisinglist 0 -promnovnoise 0.5 -promphi 5 -promtheta 6 -promwp 0.01 -ps 0.033 -rho 0.8 -s 0.001 -scoringmeasure 3 -selectclause 1 -theta 6 -tiebreaking 3 -updateschemepromlist 3 -wp 0.04  -wpwalk 0.3 -adaptive 0 -clausepen 1 -performrandomwalk 0 -singleclause 0 -smoothingscheme 1 -tabusearch 0 -varinfalse 1";

    /**
     * Optimal parameter configuration for the CBMC instance distribution found by tuning SATenstein 1.0 using ParamILS in Fall 2014.
     */
    public final static String STEIN_CBMC_PARAMILS = "-alg satenstein -adaptive 0 -alpha 1.3 -c 0.0001 -clausepen 0 -heuristic 8 -maxinc 10 -performrandomwalk 0 -pflat 0.15 -promisinglist 0 -rho 0.8 -s 0.001 -sapsthresh -0.1 -scoringmeasure 1 -selectclause 1 -singleclause 1 -tabusearch 0 -varinfalse 1 -wpwalk 0.1";

    /**
     * Optimal parameter configuration for the HGEN instance distribution found by tuning SATenstein 1.0 using ParamILS in Fall 2014.
     */
    public final static String STEIN_HGEN_PARAMILS = "-alg satenstein -adaptive 0 -alpha 1.3 -clausepen 0 -heuristic 7 -maxinc 10 -performrandomwalk 1 -pflat 0.15 -promisinglist 0 -randomwalk 3 -rdp 0.07 -rho 0.8 -sapsthresh -0.1 -scoringmeasure 1 -selectclause 1 -singleclause 1 -tabu 3 -tabusearch 1 -varinfalse 1 -wpwalk 0.1";

    /**
     * Optimal parameter configuration for the SWGCP instance distribution found by tuning SATenstein 1.0 using ParamILS in Fall 2014.
     */
    public final static String STEIN_SWGCP_PARAMILS = "-alg satenstein -adaptive 0 -alpha 1.3 -clausepen 0 -heuristic 1 -maxinc 10 -novnoise 0.1 -performalternatenovelty 0 -performrandomwalk 1 -pflat 0.15 -promisinglist 0 -randomwalk 3 -rdp 0.01 -rho 0.8 -sapsthresh -0.1 -scoringmeasure 1 -selectclause 1 -singleclause 1 -tabusearch 0 -varinfalse 1";

    @Override
    public ISATSolver getSATSolver() {
        return new UBCSATSolver(SATFCFacadeBuilder.findSATFCLibrary(SATFCFacadeBuilder.SATFCLibLocation.UBCSAT), DEFAULT_SATENSTEIN);
    }

}

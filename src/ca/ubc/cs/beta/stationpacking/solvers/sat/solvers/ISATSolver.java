package ca.ubc.cs.beta.stationpacking.solvers.sat.solvers;

import ca.ubc.cs.beta.stationpacking.solvers.base.SATSolverResult;
import ca.ubc.cs.beta.stationpacking.solvers.sat.base.CNF;

public interface ISATSolver {

	/**
	 * @param aCNF - a CNF to solve.
	 * @param aCutoff - the cutoff for the execution.
	 * @param aSeed - the seed for the execution.
	 */
	public SATSolverResult solve(CNF aCNF, double aCutoff, long aSeed);
	
	public void notifyShutdown();
}

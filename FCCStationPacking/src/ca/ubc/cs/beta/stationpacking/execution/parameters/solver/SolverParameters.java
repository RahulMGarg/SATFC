package ca.ubc.cs.beta.stationpacking.execution.parameters.solver;


import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.ParametersDelegate;

import ca.ubc.cs.beta.aclib.misc.options.UsageTextField;
import ca.ubc.cs.beta.aclib.options.AbstractOptions;
import ca.ubc.cs.beta.stationpacking.datamanagers.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.datamanagers.IStationManager;
import ca.ubc.cs.beta.stationpacking.solver.ISolver;

/**
 * Parameter class to merge different types of solvers (TAE and Incremental).
 * @author afrechet
 * TODO for future usage.
 */
@UsageTextField(title="FCC Station Packing Packing Solver Options",description="Parameters defining a feasibility checker SAT solver.")
public class SolverParameters extends AbstractOptions implements ISolverParameters{
	
	public static enum SolverChoice
	{
		INCREMENTAL,TAE;
	};

	@Parameter(names = "--solver-type",description = "the type of solver that will be executed.", required=true)
	public SolverChoice SolverChoice;
	
	@ParametersDelegate
	private TAESolverParameters TAESolverParameters = new TAESolverParameters();
	
	@ParametersDelegate
	private IncrementalSolverParameters IncrementalSolverParameters = new IncrementalSolverParameters();
	
	@Override
	public ISolver getSolver(IStationManager aStationManager, IConstraintManager aConstraintManager)
	{
		if(SolverChoice==null)
		{
			throw new ParameterException("Solver choice (--solver-type) must be defined!");
		}
		switch(SolverChoice)
		{
			case TAE:
				return TAESolverParameters.getSolver(aStationManager,aConstraintManager);
			case INCREMENTAL:
				return IncrementalSolverParameters.getSolver(aStationManager,aConstraintManager);
			default:
				throw new ParameterException("Unrecognized solver choice "+SolverChoice);
		}
	}
	
	

}

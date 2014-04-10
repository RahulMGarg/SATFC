package ca.ubc.cs.beta.stationpacking.solvers.termination.cputime;


import org.apache.commons.math3.util.FastMath;

import com.google.common.util.concurrent.AtomicDouble;

import ca.ubc.cs.beta.aclib.misc.cputime.CPUTime;
import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;

public class CPUTimeTerminationCriterion implements ITerminationCriterion 
{
	private final double fStartingCPUTime;
	private AtomicDouble fExternalEllapsedCPUTime;
	private final double fCPUTimeLimit;
	
	private final CPUTime fTimer;
	
	/**
	 * Create a CPU time termination criterion starting immediately and running for the provided duration (s).
	 * @param aCPUTimeLimit - CPU time duration (s).
	 */
	public CPUTimeTerminationCriterion(double aCPUTimeLimit)
	{
		fTimer = new CPUTime();
		
		fExternalEllapsedCPUTime = new AtomicDouble(0.0);
		fCPUTimeLimit = aCPUTimeLimit;
		fStartingCPUTime = getCPUTime();
	}

	private double getCPUTime()
	{
		return fTimer.getCPUTime()+fExternalEllapsedCPUTime.get();
	}
	
	private double getEllapsedCPUTime()
	{
		return getCPUTime() - fStartingCPUTime;
	}
	
	@Override
	public boolean hasToStop()
	{
		return (getEllapsedCPUTime() >= fCPUTimeLimit);
	}

	@Override
	public double getRemainingTime() {
		return FastMath.max(fCPUTimeLimit-getEllapsedCPUTime(), 0.0);
	}

	@Override
	public void notifyEvent(double aTime) {
		fExternalEllapsedCPUTime.addAndGet(aTime);
	}

	

	
}

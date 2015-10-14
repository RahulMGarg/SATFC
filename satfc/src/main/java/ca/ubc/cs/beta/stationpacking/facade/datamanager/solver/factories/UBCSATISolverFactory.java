package ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.factories;

import lombok.RequiredArgsConstructor;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.solvers.sat.CompressedSATBasedSolver;
import ca.ubc.cs.beta.stationpacking.solvers.sat.cnfencoder.SATCompressor;
import ca.ubc.cs.beta.stationpacking.solvers.sat.solvers.AbstractCompressedSATSolver;
import ca.ubc.cs.beta.stationpacking.solvers.sat.solvers.nonincremental.ubcsat.UBCSATSolver;

/**
 * Created by newmanne on 03/09/15.
 */
@RequiredArgsConstructor
public class UBCSATISolverFactory implements ISATSolverFactory {

    private final UBCSATLibraryGenerator libraryGenerator;
    private final SATCompressor satCompressor;

    @Override
    public CompressedSATBasedSolver create(String params, int seedOffset) {
        final AbstractCompressedSATSolver SATSolver = new UBCSATSolver(libraryGenerator.createLibrary(), params, seedOffset);
        return new CompressedSATBasedSolver(SATSolver, satCompressor);
    }

}

/**
 * Copyright 2016, Auctionomics, Alexandre Fréchette, Neil Newman, Kevin Leyton-Brown.
 *
 * This file is part of SATFC.
 *
 * SATFC is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SATFC is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SATFC.  If not, see <http://www.gnu.org/licenses/>.
 *
 * For questions, contact us at:
 * afrechet@cs.ubc.ca
 */
package ca.ubc.cs.beta.stationpacking.solvers.sat.cnfencoder;


import org.apache.commons.math3.util.Pair;

import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.solvers.sat.base.CNF;

/**
 * Encodes a problem instance as a propositional satisfiability problem. 
 * @author afrechet
 */
public interface ISATEncoder {
	

	/**
	 * Encodes a station packing problem instances to a SAT CNF formula.
	 * 
	 * @param aInstance - an instance to encode as a SAT problem.
	 * @return a SAT CNF representation of the problem instance. 
	 */
	public Pair<CNF,ISATDecoder> encode(StationPackingInstance aInstance);

	/**
	 *
	 * @param aInstance
	 * @return
	 */
	public SATEncoder.CNFEncodedProblem encodeWithAssignment(StationPackingInstance aInstance);
	
}

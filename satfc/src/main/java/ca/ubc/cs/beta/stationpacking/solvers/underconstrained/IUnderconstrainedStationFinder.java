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
package ca.ubc.cs.beta.stationpacking.solvers.underconstrained;

import java.util.Map;
import java.util.Set;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;

/**
 * Created by newmanne on 1/8/15.
 */
public interface IUnderconstrainedStationFinder {

    /** Returns the set of stations that are underconstrained (they will ALWAYS have a feasible channel) */
    default Set<Station> getUnderconstrainedStations(Map<Station, Set<Integer>> domains, ITerminationCriterion criterion) {
        return getUnderconstrainedStations(domains, criterion, domains.keySet());
    }

    Set<Station> getUnderconstrainedStations(Map<Station, Set<Integer>> domains, ITerminationCriterion criterion, Set<Station> stationsToCheck);

}

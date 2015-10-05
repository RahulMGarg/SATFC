/**
 * Copyright 2015, Auctionomics, Alexandre Fréchette, Neil Newman, Kevin Leyton-Brown.
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
package ca.ubc.cs.beta.stationpacking.facade.datamanager.data;

import ca.ubc.cs.beta.stationpacking.cache.CacheCoordinate;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.datamanagers.stations.IStationManager;

/**
 * Bundle object containing a station and a constraint manager.
 */
public class ManagerBundle {

	private IStationManager fStationManager;
	private IConstraintManager fConstraintManager;
	private String fInterferenceFolder;
    private boolean fCompact;

    /**
	 * Creates a new bundle containing the given station and constraint manager.
	 * @param stationManager station manager to be bundled.
	 * @param constraintManager constraint manager to be bundled.
	 */
	public ManagerBundle(IStationManager stationManager, IConstraintManager constraintManager, String interferenceFolder, boolean compact) {
		fStationManager = stationManager;
		fConstraintManager = constraintManager;
        fInterferenceFolder = interferenceFolder;
        fCompact = compact;
	}

	/**
	 * Returns the bundled station manager.
	 * @return the bundled station manager.
	 */
	public IStationManager getStationManager()
	{
		return fStationManager;
	}
	
	/**
	 * Returns the bundled constraint manager.
	 * @return the bundled station manager.
	 */
	public IConstraintManager getConstraintManager()
	{
		return fConstraintManager;
	}

    public CacheCoordinate getCacheCoordinate() {
        return new CacheCoordinate(fStationManager.getDomainHash(), fConstraintManager.getConstraintHash());
    }

    public boolean getCompact() {
        return fCompact;
    }

    public String getInterferenceFolder() {
        return fInterferenceFolder;
    }
	
}

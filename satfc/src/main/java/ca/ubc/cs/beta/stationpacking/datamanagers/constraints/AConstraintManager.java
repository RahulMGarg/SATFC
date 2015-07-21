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
package ca.ubc.cs.beta.stationpacking.datamanagers.constraints;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;
import ca.ubc.cs.beta.stationpacking.base.Station;

/**
 * Created by newmanne on 06/03/15.
 */
@Slf4j
public abstract class AConstraintManager implements IConstraintManager {

    @Override
    public boolean isSatisfyingAssignment(Map<Integer, Set<Station>> aAssignment) {

        final Set<Station> allStations = new HashSet<Station>();

        for(Integer channel : aAssignment.keySet())
        {
            final Set<Station> channelStations = aAssignment.get(channel);

            for(Station station1 : channelStations)
            {
                //Check if we have already seen station1
                if(allStations.contains(station1))
                {
                    log.debug("Station {} is assigned to multiple channels.");
                    return false;
                }

                {
                    //Make sure current station does not CO interfere with other stations.
                    final Collection<Station> coInterferingStations = getCOInterferingStations(station1, channel);
                    for (Station station2 : channelStations) {
                        if (coInterferingStations.contains(station2)) {
                            log.debug("Station {} and {} share channel {} on which they CO interfere.", station1, station2, channel);
                            return false;
                        }
                    }
                }

                {
                    //Make sure current station does not ADJ+1 interfere with other stations.
                    final Collection<Station> adjInterferingStations = getADJplusOneInterferingStations(station1, channel);
                    int channelp1 = channel + 1;
                    final Set<Station> channelp1Stations = aAssignment.get(channelp1);
                    if (channelp1Stations != null) {
                        for (Station station2 : channelp1Stations) {
                            if (adjInterferingStations.contains(station2)) {
                                log.debug("Station {} is on channel {}, and station {} is on channel {}, causing ADJ+1 interference.", station1, channel, station2, channelp1);
                                return false;
                            }
                        }
                    }
                }

                {
                    //Make sure current station does not ADJ+2 interfere with other stations.
                    final Collection<Station> adjPlusTwoInterferingStations = getADJplusTwoInterferingStations(station1, channel);
                    int channelp2 = channel + 2;
                    final Set<Station> channelp2Stations = aAssignment.get(channelp2);
                    if (channelp2Stations != null) {
                        for (Station station2 : channelp2Stations) {
                            if (adjPlusTwoInterferingStations.contains(station2)) {
                                log.debug("Station {} is on channel {}, and station {} is on channel {}, causing ADJ+2 interference.", station1, channel, station2, channelp2);
                                return false;
                            }
                        }
                    }
                }

            }
            allStations.addAll(channelStations);
        }
        return true;
    }

}

package ca.ubc.cs.beta.fcc.simulator.ladder;

import ca.ubc.cs.beta.fcc.simulator.station.IStationInfo;
import ca.ubc.cs.beta.fcc.simulator.utils.Band;
import com.google.common.collect.ImmutableMap;

import java.util.Map;

/**
 * Created by newmanne on 2016-07-26.
 */
public interface IModifiableLadder extends ILadder {

    /**
     * Add a station to the ladder. The band on which the station is added will be considered its home band.
     *
     * @param aStationBands - a map taking stations to add to the band in which to put them.
     */
    void addStations(Map<IStationInfo, Band> aStationBands);

    /**
     * move key stations to their respective value band in the given move map.
     *
     * @param aMoves - a map taking stations to their band they want to move to.
     */
    void moveStations(Map<IStationInfo, Band> aMoves);

    default void moveStation(IStationInfo station, Band band) {
        moveStations(ImmutableMap.of(station, band));
    }

}
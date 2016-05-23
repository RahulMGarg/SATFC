package ca.ubc.cs.beta.fcc.simulator.station;

import java.util.Collection;

/**
 * Created by newmanne on 2016-05-20.
 */
public interface StationDB {

    StationInfo getStationById(int stationID);

    Collection<StationInfo> getStations();

}

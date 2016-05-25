package ca.ubc.cs.beta.fcc.simulator.participation;

import ca.ubc.cs.beta.fcc.simulator.station.Nationality;
import ca.ubc.cs.beta.fcc.simulator.station.StationDB;
import ca.ubc.cs.beta.fcc.simulator.station.StationInfo;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Created by newmanne on 2016-05-20.
 */
public class ParticipationRecord {

    public ParticipationRecord() {
        participationMap = new ConcurrentHashMap<>();
    }

    public ParticipationRecord(StationDB stationDB, IParticipationDecider participationDecider) {
        this();
        for (StationInfo s : stationDB.getStations()) {
            if (s.getNationality().equals(Nationality.CANADA)) {
                setParticipation(s, Participation.NOT_PARTICIPATING);
            } else {
                setParticipation(s, participationDecider.isParticipating(s) ? Participation.ACTIVE : Participation.NOT_PARTICIPATING);
            }
        }
    }

    final Map<StationInfo, Participation> participationMap;

    public void setParticipation(StationInfo s, Participation participation) {
        participationMap.put(s, participation);
    }

    public Set<StationInfo> getActiveStations() {
        return participationMap.entrySet().stream().filter(e -> e.getValue().equals(Participation.ACTIVE)).map(Map.Entry::getKey).collect(Collectors.toSet());
    }

    public Set<StationInfo> getOnAirStations() {
        return participationMap.entrySet().stream().filter(e -> e.getValue().equals(Participation.EXITED) || e.getValue().equals(Participation.NOT_PARTICIPATING)).map(Map.Entry::getKey).collect(Collectors.toSet());
    }

    public Participation getParticipation(StationInfo s) {
        return participationMap.get(s);
    }

}

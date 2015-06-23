package ca.ubc.cs.beta.stationpacking.execution.extendedcache;

import ca.ubc.cs.beta.stationpacking.cache.containment.ContainmentCacheSATEntry;

import java.util.BitSet;

/**
 * Created by emily404 on 6/4/15.
 */
public interface IStationSampler {

    /**
     * Determine a new station to add to the problem based on the sampling method
     * {@link ca.ubc.cs.beta.stationpacking.execution.extendedcache.StationSamplerFactory.StationSamplingMethod}
     * @param bitSet a BitSet representing stations that are present in a problem
     * @return stationID of the station to be added
     */
    Integer sample(BitSet bitSet);
}
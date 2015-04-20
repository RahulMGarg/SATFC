package ca.ubc.cs.beta.stationpacking.cache.containment;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.cache.containment.containmentcache.IContainmentCache;
import ca.ubc.cs.beta.stationpacking.cache.containment.containmentcache.IContainmentCacheBundle;
import ca.ubc.cs.beta.stationpacking.utils.CacheUtils;
import lombok.RequiredArgsConstructor;

import java.util.BitSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.stream.StreamSupport;

/**
 * Created by newmanne on 19/04/15.
 */
// TODO: better name
@RequiredArgsConstructor
public class ContainmentCacheBundle implements IContainmentCacheBundle {

    final IContainmentCache<Station, ContainmentCacheSATEntry> SATCache;
    final IContainmentCache<Station, ContainmentCacheUNSATEntry> UNSATCache;

    @Override
    public ContainmentCacheSATResult proveSATBySuperset(final StationPackingInstance aInstance) {
        // convert instance to bit set representation
        final BitSet bitSet = CacheUtils.toBitSet(aInstance);
        // try to narrow down the entries we have to search by only looking at supersets
        final Iterator<ContainmentCacheSATEntry> supersets = SATCache.getSupersets(new ContainmentCacheSATEntry(bitSet));
        final Iterable<ContainmentCacheSATEntry> iterable = () -> supersets;
        return StreamSupport.stream(iterable.spliterator(), false)
                /**
                 * The entry must contain at least every station in the query in order to provide a solution (hence superset)
                 * The entry should also be a solution to the problem, which it will be as long as the solution can project onto the query's domains since they come from the set of interference constraints
                 */
                .filter(entry -> entry.isSolutionTo(aInstance))
                .map(entry -> new ContainmentCacheSATResult(entry.getAssignmentChannelToStation(), entry.getKey()))
                .findAny()
                .orElse(ContainmentCacheSATResult.failure());
    }

    @Override
    public ContainmentCacheUNSATResult proveUNSATBySubset(final StationPackingInstance aInstance) {
        // convert instance to bit set representation
        final BitSet bitSet = CacheUtils.toBitSet(aInstance);
        // try to narrow down the entries we have to search by only looking at subsets
        final Iterator<ContainmentCacheUNSATEntry> subsets = UNSATCache.getSubsets(new ContainmentCacheUNSATEntry(bitSet));
        final Iterable<ContainmentCacheUNSATEntry> iterable = () -> subsets;
        return StreamSupport.stream(iterable.spliterator(), false)
                /*
                 * The entry's stations should be a subset of the query's stations (so as to be less constrained)
                 * and each station in the entry must have larger than or equal to the corresponding station domain in the target (so as to be less constrained)
                 */
                .filter(entry -> isSupersetOrEqualToByDomains(entry.getDomains(), aInstance.getDomains()))
                .map(entry -> new ContainmentCacheUNSATResult(entry.getKey()))
                .findAny()
                .orElse(ContainmentCacheUNSATResult.failure());
    }

    // true if a's domain is a superset of b's domain
    private boolean isSupersetOrEqualToByDomains(Map<Station, Set<Integer>> a, Map<Station, Set<Integer>> b) {
        return b.entrySet().stream().allMatch(entry -> {
            final Set<Integer> integers = a.get(entry.getKey());
            return integers != null && integers.containsAll(entry.getValue());
        });
    }

}

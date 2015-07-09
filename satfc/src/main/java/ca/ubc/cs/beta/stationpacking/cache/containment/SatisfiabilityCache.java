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
package ca.ubc.cs.beta.stationpacking.cache.containment;

import java.util.*;
import java.util.stream.StreamSupport;

import lombok.extern.slf4j.Slf4j;
import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.cache.containment.containmentcache.ISatisfiabilityCache;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;
import ca.ubc.cs.beta.stationpacking.utils.CacheUtils;
import containmentcache.ILockableContainmentCache;

/**
 * Created by newmanne on 19/04/15.
 */
@Slf4j
public class SatisfiabilityCache implements ISatisfiabilityCache {

    final ILockableContainmentCache<Station, ContainmentCacheSATEntry> SATCache;
    final ILockableContainmentCache<Station, ContainmentCacheUNSATEntry> UNSATCache;

    public SatisfiabilityCache(ILockableContainmentCache<Station, ContainmentCacheSATEntry> aSATCache,ILockableContainmentCache<Station, ContainmentCacheUNSATEntry> aUNSATCache) {
        SATCache = aSATCache;
        UNSATCache = aUNSATCache;
    }

    @Override
    public ContainmentCacheSATResult proveSATBySuperset(final StationPackingInstance aInstance) {
        // convert instance to bit set representation
        final BitSet bitSet = CacheUtils.toBitSet(aInstance);
        // try to narrow down the entries we have to search by only looking at supersets
        try {
            SATCache.getReadLock().lock();
            final Iterable<ContainmentCacheSATEntry> iterable = SATCache.getSupersets(new ContainmentCacheSATEntry(bitSet));
            return StreamSupport.stream(iterable.spliterator(), false)
                    /**
                     * The entry must contain at least every station in the query in order to provide a solution (hence superset)
                     * The entry should also be a solution to the problem, which it will be as long as the solution can project onto the query's domains since they come from the set of interference constraints
                     */
                    .filter(entry -> entry.isSolutionTo(aInstance))
                    .map(entry -> new ContainmentCacheSATResult(entry.getAssignmentChannelToStation(), entry.getKey()))
                    .findAny()
                    .orElse(ContainmentCacheSATResult.failure());
        } finally {
            SATCache.getReadLock().unlock();
        }
    }

    @Override
    public ContainmentCacheUNSATResult proveUNSATBySubset(final StationPackingInstance aInstance) {
        // convert instance to bit set representation
        final BitSet bitSet = CacheUtils.toBitSet(aInstance);
        // try to narrow down the entries we have to search by only looking at subsets
        try {
            UNSATCache.getReadLock().lock();
            final Iterable<ContainmentCacheUNSATEntry> iterable = UNSATCache.getSubsets(new ContainmentCacheUNSATEntry(bitSet));
            return StreamSupport.stream(iterable.spliterator(), false)
                /*
                 * The entry's stations should be a subset of the query's stations (so as to be less constrained)
                 * and each station in the entry must have larger than or equal to the corresponding station domain in the target (so as to be less constrained)
                 */
                    .filter(entry -> isSupersetOrEqualToByDomains(entry.getDomains(), aInstance.getDomains()))
                    .map(entry -> new ContainmentCacheUNSATResult(entry.getKey()))
                    .findAny()
                    .orElse(ContainmentCacheUNSATResult.failure());
        } finally {
            UNSATCache.getReadLock().unlock();
        }
    }

    @Override
    public void add(StationPackingInstance aInstance, SolverResult result, String key) {
        if (result.getResult().equals(SATResult.SAT)) {
            SATCache.add(new ContainmentCacheSATEntry(result.getAssignment(), key));
        } else if (result.getResult().equals(SATResult.UNSAT)) {
            UNSATCache.add(new ContainmentCacheUNSATEntry(aInstance.getDomains(), key));
        } else {
            throw new IllegalStateException("Tried adding a result that was neither SAT or UNSAT");
        }
    }

    /**
     * Domain a has less stations than domain b because of previous method call getSubsets();
     * If each station domain in domain a has same or more channels than the matching station in domain b,
     * then a is superset of b
     * @param a superset domain
     * @param b subset domain
     * @return true if a's domain is a superset of b's domain
     */
    private boolean isSupersetOrEqualToByDomains(Map<Station, Set<Integer>> a, Map<Station, Set<Integer>> b) {
        return a.entrySet().stream().allMatch(entry -> {
            final Set<Integer> integers = b.get(entry.getKey());
            return integers != null && entry.getValue().containsAll(integers);
        });
    }

    /**
     * removes redundant SAT entries from this SATCache
     * @return list of cache entries to be removed
     */
    @Override
    public List<ContainmentCacheSATEntry> filterSAT(){
        List<ContainmentCacheSATEntry> prunableEntries = Collections.synchronizedList(new ArrayList<>());
        Iterable<ContainmentCacheSATEntry> satEntries = SATCache.getSets();

        SATCache.getReadLock().lock();
        try{
            StreamSupport.stream(satEntries.spliterator(), true)
                    .forEach(cacheEntry -> {
                        Iterable<ContainmentCacheSATEntry> supersets = SATCache.getSupersets(cacheEntry);
                        Optional<ContainmentCacheSATEntry> foundSuperset =
                                StreamSupport.stream(supersets.spliterator(), false)
                                        .filter(entry -> entry.hasMoreSolvingPower(cacheEntry))
                                        .findAny();
                        if (foundSuperset.isPresent()) {
                            prunableEntries.add(cacheEntry);
                            if (prunableEntries.size() % 2000 == 0) {
                                log.info("Found " + prunableEntries.size() + " prunables");
                            }
                        }
                    });
        } finally {
            SATCache.getReadLock().unlock();
        }

        prunableEntries.forEach(entry -> SATCache.remove(entry));
        return prunableEntries;
    }

    /**
     * removes redundant UNSAT entries from this UNSATCache
     * @return list of cache entries to be removed
     */
    @Override
    public List<ContainmentCacheUNSATEntry> filterUNSAT(){
        List<ContainmentCacheUNSATEntry> prunableEntries = new ArrayList<>();
        Iterable<ContainmentCacheUNSATEntry> unsatEntries = UNSATCache.getSets();

        UNSATCache.getReadLock().lock();
        try {
            unsatEntries.forEach(cacheEntry -> {
                Iterable<ContainmentCacheUNSATEntry> subsets = UNSATCache.getSubsets(cacheEntry);
                // For two UNSAT problems P and Q, if Q has less stations to pack,
                // and each station has more candidate channels, then Q is less restrictive than P
                Optional<ContainmentCacheUNSATEntry> lessRestrictiveUNSAT =
                        StreamSupport.stream(subsets.spliterator(), false)
                                .filter(entry -> entry.isLessRestrictive(cacheEntry))
                                .findAny();
                if (lessRestrictiveUNSAT.isPresent()) {
                    prunableEntries.add(cacheEntry);
                }
            });
        } finally {
            UNSATCache.getReadLock().unlock();
        }

        prunableEntries.forEach(entry -> UNSATCache.remove(entry));
        return prunableEntries;
    }

}

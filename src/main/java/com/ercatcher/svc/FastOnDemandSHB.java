package com.ercatcher.svc;

import com.ercatcher.LOG;
import com.ercatcher.ConcurrencyAnalysis.C3G.C3GManager;
import com.ercatcher.ConcurrencyAnalysis.ThreadLattice;

import java.util.*;

public class FastOnDemandSHB extends AbstractOnDemandSHB{
    private int[][] happensBeforeMat;
    private static final int UNCHECKED = -1;
    private static final int CHECKED_BUT_UNKOWN = 1; // Cache?
    private static final int HBEFORE = 2;
    private static final int HAFTER = 3;
    private static final int SELF = 4;
    private int DURING_PASS_MINIMIZE_THRESHOLD = 20;
    private int BEFORE_ITER_MINIMIZE_THRESHOLD = 40;

    public FastOnDemandSHB(C3GManager c3GManager) {
        super(c3GManager);
        LOG.logln("Using Fast SHB", LOG.VERBOSE);
        happensBeforeMat = new int[svcMap.size()][svcMap.size()];
        for (int i = 0; i < svcMap.size(); i++)
            for (int j = 0; j < svcMap.size(); j++)
                happensBeforeMat[i][j] = UNCHECKED;
    }

    @Override
    protected boolean quickHB(StaticVectorClock first, StaticVectorClock second) {
        if(first == second)
            return false;
        if(first == null || second == null)
            return false;
        if(isComputationFinished()){
            if(first.temporaryOrder > second.temporaryOrder)
                return false;
        }
        if(happensBeforeMat[first.id][second.id] == HAFTER)
            return false;
        if(happensBeforeMat[first.id][second.id] == HBEFORE)
            return true;
        return false;
    }

    @Override
    protected void updateSVCs(StaticVectorClock currentSVC, Map<ThreadLattice, Set<StaticVectorClock>> myHAInvokers) {
        for(Set<StaticVectorClock> svcSet : myHAInvokers.values())
            for(StaticVectorClock haSVC : svcSet) {
                if(currentSVC instanceof InvokerSVC && haSVC.getThreadLattice().equals(currentSVC.getThreadLattice()))
                    currentSVC.addHappensAfterRelation(haSVC);
                else
                    currentSVC.addAllHappensAfterRelation(haSVC);
                happensBeforeMat[currentSVC.id][haSVC.id] = HAFTER;
                happensBeforeMat[haSVC.id][currentSVC.id] = HBEFORE;
            }
    }

    @Override
    protected void addHBRelation(StaticVectorClock first, StaticVectorClock second) {
        second.addHappensAfterRelation(first);
        happensBeforeMat[second.id][first.id] = HAFTER;
        happensBeforeMat[first.id][second.id] = HBEFORE;
    }

    @Override
    protected void minimizeDuringPass(Map<StaticVectorClock, Map<ThreadLattice, Set<StaticVectorClock>>> haInvokers, StaticVectorClock child, ThreadLattice threadLattice) {
        if (haInvokers.get(child).get(threadLattice).size() > DURING_PASS_MINIMIZE_THRESHOLD)
            haInvokers.get(child).put(threadLattice, minimize(new ArrayList<>(haInvokers.get(child).get(threadLattice))));

    }

    @Override
    protected void minimizeBeforeIter() {
        for (StaticVectorClock svc : svcMap.values()) {
            if(svc.getLatestHappensAfterSize() > BEFORE_ITER_MINIMIZE_THRESHOLD)
                svc.minimize(this::minimize);
        }
    }

    @Override
    public boolean happensBefore(StaticVectorClock first, StaticVectorClock second) {
        if(quickHB(first, second))
            return true;
        List<StaticVectorClock> queue = new ArrayList<>();
        Set<StaticVectorClock> hasBeenAddedToQueue = new HashSet<>();
        queue.add(second);
        hasBeenAddedToQueue.add(second);
        boolean result = false;
        boolean flag = false;
        List<StaticVectorClock> newlyAdded = new ArrayList<>();
        for(int i=0; i< queue.size(); i++){
            StaticVectorClock svc = queue.get(i);
            for(Iterator<StaticVectorClock> it = svc.iterAll(); it.hasNext(); ) {
                StaticVectorClock haNode = it.next();
                    if (hasBeenAddedToQueue.contains(haNode))
                        continue;
                    if(svc != second)
                        newlyAdded.add(haNode);
                    if (haNode == first) {
                        result = true;
                        flag = true;
                        break;
                    }
                    queue.add(haNode);
                    hasBeenAddedToQueue.add(haNode);
                }
            if(flag)
                break;
        }
        for (StaticVectorClock svc : newlyAdded) {
            second.addAllHappensAfterRelation(svc);
            // Matrix
            happensBeforeMat[second.id][svc.id] = HAFTER;
            happensBeforeMat[svc.id][second.id] = HBEFORE;
        }
        if (result) {
            happensBeforeMat[second.id][first.id] = HAFTER;
            happensBeforeMat[first.id][second.id] = HBEFORE;
        }
        return result;

    }

    @Override
    public boolean reaches(StaticVectorClock first, StaticVectorClock second) {
        return false;
    }
}

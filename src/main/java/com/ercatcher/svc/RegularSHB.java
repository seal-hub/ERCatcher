package com.ercatcher.svc;

import com.ercatcher.LOG;
import com.ercatcher.ConcurrencyAnalysis.C3G.C3GManager;

import java.util.*;

public class RegularSHB extends AbstractSHB{

    public RegularSHB(C3GManager c3GManager){
        super(c3GManager);
    }

    @Override
    protected Set<StaticVectorClock> minimize(List<StaticVectorClock> orderedInput) {
        return new HashSet<>(orderedInput);
    }

    @Override
    public void internalComputeSVC() {
        boolean update = true;
        while(update){
            update = false;
            boolean localUpdate = true;
            while(localUpdate) {
                localUpdate = false;
                for (StaticVectorClock first : svcMap.values()) {
                    for (StaticVectorClock second : svcMap.values()) {
                        if (first.equals(second))
                            continue;
                        if (happensBefore(first, second)) {
                            for (StaticVectorClock haFirst : first.getAllHappensAfter())
                                localUpdate |= second.addAllHappensAfterRelation(haFirst);
                        }
                    }
                }
            }
            for(SVCEvent firstSVCEvent : cventSVCEventMap.values()){
                if(!firstSVCEvent.getThreadLattice().isEventQueue())
                    continue;
                for(SVCEvent secondSVCEvent : cventSVCEventMap.values()){
                    if(firstSVCEvent.equals(secondSVCEvent))
                        continue;
                    if(!firstSVCEvent.getThreadLattice().equals(secondSVCEvent.getThreadLattice()))
                        continue;
                    StaticVectorClock firstInvoke = firstSVCEvent.invoke;
                    StaticVectorClock secondInvoke = secondSVCEvent.invoke;
                    if(happensBefore(firstInvoke, secondInvoke)){
                        update |= secondSVCEvent.start.addAllHappensAfterRelation(firstSVCEvent.end);
                    }
                }
            }
        }

        LOG.logln("HB is created", LOG.SUPER_VERBOSE);
    }


    @Override
    public boolean happensBefore(StaticVectorClock first, StaticVectorClock second) {
        return first.directHappensBefore(second);
    }

    @Override
    public boolean reaches(StaticVectorClock first, StaticVectorClock second) {
        return false;
    }
}

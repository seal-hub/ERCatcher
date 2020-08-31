package com.ercatcher.RaceDetector;

import com.ercatcher.ConcurrencyAnalysis.ThreadLattice;
import soot.SootMethod;

import java.util.*;

public class EventRaceAggregator implements RaceDetector<MethodRace> {
    Set<MethodRace> allMethodRaces = new HashSet<>();
    Set<EventRace> allEventRaces = new HashSet<>();
    Map<SootMethod, Set<ThreadLattice>> methodToThreadMap = new HashMap<>();

    public EventRaceAggregator(){}

    public void aggregate(EventRaceDetector eventRaceDetector, IfGuardNullAtEndFilter ifGuardNullAtEndFilter){
        allMethodRaces.addAll(eventRaceDetector.getRaces());
        allEventRaces.addAll(eventRaceDetector.getAllEventRaces());
        for(SootMethod sootMethod : ifGuardNullAtEndFilter.methodToThreadMap.keySet()){
            if(!methodToThreadMap.containsKey(sootMethod))
                methodToThreadMap.put(sootMethod, new HashSet<>());
            methodToThreadMap.get(sootMethod).addAll(ifGuardNullAtEndFilter.methodToThreadMap.get(sootMethod));
        }
    }

    public Set<ThreadLattice> getThreads(SootMethod sootMethod){
        if(!methodToThreadMap.containsKey(sootMethod))
            return Collections.emptySet();
        return methodToThreadMap.get(sootMethod);
    }

    public Set<EventRace> getAllEventRaces(){
        return allEventRaces;
    }


    @Override
    public Set<MethodRace> getRaces() {
        return allMethodRaces;
    }
}

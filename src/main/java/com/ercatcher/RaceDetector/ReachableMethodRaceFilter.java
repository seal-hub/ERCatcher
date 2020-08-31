package com.ercatcher.RaceDetector;

import com.ercatcher.LOG;
import com.ercatcher.ConcurrencyAnalysis.C2G.C2GManager;

import java.util.HashSet;
import java.util.Set;

public class ReachableMethodRaceFilter implements RaceDetector<MethodRace> {
    Set<MethodRace> reachableMethodRaces = new HashSet<>();
    public ReachableMethodRaceFilter(RaceDetector<MethodRace> raceDetector, C2GManager c2GManager){
        LOG.startTimer("Reachability Filter", LOG.VERBOSE);
        for(MethodRace race : raceDetector.getRaces()){
            boolean reachableRead = false;
            boolean reachableWrite = false;
            boolean realReachable = false;
            if(c2GManager.getReachableSootMethods().contains(race.getReadEvent()) || c2GManager.accesibleIMBs.containsKey(race.getReadEvent()))
                reachableRead = true;
            if(c2GManager.getReachableSootMethods().contains(race.getWriteEvent()) || c2GManager.accesibleIMBs.containsKey(race.getWriteEvent()))
                reachableWrite = true;
            if(c2GManager.getReachableSootMethods().contains(race.getReadEvent()) && c2GManager.getReachableSootMethods().contains(race.getWriteEvent())){
                realReachable = true;
                race.setPriority(MethodRace.PRIORITY_REACHABLE);
            }
            if(reachableRead && reachableWrite) {
                if(!race.isUAF() && !realReachable && race.getReadStmt().equals(race.getWriteStmt()))
                    continue;
                reachableMethodRaces.add(race);
            }

        }
        LOG.logln(String.format("Reachable MRs: %d out of %d", reachableMethodRaces.size(), raceDetector.getRaces().size()), LOG.VERBOSE);
        LOG.endTimer("Reachability Filter", LOG.VERBOSE);
    }

    @Override
    public Set<MethodRace> getRaces() {
        return reachableMethodRaces;
    }
}

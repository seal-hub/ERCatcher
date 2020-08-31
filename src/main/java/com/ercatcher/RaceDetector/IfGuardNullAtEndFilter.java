package com.ercatcher.RaceDetector;

import com.ercatcher.LOG;
import com.ercatcher.ConcurrencyAnalysis.C3G.C3GTask;
import com.ercatcher.ConcurrencyAnalysis.C3G.C3GManager;
import com.ercatcher.ConcurrencyAnalysis.C3G.SNode;
import com.ercatcher.ConcurrencyAnalysis.C2G.C2GManager;
import com.ercatcher.ConcurrencyAnalysis.ThreadLattice;
import soot.SootMethod;
import soot.jimple.Stmt;

import java.util.*;

public class IfGuardNullAtEndFilter implements RaceDetector<MethodRace> {

    private Map<SootMethod, Set<ContextMethod>> methodToContextMethod = new HashMap<>();
    Map<SootMethod, Set<ThreadLattice>> methodToThreadMap = new HashMap<>();
    private List<MethodRace> eventRaceList = new ArrayList<>();

    private void calculateMethodWithContexts(RaceDetector<MethodRace> raceDetector, C3GManager c3GManager, C2GManager myC2GManager){
        Map<SootMethod, Set<ContextMethod>> preciseMap = new HashMap<>();
        for(SootMethod sootMethod : myC2GManager.getReachableSootMethods()){
            preciseMap.put(sootMethod, new HashSet<>());
            for(C3GTask c3GTask : c3GManager.getMethodToCvent(sootMethod)){
                preciseMap.get(sootMethod).add(new CventContextMethod(sootMethod, c3GTask));
            }
            for(C3GTask c3GTask : c3GManager.getMethodToContextCvent(sootMethod)) {
                preciseMap.get(sootMethod).add(new CallerContextMethod(sootMethod, c3GTask.getCallerSNode()));
            }
            for(SNode sNode : c3GManager.getMethodToMayCallInCvent(sootMethod)){
                preciseMap.get(sootMethod).addAll(SynchCallContextMethod.makeSynchCallContextMethods(sootMethod, sNode, myC2GManager.getMethodToInterMethodBox()));
            }
        }
        methodToContextMethod = new HashMap<>(preciseMap);
    }

    public IfGuardNullAtEndFilter(RaceDetector<MethodRace> raceDetector, C3GManager c3GManager, C2GManager myC2GManager){
        this(raceDetector, c3GManager, myC2GManager, false);
    }

    public IfGuardNullAtEndFilter(RaceDetector<MethodRace> raceDetector, C3GManager c3GManager, C2GManager myC2GManager, boolean filterUnreachableMethods){
        LOG.startTimer("IfGuardNullAtEndFilter", LOG.VERBOSE);
        calculateMethodWithContexts(raceDetector, c3GManager, myC2GManager);
        LOG.logln("Using Super Fast Event Race Detection", LOG.VERBOSE);

        for(SootMethod method : methodToContextMethod.keySet()){
            if(!methodToThreadMap.containsKey(method))
                methodToThreadMap.put(method, new HashSet<>());
            for(ContextMethod contextMethod : methodToContextMethod.get(method))
                methodToThreadMap.get(method).add(contextMethod.getThread());
        }
        for(MethodRace race : raceDetector.getRaces()){
            if(methodToContextMethod.getOrDefault(race.getWriteEvent(), new HashSet<>()).size() == 0) {
//                throw new RuntimeException(String.format("Method %s does not have any context!", race.getWriteEvent()));
                if(!filterUnreachableMethods)
                    eventRaceList.add(race);
//                else
//                    LOG.logln(String.format("Method %s does not have any context!", race.getWriteEvent()), LOG.ERROR);
                continue;
            }
            if(methodToContextMethod.getOrDefault(race.getReadEvent(), new HashSet<>()).size() == 0) {
//                throw new RuntimeException(String.format("Method %s does not have any context!", race.getReadEvent()));
                if(!filterUnreachableMethods)
                    eventRaceList.add(race);
//                else
//                    LOG.logln(String.format("Method %s does not have any context!", race.getReadEvent()), LOG.ERROR);
                continue;
            }
            Stmt writeStmt = race.getWriteStmt();
            Stmt readStmt = race.getReadStmt();
            if(methodToThreadMap.get(race.getWriteEvent()).size() == 1 && methodToThreadMap.get(race.getReadEvent()).size() == 1){
                ThreadLattice writeThreadLattice = (ThreadLattice) methodToThreadMap.get(race.getWriteEvent()).toArray()[0];
                ThreadLattice readThreadLattice = (ThreadLattice) methodToThreadMap.get(race.getReadEvent()).toArray()[0];
                if(writeThreadLattice.equals(readThreadLattice) && writeThreadLattice.isEventQueue()){
                    if(race.isUAF() && !race.isNullAtEnd())
                        continue;
                    if(race.isUAF() && race.isIfGuard())
                        continue;
                }
            }
            eventRaceList.add(race);
        }
        LOG.logln(String.format("IGNAE ERs: %d out of %d", eventRaceList.size(), raceDetector.getRaces().size()), LOG.VERBOSE);
        LOG.endTimer("IfGuardNullAtEndFilter", LOG.VERBOSE);
    }

    public Set<ThreadLattice> getThreads(SootMethod sootMethod){
        if(!methodToThreadMap.containsKey(sootMethod))
            return Collections.emptySet();
        return methodToThreadMap.get(sootMethod);
    }

    @Override
    public List<MethodRace> getRaces() {
        return new ArrayList<>(eventRaceList);
    }
}

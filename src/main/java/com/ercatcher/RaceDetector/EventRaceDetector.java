package com.ercatcher.RaceDetector;

import com.ercatcher.svc.StaticHappensRelation;
import com.ercatcher.LOG;
import com.ercatcher.ConcurrencyAnalysis.C3G.C3GTask;
import com.ercatcher.ConcurrencyAnalysis.C3G.C3GManager;
import com.ercatcher.ConcurrencyAnalysis.C3G.SNode;
import com.ercatcher.ConcurrencyAnalysis.C2G.C2GManager;
import com.ercatcher.ConcurrencyAnalysis.ThreadLattice;
import com.ercatcher.svc.StaticHappensBefore;
import soot.SootMethod;
import soot.jimple.Stmt;
import soot.toolkits.scalar.Pair;

import java.util.*;

public class EventRaceDetector implements RaceDetector<MethodRace> {

    private Map<SootMethod, Set<ContextMethod>> methodToContextMethod = new HashMap<>();
    private Set<EventRace> contextEventRaceList = new HashSet<>();
    private List<MethodRace> eventRaceList = new ArrayList<>();
    private StaticHappensBefore shb;
    boolean filterUnreachableMethods = false;

    public long getAllPossibleERs() {
        return allPossibleERs;
    }

    long allPossibleERs = 0;

    private void calculateMethodWithContexts(RaceDetector<MethodRace> raceDetector, C3GManager c3GManager, C2GManager myC2GManager){
        Map<SootMethod, Set<ContextMethod>> preciseMap = new HashMap<>();
        Map<SootMethod, Set<ContextMethod>> fastMap = new HashMap<>();
        for(SootMethod sootMethod : myC2GManager.getReachableSootMethods()){
            fastMap.put(sootMethod, new HashSet<>());
            preciseMap.put(sootMethod, new HashSet<>());
            for(C3GTask c3GTask : c3GManager.getMethodToCvent(sootMethod)){
                fastMap.get(sootMethod).add(new CventContextMethod(sootMethod, c3GTask));
                preciseMap.get(sootMethod).add(new CventContextMethod(sootMethod, c3GTask));
            }
            for(C3GTask c3GTask : c3GManager.getMethodToContextCvent(sootMethod)) {
                fastMap.get(sootMethod).add(new CventContextMethod(sootMethod, c3GTask.getCallerC3GTask()));
                preciseMap.get(sootMethod).add(new CallerContextMethod(sootMethod, c3GTask.getCallerSNode()));
            }
            for(SNode sNode : c3GManager.getMethodToMayCallInCvent(sootMethod)){
                fastMap.get(sootMethod).add(new CventContextMethod(sootMethod, sNode.getMyC3GTask()));
                preciseMap.get(sootMethod).addAll(SynchCallContextMethod.makeSynchCallContextMethods(sootMethod, sNode, myC2GManager.getMethodToInterMethodBox()));
            }
        }
//        LOG.logln("Context of methods have been extracted", LOG.SUPER_VERBOSE);
        long preciseMaxCMRs = 0;
        long fastMaxCMRs = 0;
        for(MethodRace race : raceDetector.getRaces()){
            preciseMaxCMRs += ((long) preciseMap.getOrDefault(race.getWriteEvent(), new HashSet<>()).size()) * preciseMap.getOrDefault(race.getReadEvent(), new HashSet<>()).size();
            fastMaxCMRs += ((long)fastMap.getOrDefault(race.getWriteEvent(), new HashSet<>()).size()) * fastMap.getOrDefault(race.getReadEvent(), new HashSet<>()).size();
        }
        LOG.logln(String.format("Precise MaxCMRs: %d Fast MaxCMRS: %d", preciseMaxCMRs, fastMaxCMRs), LOG.VERBOSE);
        if(preciseMaxCMRs > 1_000_000){
            LOG.logln(String.format("Switch to unique context sensitive, possible maxCMRs: %d", preciseMaxCMRs), LOG.VERBOSE);
            methodToContextMethod = new HashMap<>(fastMap);
//            for(SootMethod sootMethod : myCaCGManager.getReachableSootMethods()) {
//                for (SNode sNode : cventManager.getMethodToMayCallInCvent(sootMethod)) {
//                    methodToContextMethod.get(sootMethod).add(new CventContextMethod(sootMethod, sNode.getMyCvent()));
//                }
//            }
        }
        else{
            methodToContextMethod = new HashMap<>(preciseMap);
        }

    }

    static class MethodPair extends Pair<SootMethod, SootMethod> {
        public MethodPair(SootMethod o1, SootMethod o2) {
            super(o1, o2);
        }
    }

    public EventRaceDetector(RaceDetector<MethodRace> raceDetector, C3GManager c3GManager, C2GManager myC2GManager, StaticHappensBefore shb){
        this(raceDetector, c3GManager, myC2GManager, shb, false);
    }

    public EventRaceDetector(RaceDetector<MethodRace> raceDetector, C3GManager c3GManager, C2GManager myC2GManager, StaticHappensBefore shb, boolean filterUnreachableMethods){
        LOG.startTimer("EventRaceDetector", LOG.VERBOSE);
        this.filterUnreachableMethods = filterUnreachableMethods;
        this.shb = shb;
        calculateMethodWithContexts(raceDetector, c3GManager, myC2GManager);
        allPossibleERs = 0;
        for(MethodRace race : raceDetector.getRaces()){
            allPossibleERs += ((long)methodToContextMethod.getOrDefault(race.getWriteEvent(), new HashSet<>()).size()) * methodToContextMethod.getOrDefault(race.getReadEvent(), new HashSet<>()).size();
        }
        LOG.logln(String.format("Context of methods have been extracted, MaxCMRs: %d", allPossibleERs), LOG.VERBOSE);
        if(allPossibleERs < 100_000){ // TODO: configurable
            preciseEventRaceDetection(raceDetector, shb);
        }
        else if (allPossibleERs < 10_000_000){ // TODO: configurable
            fastEventRaceDetection(raceDetector, shb);
        }
        else{
            superFastEventRaceDetection(raceDetector, shb);
        }
        for(MethodRace methodRace : eventRaceList){
            LOG.logln(methodRace.toString(), LOG.VERBOSE);
        }
        allPossibleERs = 0;
        for(MethodRace race : eventRaceList){
            allPossibleERs += ((long)methodToContextMethod.getOrDefault(race.getWriteEvent(), new HashSet<>()).size()) * methodToContextMethod.getOrDefault(race.getReadEvent(), new HashSet<>()).size();
        }
        if(allPossibleERs < 10_000){ // TODO: configurable
            for(MethodRace race : eventRaceList){
                contextEventRaceList.addAll(getEventRaces(race));
            }
            allPossibleERs = contextEventRaceList.size();
            LOG.logln(String.format("Context ERs: %d", allPossibleERs), LOG.VERBOSE);
        }
        LOG.logln(String.format("ERs: %d out of %d", eventRaceList.size(), raceDetector.getRaces().size()), LOG.VERBOSE);
        LOG.endTimer("EventRaceDetector", LOG.VERBOSE);
    }

    private void preciseEventRaceDetection(RaceDetector<MethodRace> raceDetector, StaticHappensBefore shb){
        LOG.logln("Using Precise Event Race Detection", LOG.VERBOSE);
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
            // TODO: it can be more precise by applying HB on the statements
            Stmt writeStmt = race.getWriteStmt();
            Stmt readStmt = race.getReadStmt();
            StaticHappensRelation currentSHR = StaticHappensRelation.UNKNOWN;
            for(ContextMethod writeCM : methodToContextMethod.get(race.getWriteEvent())) {
                for (ContextMethod readCM : methodToContextMethod.get(race.getReadEvent())) {
                    if(Thread.interrupted())
                        return;
                    if (writeCM.equals(readCM))
                        continue;
                    if(race.isUAF()) {
                        if (currentSHR.equals(StaticHappensRelation.CYCLE) || currentSHR.equals(StaticHappensRelation.BEFORE) || currentSHR.equals(StaticHappensRelation.BEFORE_INDETERMINATE))
                            break;
                    }
                    if(writeCM.getThread().equals(readCM.getThread()) && writeCM.getThread().isEventQueue()){
                        if(race.isUAF() && !race.isNullAtEnd())
                            continue;
                        if(race.isUAF() && race.isIfGuard())
                            continue;
                    }
                    boolean firstHBSecond = shb.happensBefore(writeCM.getAfterMethodNode(), readCM.getBeforeMethodNode());
                    boolean secondHBFirst = shb.happensBefore(readCM.getAfterMethodNode(), writeCM.getBeforeMethodNode());
                    if(race.isUAF()) {
                        if (firstHBSecond && secondHBFirst)
                            currentSHR = currentSHR.eval(StaticHappensRelation.CYCLE);
                        else if (firstHBSecond)
                            currentSHR = currentSHR.eval(StaticHappensRelation.BEFORE);
                        else if (secondHBFirst)
                            currentSHR = currentSHR.eval(StaticHappensRelation.AFTER);
                        else
                            currentSHR = currentSHR.eval(StaticHappensRelation.INDETERMINATE);
                    }
                    else{
                        if(firstHBSecond && secondHBFirst) {
                            currentSHR = currentSHR.eval(StaticHappensRelation.AFTER);
                            LOG.logln("There is a cycle in HB query", LOG.ERROR);
                        }
                        else if (firstHBSecond || secondHBFirst)
                            currentSHR = currentSHR.eval(StaticHappensRelation.AFTER);
                        else
                            currentSHR = currentSHR.eval(StaticHappensRelation.INDETERMINATE);
                    }
                }
            }
            if(race.isUAF()) {
                if (currentSHR.equals(StaticHappensRelation.AFTER) || currentSHR.equals(StaticHappensRelation.UNKNOWN)
                        || currentSHR.equals(StaticHappensRelation.SAME)
                        || currentSHR.equals(StaticHappensRelation.NOT_EXISTED))
                    continue;
            }
            else{
                if (currentSHR.equals(StaticHappensRelation.AFTER) || currentSHR.equals(StaticHappensRelation.UNKNOWN)
                        || currentSHR.equals(StaticHappensRelation.SAME)
                        || currentSHR.equals(StaticHappensRelation.NOT_EXISTED)
                        || currentSHR.equals(StaticHappensRelation.BEFORE)
                        || currentSHR.equals(StaticHappensRelation.CYCLE))
                    continue;
            }
            eventRaceList.add(race);
        }
    }

    private void fastEventRaceDetection(RaceDetector<MethodRace> raceDetector, StaticHappensBefore shb){
        LOG.logln("Using Fast Event Race Detection", LOG.VERBOSE);
        Map<MethodPair, StaticHappensRelation> methodPairToSHR = new HashMap<>();
        for(MethodRace race : raceDetector.getRaces()){
            MethodPair mp = new MethodPair(race.getWriteEvent(), race.getReadEvent());
            methodPairToSHR.put(mp, StaticHappensRelation.UNKNOWN);
        }
        Map<SootMethod, Set<ThreadLattice>> methodToThreadMap = new HashMap<>();
        for(SootMethod method : methodToContextMethod.keySet()){
            if(!methodToThreadMap.containsKey(method))
                methodToThreadMap.put(method, new HashSet<>());
            for(ContextMethod contextMethod : methodToContextMethod.get(method))
                methodToThreadMap.get(method).add(contextMethod.getThread());
        }

        long allQueryCount = 0;
        for(MethodPair methodPair : methodPairToSHR.keySet()){
            allQueryCount += methodToContextMethod.getOrDefault(methodPair.getO1(), new HashSet<>()).size() * methodToContextMethod.getOrDefault(methodPair.getO2(), new HashSet<>()).size();
        }
        LOG.logln(String.format("Method Pairs: %d, All Queries: %d", methodPairToSHR.size(), allQueryCount), LOG.VERBOSE);
        LOG.startTimer("HB Analysis", LOG.VERBOSE);
        int c = 0;
        int m = 0;
        for(MethodPair methodPair : methodPairToSHR.keySet()){
            if(Thread.interrupted())
                return;
            m++;
            if(m % 10 == 0)
                LOG.logln(String.format("MP: %d", m), LOG.VERBOSE);
            StaticHappensRelation currentSHR = methodPairToSHR.get(methodPair);
            if(methodToContextMethod.getOrDefault(methodPair.getO1(), new HashSet<>()).size() == 0) {
//                throw new RuntimeException(String.format("Method %s does not have any context!", race.getWriteEvent()));
//                if(filterUnreachableMethods)
//                    LOG.logln(String.format("Method %s does not have any context!", methodPair.getO1()), LOG.ERROR);
                continue;
            }
            if(methodToContextMethod.getOrDefault(methodPair.getO2(), new HashSet<>()).size() == 0) {
//                throw new RuntimeException(String.format("Method %s does not have any context!", race.getReadEvent()));
//                if(filterUnreachableMethods)
//                    LOG.logln(String.format("Method %s does not have any context!", methodPair.getO2()), LOG.ERROR);
                continue;
            }
            for(ContextMethod writeCM : methodToContextMethod.get(methodPair.getO1())) {
                if(currentSHR.equals(StaticHappensRelation.CYCLE) || currentSHR.equals(StaticHappensRelation.BEFORE) || currentSHR.equals(StaticHappensRelation.BEFORE_INDETERMINATE)) {
                    break;
                }
                for (ContextMethod readCM : methodToContextMethod.get(methodPair.getO2())) {
                    c++;
                    if(c % 1_000 == 0)
                        LOG.log(String.format("%d-", c), LOG.VERBOSE);
                    // This is because of UAF, it should not break in the case of general event race
                    if(currentSHR.equals(StaticHappensRelation.CYCLE) || currentSHR.equals(StaticHappensRelation.BEFORE) || currentSHR.equals(StaticHappensRelation.BEFORE_INDETERMINATE)) {
                        LOG.log(String.format("break %d", c), LOG.VERBOSE);
                        break;
                    }
                    if (writeCM.equals(readCM))
                        continue;
                    boolean firstHBSecond = shb.happensBefore(writeCM.getAfterMethodNode(), readCM.getBeforeMethodNode());
                    boolean secondHBFirst = shb.happensBefore(readCM.getAfterMethodNode(), writeCM.getBeforeMethodNode());
                    if(firstHBSecond && secondHBFirst)
                        currentSHR = currentSHR.eval(StaticHappensRelation.CYCLE);
                    else if(firstHBSecond)
                        currentSHR = currentSHR.eval(StaticHappensRelation.BEFORE);
                    else if(secondHBFirst)
                        currentSHR = currentSHR.eval(StaticHappensRelation.AFTER);
                    else
                        currentSHR = currentSHR.eval(StaticHappensRelation.INDETERMINATE);
                }
            }
            methodPairToSHR.put(methodPair, currentSHR);
        }
        LOG.endTimer("HB Analysis", LOG.VERBOSE);
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
            StaticHappensRelation currentSHR = methodPairToSHR.get(new MethodPair(race.getWriteEvent(), race.getReadEvent()));
            if(currentSHR.equals(StaticHappensRelation.AFTER) || currentSHR.equals(StaticHappensRelation.UNKNOWN)
                    || currentSHR.equals(StaticHappensRelation.SAME)
                    || currentSHR.equals(StaticHappensRelation.NOT_EXISTED))
                continue;
            eventRaceList.add(race);
        }
    }

    private void superFastEventRaceDetection(RaceDetector<MethodRace> raceDetector, StaticHappensBefore shb){
        LOG.logln("Using Super Fast Event Race Detection", LOG.VERBOSE);
        Map<SootMethod, Set<ThreadLattice>> methodToThreadMap = new HashMap<>();
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
    }

    public Set<EventRace> getEventRaces(MethodRace race) {
        Set<EventRace> eventRaceSet = new HashSet<>();
        if (methodToContextMethod.getOrDefault(race.getWriteEvent(), new HashSet<>()).size() == 0) {
//            LOG.logln(String.format("Method %s does not have any context!", race.getWriteEvent()), LOG.ERROR);
            return eventRaceSet;
        }
        if (methodToContextMethod.getOrDefault(race.getReadEvent(), new HashSet<>()).size() == 0) {
//                throw new RuntimeException(String.format("Method %s does not have any context!", race.getReadEvent()));
//            LOG.logln(String.format("Method %s does not have any context!", race.getReadEvent()), LOG.ERROR);
            return eventRaceSet;
        }
        // TODO: it can be more precise by applying HB on the statements
        Stmt writeStmt = race.getWriteStmt();
        Stmt readStmt = race.getReadStmt();
        for (ContextMethod writeCM : methodToContextMethod.get(race.getWriteEvent())) {
            for (ContextMethod readCM : methodToContextMethod.get(race.getReadEvent())) {
                if (writeCM.equals(readCM))
                    continue;
                if (writeCM.getThread().equals(readCM.getThread()) && writeCM.getThread().isEventQueue()) {
                    if (race.isUAF() && !race.isNullAtEnd())
                        continue;
                    if (race.isUAF() && race.isIfGuard())
                        continue;
                }
                boolean firstHBSecond = shb.happensBefore(writeCM.getAfterMethodNode(), readCM.getBeforeMethodNode());
                boolean secondHBFirst = shb.happensBefore(readCM.getAfterMethodNode(), writeCM.getBeforeMethodNode());
                if (secondHBFirst && !firstHBSecond)
                    continue;
                EventRace eventRace = new EventRace(writeCM, writeStmt, readCM, readStmt, race.getMemoryId());
                eventRaceSet.add(eventRace);
            }
        }
        return eventRaceSet;
    }

    public Set<EventRace> getAllEventRaces(){
        return contextEventRaceList;
    }

    @Override
    public List<MethodRace> getRaces() {
        return new ArrayList<>(eventRaceList);
    }
}

package com.ercatcher.svc;

import com.ercatcher.ConcurrencyAnalysis.ThreadLattice;

import java.util.*;

public abstract class StaticVectorClock implements Comparable{
    int id;

    @Override
    public int compareTo(Object o) {
        if(!(o instanceof  StaticVectorClock))
            return 1;
        return this.temporaryOrder - ((StaticVectorClock) o).temporaryOrder;
    }

    int temporaryOrder = 0;
    public static int SVCCount = 0;
    SVCEvent myOwnSVCEvent;

    StaticVectorClock(SVCEvent myOwnSVCEvent, Set<ThreadLattice> allThreads){
        this.myOwnSVCEvent = myOwnSVCEvent;
        this.id = SVCCount;
        SVCCount++;
        for(ThreadLattice threadLattice : allThreads){
            latestHappensAfter.put(threadLattice, new HashSet<>());
            allHappensAfterThreadWise.put(threadLattice, new HashSet<>());
        }
    }

    private Map<ThreadLattice, Set<StaticVectorClock>> latestHappensAfter = new HashMap<>();
    private Map<ThreadLattice, Set<StaticVectorClock>> allHappensAfterThreadWise = new HashMap<>();

    public Set<StaticVectorClock> getAllHappensAfter() {
        return allHappensAfter;
    }

    private Set<StaticVectorClock> allHappensAfter = new HashSet<>();
    public abstract ThreadLattice getThreadLattice();
    public void addHappensAfterRelation(StaticVectorClock other){
        latestHappensAfter.get(other.getThreadLattice()).add(other);
        addAllHappensAfterRelation(other);
    }

    public boolean directHappensBefore(StaticVectorClock other){
        return other.allHappensAfter.contains(this);
    }

    public int getLatestHappensAfterSize(){
        int sum = 0;
        for(Set<StaticVectorClock> svcs : latestHappensAfter.values())
            sum += svcs.size();
        return sum;
    }

    public boolean addAllHappensAfterRelation(StaticVectorClock other){
        allHappensAfterThreadWise.get(other.getThreadLattice()).add(other);
        return allHappensAfter.add(other);
    }

    public Iterator<Set<StaticVectorClock>> iterLatest(){
        return latestHappensAfter.values().iterator();
    }

    public Iterator<StaticVectorClock> iterAll(){
        return allHappensAfter.iterator();
    }

    public void setTemporaryOrder(int temporaryOrder){
        this.temporaryOrder = temporaryOrder;
    }
    interface MinimizationFunction {
        Set<StaticVectorClock> minimize(List<StaticVectorClock> orderedInput);
    }
    public void minimize(MinimizationFunction minimizationFunction){
        for(ThreadLattice threadLattice : latestHappensAfter.keySet()){
            List<StaticVectorClock> svcs = new ArrayList<>(latestHappensAfter.get(threadLattice));
            Collections.sort(svcs);
            latestHappensAfter.put(threadLattice, minimizationFunction.minimize(svcs));
        }
    }
}

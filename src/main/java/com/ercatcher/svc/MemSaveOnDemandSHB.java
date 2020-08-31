package com.ercatcher.svc;

import com.ercatcher.LOG;
import com.ercatcher.ConcurrencyAnalysis.C3G.C3GManager;
import com.ercatcher.ConcurrencyAnalysis.ThreadLattice;
import soot.toolkits.scalar.Pair;

import java.util.*;

public class MemSaveOnDemandSHB extends AbstractOnDemandSHB{
    private final int DURING_PASS_MINIMIZE_THRESHOLD = 10;
    private final int BEFORE_ITER_MINIMIZE_THRESHOLD = 30;
    public MemSaveOnDemandSHB(C3GManager c3GManager){
        super(c3GManager);
        LOG.logln("Using Memory Saving SHB", LOG.ESSENTIAL);
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
        return first.directHappensBefore(second);
    }

    @Override
    protected void updateSVCs(StaticVectorClock currentSVC, Map<ThreadLattice, Set<StaticVectorClock>> myHAInvokers) {
        if (currentSVC.getThreadLattice().isEventQueue())
            if (currentSVC instanceof InvokerSVC)
                for (StaticVectorClock haI : myHAInvokers.get(currentSVC.getThreadLattice()))
                    currentSVC.addHappensAfterRelation(haI);
    }

    @Override
    protected void addHBRelation(StaticVectorClock first, StaticVectorClock second) {
        second.addHappensAfterRelation(first);
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

    static class SVCPair extends Pair<StaticVectorClock, StaticVectorClock>{
        public SVCPair(StaticVectorClock o1, StaticVectorClock o2) {
            super(o1, o2);
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + getO1().hashCode();
            result = prime * result + getO2().hashCode();
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            SVCPair other = (SVCPair) obj;
            return this.getO1().equals(other.getO1()) && this.getO1().equals(other.getO2());
        }
    }
    private Map<SVCPair, StaticHappensRelation> myCache = new HashMap<>();
    @Override
    public boolean happensBefore(StaticVectorClock first, StaticVectorClock second) {
        SVCPair myPair = new SVCPair(first, second);
        if(myCache.containsKey(myPair)){
            return myCache.get(myPair).equals(StaticHappensRelation.BEFORE);
        }
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
        if(isComputationFinished()){
            for (StaticVectorClock svc : newlyAdded) {
                second.addAllHappensAfterRelation(svc);
                myCache.put(new SVCPair(svc, second), StaticHappensRelation.BEFORE);
                myCache.put(new SVCPair(second, svc), StaticHappensRelation.AFTER);
            }
            if (result) {
                second.addAllHappensAfterRelation(first);
                myCache.put(myPair, StaticHappensRelation.BEFORE);
                myCache.put(new SVCPair(second, first), StaticHappensRelation.AFTER);
            } else{
                myCache.put(myPair, StaticHappensRelation.INDETERMINATE);
            }
        }
        return result;

    }

    @Override
    public boolean reaches(StaticVectorClock first, StaticVectorClock second) {
        return false;
    }

//    @Override
//    protected Set<StaticVectorClock> minimize(List<StaticVectorClock> orderedInput){
//        Set<StaticVectorClock> newSet = new HashSet<>();
//        Set<StaticVectorClock> shouldBeRemoved = new HashSet<>();
//        for(int i=0; i< orderedInput.size(); i++) {
//            StaticVectorClock first = orderedInput.get(i);{
//                if (shouldBeRemoved.contains(first))
//                    continue;
//                for(int j=orderedInput.size()-1; j>i ; j--) {
//                    StaticVectorClock second = orderedInput.get(j);
//                    if (shouldBeRemoved.contains(second))
//                        continue;
//                    if(quickHB(first, second)){
//                        shouldBeRemoved.add(first);
//                        break;
//                    }
////                    if(happensBefore(first, second)){
////                        shouldBeRemoved.add(first);
////                        break;
////                    }
//                }
//            }
//        }
//        for(StaticVectorClock svc : orderedInput)
//            if(!shouldBeRemoved.contains(svc))
//                newSet.add(svc);
//        return newSet;
//    }
//
//    @Override
//    public void computeSVC() {
//        boolean update = true;
//        int i = 0;
//        while(update) {
//            // TODO: tof
//            if(i > 10)
//                break;
//            int sum = 0;
//            for(int j=0; j < svcMap.size(); j++)
//                sum += svcMap.get(j).getLatestHappensAfterSize();
//            LOG.logln(String.format("Iter: %d Sum: %d Average: %f", i++, sum, ((float)sum)/svcMap.size()), LOG.VERBOSE);
//
//            update = false;
//            update = sameThreadByTopologicalOrdering(update);
////            sum = 0;
////            for(int j=0; j < svcMap.size(); j++)
////                sum += svcMap.get(j).getLatestHappensAfterSize();
////            System.out.println("Before minimzation " + + sum + " " + ((float)sum)/svcMap.size());
////            for (StaticVectorClock svc : ordered)
////                svc.minimize(this::minimize);
//////            System.out.println("Minimization has been completed.");
////            sum = 0;
////            for(int j=0; j < svcMap.size(); j++)
////                sum += svcMap.get(j).getLatestHappensAfterSize();
////            System.out.println("After minimzation " + + sum + " " + ((float)sum)/svcMap.size());
////            System.out.println("-----------");
//        }
//        LOG.logln("HB is created", LOG.SUPER_VERBOSE);
//        int hbEdges = 0;
////        for(SVCEvent first : cventSVCEventMap.values()){
////            for(SVCEvent second : cventSVCEventMap.values()){
////                if(happensBefore(first, second)) {
////                    LOG.logln(String.format("%s -----> %s", first, second), LOG.SUPER_VERBOSE);
////                    hbEdges++;
////                }
////            }
////        }
//        LOG.logln(String.format("Edges HB: %d, Possible Edges: %d", hbEdges,  (cventSVCEventMap.size()*(cventSVCEventMap.size()-1)/2)), LOG.VERBOSE);
//    }
//
//    private boolean sameThreadByTopologicalOrdering(boolean update) {
//        DirectedGraph<StaticVectorClock> dg = getDirectedGraph();
//        PseudoTopologicalOrderer<StaticVectorClock> topologicalOrderer = new PseudoTopologicalOrderer<>();
//        List<StaticVectorClock> ordered = topologicalOrderer.newList(dg, false);
//        Map<StaticVectorClock, Map<ThreadLattice, Set<StaticVectorClock>>> haInvokers = new HashMap<>();
//        for (int i1 = 0; i1 < ordered.size(); i1++) {
//            StaticVectorClock svc = ordered.get(i1);
//            svc.setTemporaryOrder(i1);
//            haInvokers.put(svc, new HashMap<>());
//            for (ThreadLattice threadLattice : allThreads) {
//                haInvokers.get(svc).put(threadLattice, new HashSet<>());
//            }
//        }
//        LOG.logln("Ordered list has been built.", LOG.SUPER_VERBOSE);
//
//        int c = 0;
//        for (StaticVectorClock svc : ordered) {
//            c++;
//            if(c % 100 == 0) {
//                LOG.log(String.format("%d-",c), LOG.SUPER_VERBOSE);
//            }
//            Map<ThreadLattice, Set<StaticVectorClock>> myHAInvokers = haInvokers.get(svc);
//            if (svc.getThreadLattice().isEventQueue()) {
//                if (svc instanceof InvokerSVC) {
//                    for (StaticVectorClock haI : myHAInvokers.get(svc.getThreadLattice())) {
//                        // TODO add some assertions
//                        svc.addAllHappensAfterRelation(haI);
//                        StaticVectorClock svcEventStart = ((InvokerSVC) svc).target.start;
//                        StaticVectorClock haIEventEnd = ((InvokerSVC) haI).target.end;
//                        if (quickHB(haIEventEnd, svcEventStart)) {
//                            continue;
//                        }
//                        svcEventStart.addHappensAfterRelation(haIEventEnd);
////                            happensBefore(haIEventEnd, svcEventStart);
//                        if(!tooBig) {
//                            happensBeforeMat[svcEventStart.id][haIEventEnd.id] = HAFTER;
//                            happensBeforeMat[haIEventEnd.id][svcEventStart.id] = HBEFORE;
//                        }
//                        update = true;
//
//                    }
//                    myHAInvokers.put(svc.getThreadLattice(), new HashSet<>(Collections.singleton(svc)));
//                }
//            }
//            for (StaticVectorClock childSVC : dg.getSuccsOf(svc)) {
//                for (ThreadLattice threadLattice : allThreads) {
//                    haInvokers.get(childSVC).get(threadLattice).addAll(myHAInvokers.get(threadLattice));
//                    haInvokers.get(childSVC).put(threadLattice, minimize(new ArrayList<>(haInvokers.get(childSVC).get(threadLattice))));
//                }
//            }
//            // Memory Cleaning
//            haInvokers.put(svc, null);
//        }
//        LOG.logln("SameThread Pass has been completed.", LOG.SUPER_VERBOSE);
//        return update;
//    }
////        while(update) {
////            update = orderSameThreadEvents();
//////            System.out.println("Iter " + i++);
////            int sum = 0;
////            for(int j=0; j < svcMap.size(); j++)
////                sum += svcMap.get(j).getLatestHappensAfterSize();
////            System.out.println("Iter " + i++ + " " + sum + " " + ((float)sum)/svcMap.size());
////        }
//
//
//    public boolean quickHB(StaticVectorClock first, StaticVectorClock second){
//        if(first == second)
//            return false;
//        if(first == null || second == null)
//            return false;
//        if(tooBig)
//            return first.directHappensBefore(second);
//        if(happensBeforeMat[first.id][second.id] == HAFTER)
//            return false;
//        if(happensBeforeMat[first.id][second.id] == HBEFORE)
//            return true;
//        return false;
//    }
//
//    public boolean orderSameThreadEvents(){
//        boolean update = false;
//        for(SVCEvent first : cventSVCEventMap.values()){
////            System.out.print(first + " ");
//            if(first.getThreadLattice().isEventQueue())
//                continue;
//            for(SVCEvent second : cventSVCEventMap.values()){
//                if(second == first)
//                    continue;
//                if(first.getThreadLattice() != second.getThreadLattice())
//                    continue;
//                if(quickHB(first.end, second.start))
//                    continue;
//                if(happensBefore(first.invoke, second.invoke)) {
//                    second.start.addHappensAfterRelation(first.end);
//                    happensBefore(first.end, second.start);
//                    update = true;
//                }
//            }
//        }
//        return update;
//    }
//
//    private boolean internalHappensBefore(StaticVectorClock first, StaticVectorClock second) {
//        if(quickHB(first, second))
//            return true;
//        List<StaticVectorClock> queue = new ArrayList<>();
//        Set<StaticVectorClock> hasBeenAddedToQueue = new HashSet<>();
//        queue.add(second);
//        hasBeenAddedToQueue.add(second);
//        boolean result = false;
//        boolean flag = false;
//        List<StaticVectorClock> newlyAdded = new ArrayList<>();
//        for(int i=0; i< queue.size(); i++){
//            StaticVectorClock svc = queue.get(i);
//            for(Iterator<StaticVectorClock> it = svc.iterAll(); it.hasNext(); ) {
//                StaticVectorClock haNode = it.next();
//                    if (hasBeenAddedToQueue.contains(haNode))
//                        continue;
//                    if(svc != second)
//                        newlyAdded.add(haNode);
//                    if (haNode == first) {
//                        result = true;
//                        flag = true;
//                        break;
//                    }
//                    queue.add(haNode);
//                    hasBeenAddedToQueue.add(haNode);
//                }
//            if(flag)
//                break;
//        }
//        if(!tooBig) {
//            for (StaticVectorClock svc : newlyAdded) {
//                second.addAllHappensAfterRelation(svc);
//                // Matrix
//                happensBeforeMat[second.id][svc.id] = HAFTER;
//                happensBeforeMat[svc.id][second.id] = HBEFORE;
//            }
//            if (result) {
//                happensBeforeMat[second.id][first.id] = HAFTER;
//                happensBeforeMat[first.id][second.id] = HBEFORE;
//            }
//        }
//        return result;
//
//    }
//
//    @Override
//    public boolean happensBefore(StaticVectorClock first, StaticVectorClock second) {
//        return internalHappensBefore(first, second);
//    }
//
//    @Override
//    public boolean reaches(StaticVectorClock first, StaticVectorClock second) {
//        return false;
//    }

}

package com.ercatcher.svc;

import com.ercatcher.LOG;
import com.ercatcher.ConcurrencyAnalysis.AbstractNode;
import com.ercatcher.ConcurrencyAnalysis.C3G.C3GTask;
import com.ercatcher.ConcurrencyAnalysis.C3G.C3GManager;
import com.ercatcher.ConcurrencyAnalysis.C3G.SNode;
import com.ercatcher.ConcurrencyAnalysis.ThreadLattice;
import soot.toolkits.graph.DirectedGraph;
import soot.toolkits.scalar.Pair;

import java.util.*;

public abstract class AbstractSHB implements StaticHappensBefore {

    private boolean isComputationFinished = false;
    Set<ThreadLattice> allThreads;
    Map<C3GTask, SVCEvent> cventSVCEventMap = new HashMap<>();
    Map<Integer, StaticVectorClock> svcMap = new HashMap<>();
    C3GManager c3GManager;
    Map<SNode, Set<SNode>> cacheSNode = new HashMap<>();
    private Map<SNode, StaticVectorClock> sNodeToSVC = new HashMap<>();
    private SVCEvent root;
    private InvokerSVC rootInvokerSVC;

    public AbstractSHB(C3GManager c3GManager){
        LOG.startTimer("SHB Constructor", LOG.SUPER_VERBOSE);
        StaticVectorClock.SVCCount = 0;
        this.c3GManager = c3GManager;
        this.allThreads = new HashSet<>(this.c3GManager.getAllThread());
        LOG.startTimer("SVCEvent Creation", LOG.SUPER_VERBOSE);
        for(C3GTask c3GTask : c3GManager.getAllC3GTasks()){
            SVCEvent svcEvent = new SVCEvent(c3GTask, allThreads);
            cventSVCEventMap.put(c3GTask, svcEvent);
        }

        for(C3GTask c3GTask : c3GManager.getAllC3GTasks()){
            SVCEvent svcEvent = cventSVCEventMap.get(c3GTask);
            svcEvent.initBody(cventSVCEventMap, this.allThreads);
        }
        LOG.endTimer("SVCEvent Creation", LOG.SUPER_VERBOSE);
        LOG.startTimer("SVC Init", LOG.SUPER_VERBOSE);
        this.root = cventSVCEventMap.get(c3GManager.getRootC3GTask());
        rootInvokerSVC = new InvokerSVC(null, this.allThreads);
        this.root.setInvokeSVC(rootInvokerSVC);
        for(C3GTask c3GTask : c3GManager.getAllC3GTasks()){
            SVCEvent svcEvent = cventSVCEventMap.get(c3GTask);
            svcEvent.start.addHappensAfterRelation(svcEvent.invoke);
        }
        for(C3GTask c3GTask : c3GManager.getAllC3GTasks()){
            SVCEvent svcEvent = cventSVCEventMap.get(c3GTask);
            svcMap.put(svcEvent.start.id, svcEvent.start);
            svcMap.put(svcEvent.end.id, svcEvent.end);
            svcMap.put(svcEvent.invoke.id, svcEvent.invoke);
            for(SNode sNode : svcEvent.sNodeToSVC.keySet())
                sNodeToSVC.put(sNode, svcEvent.sNodeToSVC.get(sNode));
        }
        LOG.endTimer("SVC Init", LOG.SUPER_VERBOSE);
        LOG.endTimer("SHB Constructor", LOG.SUPER_VERBOSE);
    }

    @Override
    public boolean isComputationFinished() {
        return isComputationFinished;
    }

    protected abstract Set<StaticVectorClock> minimize(List<StaticVectorClock> orderedInput);

    public final void computeSVC(){
        internalComputeSVC();
        isComputationFinished = true;
    }

    public abstract void internalComputeSVC();

    protected DirectedGraph<StaticVectorClock> getDirectedGraph(){
        Map<StaticVectorClock, Set<StaticVectorClock>> outgoingEdges = new HashMap<>();
        Map<StaticVectorClock, Set<StaticVectorClock>> incomingEdges = new HashMap<>();
        for(StaticVectorClock svc : svcMap.values()) {
            outgoingEdges.put(svc, new HashSet<>());
            incomingEdges.put(svc, new HashSet<>());
        }
        for(StaticVectorClock svc : svcMap.values()){
            for (Iterator<Set<StaticVectorClock>> it = svc.iterLatest(); it.hasNext(); ) {
                Set<StaticVectorClock> childSet = it.next();
                for(StaticVectorClock haNode : childSet) {
                    outgoingEdges.get(haNode).add(svc);
                    incomingEdges.get(svc).add(haNode);
                }
            }
        }
        List<StaticVectorClock> heads = new ArrayList<>();
        List<StaticVectorClock> tails = new ArrayList<>();
        for(StaticVectorClock svc : svcMap.values()){
            if(incomingEdges.get(svc).size() == 0)
                heads.add(svc);
            if(outgoingEdges.get(svc).size() == 0)
                tails.add(svc);
        }
        return new DirectedGraph<StaticVectorClock>() {
            @Override
            public List<StaticVectorClock> getHeads() {
                return heads;
            }

            @Override
            public List<StaticVectorClock> getTails() {
                return tails;
            }

            @Override
            public List<StaticVectorClock> getPredsOf(StaticVectorClock staticVectorClock) {
                return new ArrayList<>(incomingEdges.get(staticVectorClock));
            }

            @Override
            public List<StaticVectorClock> getSuccsOf(StaticVectorClock staticVectorClock) {
                return new ArrayList<>(outgoingEdges.get(staticVectorClock));
            }

            @Override
            public int size() {
                return outgoingEdges.size();
            }

            @Override
            public Iterator<StaticVectorClock> iterator() {
                return svcMap.values().iterator();
            }
        };
    }


    @Override
    public boolean happensBefore(SNode first, SNode second) {
        if(isComputationFinished()){
            if(first.equals(second))
                return true;
            if(!cacheSNode.containsKey(first))
                cacheSNode.put(first, new HashSet<>());
            else
                if(cacheSNode.get(first).contains(second))
                    return false;
        }
        boolean result = happensBefore(sNodeToSVC.get(first), sNodeToSVC.get(second));
//        if(isComputationFinished() && !result)
//            cacheSNode.get(first).add(second);
        return result;
    }

    @Override
    public boolean happensBefore(C3GTask first, C3GTask second) {
        return happensBefore(cventSVCEventMap.get(first),cventSVCEventMap.get(second));
    }

    Set<Pair<StaticVectorClock, StaticVectorClock>> getStartEndMayCaller(SNode sNode){
        Set<Pair<StaticVectorClock, StaticVectorClock>> pairSet = new HashSet<>();
        for(AbstractNode parent : sNode.getParents()){
            SNode sParent = (SNode)parent;
            pairSet.add(new Pair<>(sNodeToSVC.get(sParent), sNodeToSVC.get(sNode)));
        }
        return pairSet;
    }


    @Override
    public boolean happensBefore(SVCEvent first, SVCEvent second) {
        return happensBefore(first.end, second.start);
    }

}

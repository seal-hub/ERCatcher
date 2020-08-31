package com.ercatcher.svc;

import com.ercatcher.LOG;
import com.ercatcher.ConcurrencyAnalysis.C3G.C3GManager;
import com.ercatcher.ConcurrencyAnalysis.ThreadLattice;
import soot.toolkits.graph.DirectedGraph;
import soot.toolkits.graph.PseudoTopologicalOrderer;

import java.util.*;
import java.util.concurrent.*;

public abstract class AbstractOnDemandSHB extends AbstractSHB{
    public AbstractOnDemandSHB(C3GManager c3GManager){
        super(c3GManager);
    }

    @Override
    protected Set<StaticVectorClock> minimize(List<StaticVectorClock> orderedInput){
        Set<StaticVectorClock> newSet = new HashSet<>();
        Set<StaticVectorClock> shouldBeRemoved = new HashSet<>();
        for(int i=0; i< orderedInput.size(); i++) {
            StaticVectorClock first = orderedInput.get(i);{
                if (shouldBeRemoved.contains(first))
                    continue;
                for(int j=orderedInput.size()-1; j>i ; j--) {
                    StaticVectorClock second = orderedInput.get(j);
                    if (shouldBeRemoved.contains(second))
                        continue;
                    if(quickHB(first, second)){
                        shouldBeRemoved.add(first);
                        break;
                    }
                }
            }
        }
        for(StaticVectorClock svc : orderedInput)
            if(!shouldBeRemoved.contains(svc))
                newSet.add(svc);
        return newSet;
    }

    @Override
    public void internalComputeSVC() {
        boolean update = true;


        update = sameThreadByTopologicalOrdering();

        int i = 0;
        ExecutorService executor = Executors.newCachedThreadPool();
        while(update) {
//            TODO: configurable
            if(i > 5)
                break;
            int sum = 0;
            for(int j=0; j < svcMap.size(); j++)
                sum += svcMap.get(j).getLatestHappensAfterSize();
            LOG.logln(String.format("Iter: %d Sum: %d Average: %f", i++, sum, ((float)sum)/svcMap.size()), LOG.VERBOSE);

            // Minimize SVCs
            Future futureMinimize = executor.submit(this::minimizeBeforeIter);
            try {
                futureMinimize.get(60, TimeUnit.SECONDS); // TODO: configurable
            } catch (Exception ex) {
                LOG.logln(String.format("Minimize TIMEOUT - %s", ex.getMessage()), LOG.ERROR);
            }
            futureMinimize.cancel(true); // may or may not desire this
            // Apply SameThread Rule using an Executor
            Future<Boolean> futureSameThreadOrder = executor.submit(this::sameThreadByTopologicalOrdering);
            update = false;
            try {
                update = futureSameThreadOrder.get(1200, TimeUnit.SECONDS); // TODO: configurable
            } catch (Exception ex) {
                LOG.logln(String.format("SameThreadOrder TIMEOUT - %s", ex.getMessage()), LOG.ERROR);
            }
            futureSameThreadOrder.cancel(true); // may or may not desire this
        }
        executor.shutdown();
        // Final Ordering for fast query
        DirectedGraph<StaticVectorClock> dg = getDirectedGraph();
        PseudoTopologicalOrderer<StaticVectorClock> topologicalOrderer = new PseudoTopologicalOrderer<>();
        List<StaticVectorClock> ordered = topologicalOrderer.newList(dg, false);
        for (int i1 = 0; i1 < ordered.size(); i1++) {
            StaticVectorClock svc = ordered.get(i1);
            svc.setTemporaryOrder(i1);
        }
        LOG.logln("Ordered list has been built.", LOG.SUPER_VERBOSE);
        // ----------------
        LOG.logln("HB is created", LOG.SUPER_VERBOSE);
//        int hbEdges = 0;
//        LOG.logln(String.format("Edges HB: %d, Possible Edges: %d", hbEdges,  (cventSVCEventMap.size()*(cventSVCEventMap.size()-1)/2)), LOG.VERBOSE);
    }

    private boolean sameThreadByTopologicalOrdering() {
        // Make Ordered list of nodes
        DirectedGraph<StaticVectorClock> dg = getDirectedGraph();
        PseudoTopologicalOrderer<StaticVectorClock> topologicalOrderer = new PseudoTopologicalOrderer<>();
        List<StaticVectorClock> ordered = topologicalOrderer.newList(dg, false);
        for (int i1 = 0; i1 < ordered.size(); i1++) {
            StaticVectorClock svc = ordered.get(i1);
            svc.setTemporaryOrder(i1);
        }
        LOG.logln("Ordered list has been built.", LOG.SUPER_VERBOSE);
        boolean update = false;

        Map<StaticVectorClock, Map<ThreadLattice, Set<StaticVectorClock>>> haInvokers = new HashMap<>();
        for (StaticVectorClock svc : ordered) {
            haInvokers.put(svc, new HashMap<>());
            for (ThreadLattice threadLattice : allThreads) {
                haInvokers.get(svc).put(threadLattice, new HashSet<>());
            }
        }
        int c = 0;
        for (StaticVectorClock svc : ordered) {
            if(Thread.currentThread().isInterrupted())
                break;
            c++;
            if(c % 100 == 0) {
                LOG.log(String.format("%d-",c), LOG.SUPER_VERBOSE);
            }
            Map<ThreadLattice, Set<StaticVectorClock>> myHAInvokers = haInvokers.get(svc);
            updateSVCs(svc, myHAInvokers);
            if (svc.getThreadLattice().isEventQueue()) {
                if (svc instanceof InvokerSVC) {
                    if(!((InvokerSVC) svc).hasNegativeDelay()) {
                        for (StaticVectorClock haI : myHAInvokers.get(svc.getThreadLattice())) {
                            // TODO add some assertions
                            StaticVectorClock svcEventStart = ((InvokerSVC) svc).target.start;
                            StaticVectorClock haIEventEnd = ((InvokerSVC) haI).target.end;
                            if (quickHB(haIEventEnd, svcEventStart)) {
                                continue;
                            }
                            addHBRelation(haIEventEnd, svcEventStart);
                            update = true;
                        }
                    }
                    if(!((InvokerSVC) svc).hasPositiveDelay())
                        myHAInvokers.put(svc.getThreadLattice(), new HashSet<>(Collections.singleton(svc)));
                }
            }
            for (StaticVectorClock childSVC : dg.getSuccsOf(svc)) {
                for (ThreadLattice threadLattice : allThreads) {
                    haInvokers.get(childSVC).get(threadLattice).addAll(myHAInvokers.get(threadLattice));
                    minimizeDuringPass(haInvokers, childSVC, threadLattice);
                }
            }
            // Memory Cleaning
            haInvokers.put(svc, null);
        }
        LOG.logln("SameThread Pass has been completed.", LOG.SUPER_VERBOSE);
        return update;
    }
    protected abstract boolean quickHB(StaticVectorClock first, StaticVectorClock second);
    protected abstract void updateSVCs(StaticVectorClock currentSVC, Map<ThreadLattice, Set<StaticVectorClock>> myHAInvokers);
    protected abstract void addHBRelation(StaticVectorClock first, StaticVectorClock second);
    protected abstract void minimizeDuringPass(Map<StaticVectorClock, Map<ThreadLattice, Set<StaticVectorClock>>> haInvokers, StaticVectorClock child, ThreadLattice threadLattice);
    protected abstract void minimizeBeforeIter();

}

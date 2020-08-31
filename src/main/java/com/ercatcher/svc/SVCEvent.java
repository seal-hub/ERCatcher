package com.ercatcher.svc;

import com.ercatcher.ConcurrencyAnalysis.AbstractNode;
import com.ercatcher.ConcurrencyAnalysis.C3G.C3GTask;
import com.ercatcher.ConcurrencyAnalysis.C3G.SInvokeNode;
import com.ercatcher.ConcurrencyAnalysis.C3G.SNode;
import com.ercatcher.ConcurrencyAnalysis.ThreadLattice;
import soot.toolkits.scalar.Pair;

import java.util.*;

public class SVCEvent {
    OtherSVC start;
    OtherSVC end;
    InvokerSVC invoke;
    C3GTask source;
    Set<InvokerSVC> body = new HashSet<>();
    Map<SNode, StaticVectorClock> sNodeToSVC = new HashMap<>();
    SVCEvent(C3GTask source, Set<ThreadLattice> allThreads){
        this.source = source;
        start = new OtherSVC(this, allThreads, true);
        end = new OtherSVC(this, allThreads, false);
    }

    void initBody(Map<C3GTask, SVCEvent> cventToSVCEventMap, Set<ThreadLattice> allThreads){
        List<SNode> sQueue = new ArrayList<>();
        Set<SNode> visited = new HashSet<>();
        sQueue.add(source.getStart());
        sNodeToSVC.put(source.getStart(), start);
        sNodeToSVC.put(source.getEnd(), end);

        List<Pair<SInvokeNode, InvokerSVC>> addedNodes = new ArrayList<>();
        for(int i=0; i< sQueue.size(); i++){
            SNode current = sQueue.get(i);
            if(visited.contains(current))
                continue;
            visited.add(current);
            if(current instanceof  SInvokeNode){
                SInvokeNode sInvokeNode = (SInvokeNode) current;
                C3GTask target =  (C3GTask) sInvokeNode.getTarget();
                InvokerSVC invokerSVC = new InvokerSVC(this, allThreads);
                invokerSVC.setDelay(sInvokeNode.getDelay());
                cventToSVCEventMap.get(target).setInvokeSVC(invokerSVC);
                sNodeToSVC.put(sInvokeNode, invokerSVC);
                addedNodes.add(new Pair<>(sInvokeNode,invokerSVC));
            }
            for(AbstractNode child : current.getChildren())
                sQueue.add((SNode) child);
        }
        end.addHappensAfterRelation(start);
        body = new HashSet<>();
        for(Pair<SInvokeNode, InvokerSVC> p : addedNodes){
            SInvokeNode sInvokeNode = p.getO1();
            InvokerSVC iSVC = p.getO2();
            iSVC.addHappensAfterRelation(start);
            end.addHappensAfterRelation((iSVC));
            for(AbstractNode parent : sInvokeNode.getParents()){
                SNode sParent = (SNode) parent;
                iSVC.addHappensAfterRelation(sNodeToSVC.get(sParent));
            }
        }
    }

    void setInvokeSVC(InvokerSVC invokerSVC){
        this.invoke = invokerSVC;
        invokerSVC.setTarget(this);
    }

    Pair<StaticVectorClock, StaticVectorClock> getStartEndBody(){
        return new Pair<>(start, end);
    }

    Pair<StaticVectorClock, StaticVectorClock> getStartEndContext(){
        return new Pair<>(invoke, start);
    }

    Pair<StaticVectorClock, StaticVectorClock> getStartEndMayCaller(){
        return new Pair<>(start, end);
    }

    @Override
    public String toString() {
        return "SVCE-"+source.toString();
    }

    public ThreadLattice getThreadLattice(){
        return source.getThreadLattice();
    }
}

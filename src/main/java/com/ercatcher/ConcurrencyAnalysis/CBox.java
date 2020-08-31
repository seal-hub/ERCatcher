package com.ercatcher.ConcurrencyAnalysis;

import com.ercatcher.LOG;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class CBox<T extends AbstractNode, S> {
    protected T start;
    protected T end;
    private S source;
    protected List<T> allNodes;
    public T getStart(){
        return start;
    }
    public T getEnd(){
        return end;
    }
    public S getSource(){
        return source;
    }
    protected CBox(S source){
        this.source = source;
    }

    public Set<T> getAllNodes(){
        return new HashSet<>(allNodes);
    }

    protected void destroy(){
        source = null;
    }

    protected void setAllNodes() {
        List<T> uQueue = new ArrayList<>();
        Set<T> visited;
        uQueue.add(start);
        visited = new HashSet<>();
        allNodes = new ArrayList<>();
        for(int i=0; i< uQueue.size(); i++){
            T current = uQueue.get(i);
            if(visited.contains(current))
                continue;
            visited.add(current);
            allNodes.add(current);
            for(Object child : current.getChildren()) {
                T tChild = (T) child;
                uQueue.add(tChild);
            }
            if(current.getChildren().size() == 0 && !current.equals(end))
                current.addChild(end);
        }
        if(!allNodes.contains(end))
            allNodes.add(end);
        if(start.haveCycle()) {
            LOG.logln("It seems a cycle is existed", LOG.ERROR);
            start.getChildren().clear();
            end.getParents().clear();
            for(T node : allNodes){
                if(node.equals(start) || node.equals(end))
                    continue;
                node.parents.clear();
                node.children.clear();
                start.addChild(node);
                node.addChild(end);
            }
//            throw new RuntimeException("Why?");
        }
    }
    public boolean isEmpty(){
        return allNodes.size() <= 2;
    }
}

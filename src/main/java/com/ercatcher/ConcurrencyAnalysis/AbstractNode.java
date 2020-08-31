package com.ercatcher.ConcurrencyAnalysis;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class AbstractNode <T extends AbstractNode, S> {
    private static int created = 0;
    private static int deleted = 0;
    protected Set<T> children = new HashSet<>();
    protected Set<T> parents = new HashSet<>();
    protected String title = null;
    private S sourceNode;
    protected AbstractNode(S correspondingNode){
        created += 1;
        this.sourceNode = correspondingNode;
    }

    public Set<T> getParents() {
        return new HashSet<>(parents);
    }

    @Override
    public void finalize() {
        deleted += 1;
    }
    public S getSource(){
        return sourceNode;
    }
    public void changeSource(S correspondingNode){
        this.sourceNode = correspondingNode;
    }
    public Set<T> getChildren(){
        return new HashSet<>(children);
    }
    public void addChild(T child){
        children.add(child);
        T thisT = (T) this;
        child.parents.add(thisT);
    }

    public void removeMySelf(){
        List<T> copyChildren = new ArrayList<>(children);
        List<T> copyParent = new ArrayList<>(parents);
        for (T child : copyChildren)
            child.parents.remove(this);
        for(T parent : copyParent) {
            parent.children.remove(this);
            for (T child : copyChildren) {
                parent.addChild(child);

            }
        }
        // TODO: make children and parents null
    }

    public void destroy(){
         children = null;
         parents = null;
         sourceNode = null;
    }


    public boolean haveCycle(){
        return this.haveCycle(new HashSet<>(), new HashSet<>());
    }

    protected boolean haveCycle(Set<T> visited, Set<T> working){
        T thisT = (T) this;
        if(working.contains(thisT))
            return true;
        working.add(thisT);
        for(T child : getChildren()){
            if(!visited.contains(child) && child.haveCycle(visited, working))
                return true;
        }
        working.remove(thisT);
        visited.add(thisT);
        return false;
    }

}

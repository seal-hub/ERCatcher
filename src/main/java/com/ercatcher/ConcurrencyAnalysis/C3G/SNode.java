package com.ercatcher.ConcurrencyAnalysis.C3G;

import com.ercatcher.ConcurrencyAnalysis.AbstractNode;
import com.ercatcher.ConcurrencyAnalysis.C2G.CNode;
import com.ercatcher.ConcurrencyAnalysis.C2G.InterMethodBox;

import java.util.*;

public class SNode extends AbstractNode<SNode, CNode> {
//    public Set<InterMethodBox> mayBeCalledBeforeMe = new HashSet<>();
    public Map<SNode, Set<InterMethodBox>> mayBeCalledBeforeMeMap = new HashMap<>();
    private C3GTask myC3GTask = null;

    protected SNode(CNode correspondingNode) {
        super(correspondingNode);
    }

    public static SNode newStart(CNode correspondingNode){
        SNode ret = new SNode(correspondingNode);
        ret.title = "S-Start";
        return ret;
    }

    public static SNode newEnd(CNode correspondingNode){
        SNode ret = new SNode(correspondingNode);
        ret.title = "S-End";
        return ret;
    }

    public C3GTask getMyC3GTask() {
        return myC3GTask;
    }

    void setMyC3GTask(C3GTask myC3GTask) {
        this.myC3GTask = myC3GTask;
    }

    @Override
    public String toString() {
        if(title != null) {
            if(myC3GTask != null)
            return title + "-"+ myC3GTask;
        }
        if(getSource() == null)
            return "S-N";
        return "S-"+getSource().toString();
    }

    @Override
    public void removeMySelf(){
        for (AbstractNode child : children) {
            SNode sChild = (SNode) child;
//            sChild.mayBeCalledBeforeMe.addAll(this.mayBeCalledBeforeMe);
            for(SNode parent : mayBeCalledBeforeMeMap.keySet()){
                if(parent == null)
                    throw new RuntimeException("Why it's null?");
                if(!sChild.mayBeCalledBeforeMeMap.containsKey(parent))
                    sChild.mayBeCalledBeforeMeMap.put(parent, new HashSet<>());
                sChild.mayBeCalledBeforeMeMap.get(parent).addAll(this.mayBeCalledBeforeMeMap.get(parent));
            }
            if(sChild.mayBeCalledBeforeMeMap.containsKey(this)){
                Set<InterMethodBox> myChildIMCalled = sChild.mayBeCalledBeforeMeMap.remove(this);
                for(AbstractNode myParent : getParents()){
                    SNode mySParent = (SNode) myParent;
                    if(!sChild.mayBeCalledBeforeMeMap.containsKey(mySParent))
                        sChild.mayBeCalledBeforeMeMap.put(mySParent, new HashSet<>());
                    sChild.mayBeCalledBeforeMeMap.get(mySParent).addAll(myChildIMCalled);
                }
            }
        }
        for (AbstractNode child : children) {
            SNode sChild = (SNode) child;
            if(sChild.mayBeCalledBeforeMeMap.containsKey(this))
                throw new RuntimeException("Why I'm still here?");
        }
            super.removeMySelf();
    }
}

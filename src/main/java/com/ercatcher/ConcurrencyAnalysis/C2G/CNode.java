package com.ercatcher.ConcurrencyAnalysis.C2G;

import com.ercatcher.ConcurrencyAnalysis.AbstractNode;
import com.ercatcher.ConcurrencyAnalysis.CSF.UNode;

public class CNode extends AbstractNode<CNode, UNode> {
    protected CNode(UNode correspondingNode) {
        super(correspondingNode);
    }

    public InterMethodBox getMyInterMethodBox() {
        return myInterMethodBox;
    }

    public void setMyInterMethodBox(InterMethodBox myInterMethodBox) {
        this.myInterMethodBox = myInterMethodBox;
    }

    private InterMethodBox myInterMethodBox;
    public static CNode newStart(UNode correspondingNode){
        CNode ret = new CNode(correspondingNode);
        ret.title = "C-Start";
        return ret;
    }
    public static CNode newEnd(UNode correspondingNode){
        CNode ret = new CNode(correspondingNode);
        ret.title = "C-End";
        return ret;
    }


    @Override
    public String toString() {
        if(title != null)
            return title;
        if(getSource() == null)
            return "C-N";
        return "C-"+getSource().toString();
    }

    @Override
    public void removeMySelf() {
        super.removeMySelf();
        myInterMethodBox = null;
    }
}

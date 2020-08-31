package com.ercatcher.ConcurrencyAnalysis.CSF;

import com.ercatcher.ConcurrencyAnalysis.AbstractNode;
import com.ercatcher.ConcurrencyAnalysis.NopCaSFGenerator;
import soot.jimple.Stmt;
import soot.jimple.internal.JInvokeStmt;
import soot.jimple.internal.JStaticInvokeExpr;

import java.util.ArrayList;
import java.util.List;

public class UNode extends AbstractNode<UNode, Stmt> {

    protected UNode(Stmt stmt) {
        super(stmt);
    }

    public static UNode newStart(){
        UNode ret = new UNode(null);
        ret.title = "U-Start";
        return ret;
    }
    public static UNode newEnd(){
        UNode ret = new UNode(null);
        ret.title = "U-End";
        return ret;
    }

    public UNode addNopNode(){
        Stmt nopStmt = new JInvokeStmt(new JStaticInvokeExpr(NopCaSFGenerator.nopAsyncMethod.makeRef(), new ArrayList<>())); // TODO: bad design
        UInvokeNode nopInvokeNode = new UInvokeNode(nopStmt, true);
        nopInvokeNode.addTarget(NopCaSFGenerator.nopAsyncMethodBox);
        List<UNode> copyChildren = new ArrayList<>(children);
        for(UNode child : copyChildren){
            child.parents.remove(this);
            this.children.remove(child);
            nopInvokeNode.addChild(child);
        }
        this.addChild(nopInvokeNode);
        return nopInvokeNode;
    }

    @Override
    public String toString() {
        if(title != null)
            return title;
        if(getSource() == null)
            return "U-N";
        return "U-"+getSource().toString();
    }
}

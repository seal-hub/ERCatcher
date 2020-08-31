package com.ercatcher.ConcurrencyAnalysis.C2G;

import com.ercatcher.ConcurrencyAnalysis.CSF.UNode;
import com.ercatcher.ConcurrencyAnalysis.InvokeNode;

public class CInvokeNode extends CNode implements InvokeNode<InterMethodBox> {
    public void setAsyncInvoke(boolean asyncInvoke) {
        this.asyncInvoke = asyncInvoke;
    }

    boolean asyncInvoke;
    InterMethodBox target;
    public CInvokeNode(UNode uNode, InterMethodBox target, boolean asyncInvoke) {
        super(uNode);
        this.target = target;
        this.asyncInvoke = asyncInvoke;
    }

    @Override
    public InterMethodBox getTarget() {
        return target;
    }

    public void setTarget(InterMethodBox target){
        this.target = target;
    }

    @Override
    public boolean isAsync() {
        return asyncInvoke;
    }

    @Override
    public String toString() {
        return "CInvoke-"+asyncInvoke+target.toString();
    }
}

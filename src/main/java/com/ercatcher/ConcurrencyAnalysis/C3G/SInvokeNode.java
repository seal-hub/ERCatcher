package com.ercatcher.ConcurrencyAnalysis.C3G;

import com.ercatcher.ConcurrencyAnalysis.C2G.CInvokeNode;
import com.ercatcher.ConcurrencyAnalysis.InvokeNode;

public class SInvokeNode extends SNode implements InvokeNode<C3GTask> {
    private boolean asyncInvoke;
    private C3GTask target;
    private int delay = 0;
    public int getDelay(){
        return delay ;
    }

    public void setDelay(int delay){
        this.delay = delay;
    }

    public SInvokeNode(CInvokeNode correspondingNode, C3GTask target, boolean asyncInvoke) {
        super(correspondingNode);
        this.target = target;
        this.asyncInvoke = asyncInvoke;
    }

    @Override
    public C3GTask getTarget() {
        return target;
    }

    @Override
    public boolean isAsync() {
        return asyncInvoke;
    }
    @Override
    public String toString() {
        return "SInvoke-"+asyncInvoke+target.toString();
    }

    @Override
    public void destroy() {
        super.destroy();
        target = null;
    }
}

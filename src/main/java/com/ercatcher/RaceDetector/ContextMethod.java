package com.ercatcher.RaceDetector;

import com.ercatcher.ConcurrencyAnalysis.C3G.C3GTask;
import com.ercatcher.ConcurrencyAnalysis.C3G.SNode;
import com.ercatcher.ConcurrencyAnalysis.ThreadLattice;
import soot.SootMethod;

public abstract class ContextMethod {
    private SootMethod method;
    private SNode beforeMethodNode;
    private SNode afterMethodNode;

    public SootMethod getMethod() {
        return method;
    }

    public SNode getBeforeMethodNode() {
        return beforeMethodNode;
    }

    public C3GTask getLastCvent(){
        return beforeMethodNode.getMyC3GTask();
    }

    public ThreadLattice getThread(){
        return getLastCvent().getThreadLattice();
    }

    public SNode getAfterMethodNode() {
        return afterMethodNode;
    }

    public ContextMethod(SootMethod method, SNode beforeMethodNode, SNode afterMethodNode) {
        this.method = method;
        this.beforeMethodNode = beforeMethodNode;
        this.afterMethodNode = afterMethodNode;
    }

    public abstract String getDetailedContextString();

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + method.hashCode();
        result = prime * result + beforeMethodNode.hashCode();
        result = prime * result + afterMethodNode.hashCode();
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ContextMethod other = (ContextMethod) obj;
        return this.method == other.method && this.beforeMethodNode == other.beforeMethodNode && this.afterMethodNode == other.afterMethodNode;
    }

}

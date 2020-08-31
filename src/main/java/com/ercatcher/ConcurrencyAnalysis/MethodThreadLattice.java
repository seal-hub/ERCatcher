package com.ercatcher.ConcurrencyAnalysis;

import soot.SootMethod;

public class MethodThreadLattice extends ThreadLattice {
    public SootMethod getSootMethod() {
        return sootMethod;
    }

    private SootMethod sootMethod;
    public MethodThreadLattice(SootMethod sootMethod){
        this.sootMethod = sootMethod;
    }

    @Override
    public String toString() {
        return "MethodThread: " + sootMethod.getSignature();
    }
}

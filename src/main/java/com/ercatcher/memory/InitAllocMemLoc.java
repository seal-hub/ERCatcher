package com.ercatcher.memory;

import soot.Unit;
import soot.jimple.SpecialInvokeExpr;

public class InitAllocMemLoc extends AllocMemLoc {
    public SpecialInvokeExpr getInitExpr() {
        return initExpr;
    }

    private SpecialInvokeExpr initExpr;
    InitAllocMemLoc(Unit ctxUnit, SpecialInvokeExpr initExpr){
        super(ctxUnit);
        this.initExpr = initExpr;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + initExpr.hashCode();
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if(this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        InitAllocMemLoc other = (InitAllocMemLoc) obj;
        if(this.initExpr.equals(other.initExpr))
            return true;
        return false;
    }
    @Override
    public String toString() {
        String prefix = "ML-INT-";
        if(initExpr == null)
            return prefix+"NULL";
        return prefix+initExpr;
    }
}

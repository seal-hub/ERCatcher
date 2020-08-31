package com.ercatcher.memory;

import soot.Unit;
import soot.jimple.InvokeExpr;

public class InvokeAllocMemLoc extends AllocMemLoc {
    public InvokeExpr getInvokeExpr() {
        return invokeExpr;
    }

    private InvokeExpr invokeExpr;
    InvokeAllocMemLoc(Unit ctxUnit, InvokeExpr invokeExpr){
        super(ctxUnit);
        this.invokeExpr = invokeExpr;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + invokeExpr.hashCode();
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
        InvokeAllocMemLoc other = (InvokeAllocMemLoc) obj;
        if(this.invokeExpr.equals(other.invokeExpr))
            return true;
        return false;
    }

    @Override
    public String toString() {
        String prefix = "ML-Ink-";
        if(invokeExpr == null)
            return prefix+"NULL";
        return prefix+invokeExpr;
    }
}

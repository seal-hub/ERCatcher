package com.ercatcher.memory;

import soot.Unit;
import soot.jimple.AnyNewExpr;

public class NewAllocMemLoc extends AllocMemLoc {
    AnyNewExpr newExpr;
    NewAllocMemLoc(Unit ctxUnit, AnyNewExpr newExpr){
        super(ctxUnit);
        this.newExpr = newExpr;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + newExpr.hashCode();
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
        NewAllocMemLoc other = (NewAllocMemLoc) obj;
        if(this.newExpr.equals(other.newExpr))
            return true;
        return false;
    }
    @Override
    public String toString() {
        String prefix = "ML-NEW-";
        if(newExpr == null)
            return prefix+"NULL";
        return prefix+newExpr;
    }
}

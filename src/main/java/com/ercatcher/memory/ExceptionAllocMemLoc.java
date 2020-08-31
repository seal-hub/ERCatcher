package com.ercatcher.memory;

import soot.Unit;
import soot.jimple.CaughtExceptionRef;

public class ExceptionAllocMemLoc extends AllocMemLoc {
    CaughtExceptionRef caughtExceptionRef;
    ExceptionAllocMemLoc(Unit ctxUnit, CaughtExceptionRef caughtExceptionRef){
        super(ctxUnit);
        this.caughtExceptionRef = caughtExceptionRef;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + caughtExceptionRef.hashCode();
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
        ExceptionAllocMemLoc other = (ExceptionAllocMemLoc) obj;
        return this.caughtExceptionRef.equals(other.caughtExceptionRef);
    }

    @Override
    public String toString() {
        String prefix = "ML-EXP-";
        if(caughtExceptionRef == null)
            return prefix+"NULL";
        return prefix+caughtExceptionRef;
    }
}

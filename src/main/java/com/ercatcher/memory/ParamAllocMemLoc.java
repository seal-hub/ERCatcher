package com.ercatcher.memory;

import soot.Unit;
import soot.jimple.ParameterRef;

public class ParamAllocMemLoc extends AllocMemLoc  {
    ParameterRef parameterRef;
    ParamAllocMemLoc(Unit ctxUnit, ParameterRef parameterRef){
        super(ctxUnit);
        this.parameterRef = parameterRef;
    }


    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + parameterRef.hashCode();
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
        ParamAllocMemLoc other = (ParamAllocMemLoc) obj;
        if(this.parameterRef.equals(other.parameterRef))
            return true;
        return false;
    }

    @Override
    public String toString() {
        String prefix = "ML-PAR";
        if(parameterRef == null)
            return prefix+"NULL";
        return prefix+parameterRef;
    }
}

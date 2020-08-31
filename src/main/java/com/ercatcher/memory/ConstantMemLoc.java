package com.ercatcher.memory;

import soot.Unit;
import soot.jimple.Constant;

public class ConstantMemLoc extends AllocMemLoc {
    Constant constant;
    ConstantMemLoc(Unit ctxUnit, Constant constant){
        super(ctxUnit);
        this.constant = constant;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + constant.hashCode();
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
        ConstantMemLoc other = (ConstantMemLoc) obj;
        if(this.constant.equals(other.constant))
            return true;
        return false;
    }
    @Override
    public String toString() {
        String prefix = "ML-CNS-";
        if(constant == null)
            return prefix+"NULL";
        return prefix+constant;
    }
}

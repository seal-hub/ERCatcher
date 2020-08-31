package com.ercatcher.memory;

import soot.Unit;
import soot.jimple.ThisRef;

public class ThisAllocMemLoc extends AllocMemLoc {
    ThisRef thisRef;
    ThisAllocMemLoc(Unit ctxUnit, ThisRef thisRef){
        super(ctxUnit);
        this.thisRef = thisRef;
    }


    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + thisRef.hashCode();
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
        ThisAllocMemLoc other = (ThisAllocMemLoc) obj;
        if(this.thisRef.equals(other.thisRef))
            return true;
        return false;
    }

    @Override
    public String toString() {
        String prefix = "ML-THS";
        if(thisRef == null)
            return prefix+"NULL";
        return prefix+thisRef;
    }


}

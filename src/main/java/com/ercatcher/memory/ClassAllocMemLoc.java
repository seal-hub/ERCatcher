package com.ercatcher.memory;

import soot.SootClass;

public class ClassAllocMemLoc extends AllocMemLoc{
    ClassAllocMemLoc(SootClass sootClass){
        super(null);
        this.sootClass = sootClass;
    }
    SootClass sootClass;
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + sootClass.hashCode();
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
        ClassAllocMemLoc other = (ClassAllocMemLoc) obj;
        return this.sootClass.equals(other.sootClass);
    }

    @Override
    public String toString() {
        String prefix = "ML-CLS-";
        if(sootClass == null)
            return prefix+"NULL";
        return prefix+sootClass;
    }
}

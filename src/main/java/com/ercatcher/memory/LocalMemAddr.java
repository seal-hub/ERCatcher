package com.ercatcher.memory;

import soot.Local;

public class LocalMemAddr extends MemoryAddress {
    LocalMemAddr(Local local){
        this(local, false);
    }
    LocalMemAddr(Local local, boolean isArray){
        this.local = local;
        this.isArray = isArray;
    }
    Local local;

    public boolean isArray() {
        return isArray;
    }

    boolean isArray;

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + local.hashCode();
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
        LocalMemAddr other = (LocalMemAddr) obj;
        return this.local.equals(other.local);
    }

    @Override
    public String toString() {
        String prefix = "MLocAddr-";
        if(local == null)
            return prefix+"NULL";
        return prefix+local+"-"+local.getType();
    }
}

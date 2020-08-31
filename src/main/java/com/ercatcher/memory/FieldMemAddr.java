package com.ercatcher.memory;

public class FieldMemAddr extends MemoryAddress {
    public FieldMemLoc getFieldMemLoc() {
        return fieldMemLoc;
    }

    FieldMemLoc fieldMemLoc;
    FieldMemAddr(FieldMemLoc fieldMemLoc){
        this.fieldMemLoc = fieldMemLoc;
    }
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + fieldMemLoc.hashCode();
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
        FieldMemAddr other = (FieldMemAddr) obj;
        return this.fieldMemLoc.equals(other.fieldMemLoc);
    }

    @Override
    public String toString() {
        String prefix = "MFldAddr-";
        if(fieldMemLoc == null)
            return prefix+"NULL";
        return prefix+fieldMemLoc;
    }
}

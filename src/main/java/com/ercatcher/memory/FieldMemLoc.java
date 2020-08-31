package com.ercatcher.memory;

import soot.SootField;
import soot.Unit;
import soot.jimple.FieldRef;

import java.util.HashSet;
import java.util.Set;

public class FieldMemLoc extends MemoryLocation {
    public MemoryLocation getBase() {
        return base;
    }

    public SootField getField() {
        return field;
    }

    MemoryLocation base;
    SootField field;

    public FieldRef getFieldRef() {
        return fieldRef;
    }

    FieldRef fieldRef;

    FieldMemLoc(MemoryLocation base, SootField field){
        this(null, base, field, null);
    }

    FieldMemLoc(Unit ctxUnit, MemoryLocation base, SootField field, FieldRef fieldRef){
        super(ctxUnit);
        this.base = base;
        this.field = field;
        this.fieldRef = fieldRef;
    }

    @Override
    public boolean isValid() {
        Set<Unit> visited = new HashSet<>();
        MemoryLocation current = base;
        while (current instanceof FieldMemLoc) {
            if (visited.contains(current.ctxUnit))
                return false;
            visited.add(current.ctxUnit);
            current = ((FieldMemLoc) current).base;
        }
        return true;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + base.hashCode();
        result = prime * result + field.hashCode();
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
        FieldMemLoc other = (FieldMemLoc) obj;
        if(this.base.equals(other.base) && this.field.equals(other.field))
            return true;
        return false;
    }

    @Override
    public String toString() {
        String prefix = "ML-FLD-";
        if(base == null || field == null)
            return prefix+"NULL";
        return prefix+base+"."+field;
    }
}

package com.ercatcher.memory;

import soot.Unit;

public abstract class MemoryLocation {
    Unit ctxUnit;
    MemoryLocation(Unit ctxUnit){
        this.ctxUnit = ctxUnit;
    }

    public boolean isValid(){
        return true;
    }
    public Unit getCtxUnit(){
        return ctxUnit;
    }

}

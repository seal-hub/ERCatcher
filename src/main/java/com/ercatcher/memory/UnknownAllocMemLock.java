package com.ercatcher.memory;

import soot.Unit;

public class UnknownAllocMemLock extends AllocMemLoc {

    UnknownAllocMemLock(Unit ctxUnit) {
        super(ctxUnit);
    }

    @Override
    public String toString() {
        return "ML-UNK";
    }
}

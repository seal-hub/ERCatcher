package com.ercatcher.ConcurrencyAnalysis;

import com.ercatcher.memory.MemoryLocation;

public class NewThreadLattice extends ThreadLattice {
    private MemoryLocation memLoc;
    public NewThreadLattice(MemoryLocation memLoc){
        this.memLoc = memLoc;
    }

    @Override
    public String toString() {
        return "New Thread " + this.memLoc;
    }
}

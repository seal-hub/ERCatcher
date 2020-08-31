package com.ercatcher.ConcurrencyAnalysis;

import soot.jimple.spark.pag.AllocNode;

public class NewAllocThreadLattice extends ThreadLattice {
    private AllocNode allocNode;
    public NewAllocThreadLattice(AllocNode allocNode){
        this.allocNode = allocNode;
    }

    @Override
    public String toString() {
        return "New Alloc Thread " + this.allocNode;
    }
}

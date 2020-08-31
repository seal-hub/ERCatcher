package com.ercatcher.ConcurrencyAnalysis;

public class ThreadLattice {

    public boolean partialOrder(ThreadLattice threadLattice){
        if (this.equals(threadLattice))
            return true;
        if (threadLattice.equals(ThreadLatticeManager.getUNKNOWNThreadLattice()))
            return true;
        return false;
    }

    public boolean isEventQueue(){
        if(this.equals(ThreadLatticeManager.getAsyncParallelThreadLattice()))
            return false;
        if(this.equals(ThreadLatticeManager.getUNDETERMINED()))
            return false;
        if(this.equals(ThreadLatticeManager.getUNKNOWNThreadLattice()))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "UNDEFINED";
    }
}

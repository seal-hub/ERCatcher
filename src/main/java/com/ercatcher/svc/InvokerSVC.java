package com.ercatcher.svc;

import com.ercatcher.ConcurrencyAnalysis.ThreadLattice;

import java.util.Set;

public class InvokerSVC extends StaticVectorClock {

    SVCEvent target;
    int delay = 0;
    public boolean hasPositiveDelay(){
        return delay > 0;
    }
    public boolean hasNegativeDelay(){
        return delay < 0;
    }
    public void setDelay(int d){
        this.delay = d;
    }

    InvokerSVC(SVCEvent myOwnSVCEvent, Set<ThreadLattice> allThreads) {
        super(myOwnSVCEvent, allThreads);
    }

    public void setTarget(SVCEvent target){
        this.target = target;
    }
    @Override
    public ThreadLattice getThreadLattice() {
        return target.getThreadLattice();
    }

    @Override
    public String toString() {
        return id+": Invoke-"+target.toString();
    }
}

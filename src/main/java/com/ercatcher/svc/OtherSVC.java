package com.ercatcher.svc;

import com.ercatcher.ConcurrencyAnalysis.ThreadLattice;

import java.util.Set;

public class OtherSVC extends StaticVectorClock {
    boolean isStart;
    OtherSVC(SVCEvent myOwnSVCEvent, Set<ThreadLattice> allThreads, boolean isStart) {
        super(myOwnSVCEvent, allThreads);
        this.isStart = isStart;
    }

    @Override
    public ThreadLattice getThreadLattice() {
        return myOwnSVCEvent.getThreadLattice();
    }

    @Override
    public String toString() {
        return id+ ": " + (isStart ? "Start-" : "End-") + myOwnSVCEvent.toString();
    }
}

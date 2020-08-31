package com.ercatcher.svc;

import com.ercatcher.ConcurrencyAnalysis.C3G.C3GTask;
import com.ercatcher.ConcurrencyAnalysis.C3G.SNode;

public interface StaticHappensBefore {
    public boolean isComputationFinished();
    public boolean happensBefore(StaticVectorClock first, StaticVectorClock second);
    public boolean happensBefore(SNode first, SNode second);
    public boolean happensBefore(C3GTask first, C3GTask second);
//    public StaticHappensRelation mayHappenIndeterministic(SootMethod first, SootMethod second);
    public boolean happensBefore(SVCEvent first, SVCEvent second);
    public boolean reaches(StaticVectorClock first, StaticVectorClock second);
}

package com.ercatcher.memory;

import com.ercatcher.LOG;
import soot.SootMethod;
import soot.toolkits.graph.CompleteUnitGraph;
import soot.toolkits.graph.UnitGraph;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class MemoryAnalysis {
    private Map<SootMethod, IntraAllocationAnalysis> methodToAllocInfo = new HashMap<>();
    public MemoryAnalysis(Set<SootMethod> allAPKMethods, boolean isUAF){
        LOG.startTimer("Memory Analysis", LOG.VERBOSE);
        for(SootMethod sootMethod : allAPKMethods) {
            if(!sootMethod.hasActiveBody())
                continue;
            if(sootMethod.getDeclaringClass().getName().startsWith("java") || sootMethod.getDeclaringClass().getName().startsWith("android"))
                continue;
            UnitGraph unitGraph = new CompleteUnitGraph(sootMethod.getActiveBody());
            IntraAllocationAnalysis intraAllocationAnalysis = new IntraAllocationAnalysis(unitGraph, isUAF);
            methodToAllocInfo.put(sootMethod, intraAllocationAnalysis);
        }
        LOG.endTimer("Memory Analysis", LOG.VERBOSE);
    }

    public IntraAllocationAnalysis getAllocInfo(SootMethod method){
        return methodToAllocInfo.getOrDefault(method, null);
    }
}

package com.ercatcher.ConcurrencyAnalysis;

import com.ercatcher.memory.MemoryLocation;
import soot.SootMethod;
import soot.jimple.spark.pag.AllocNode;

import java.util.HashMap;
import java.util.Map;

public class ThreadLatticeManager {
    private static ThreadLattice UNKNOWN = new ThreadLattice(){
        @Override
        public String toString() {
            return "UNKNOWN";
        }
    };
    private static ThreadLattice UNDETERMINED = new ThreadLattice(){
        @Override
        public String toString() {
            return "UNDETERMINED";
        }
    };
    private static ThreadLattice UI = new ThreadLattice(){
        @Override
        public String toString() {
            return "UI";
        }
    };
    private static ThreadLattice ASYNC_SERIAL = new ThreadLattice(){
        @Override
        public String toString() {
            return "ASYNC_SERIAL";
        }
    };
    private static ThreadLattice INTENT_SERVICE = new ThreadLattice(){
        @Override
        public String toString() {
            return "INTENT_SERVICE";
        }
    };
    private static ThreadLattice ASYNC_PARALLEL = new ThreadLattice(){
        @Override
        public String toString() {
            return "ASYNC_PARALLEL";
        }
    };

    private static Map<MemoryLocation, NewThreadLattice> newThreadLatticeMap = new HashMap<>();
    private static Map<AllocNode, NewAllocThreadLattice> newAllocThreadLatticeMap = new HashMap<>();
    private static Map<SootMethod, MethodThreadLattice> methodThreadLatticeMap = new HashMap<>();

    public static NewThreadLattice getNewThreadLattice(MemoryLocation memLoc){
        if(!newThreadLatticeMap.containsKey(memLoc))
            newThreadLatticeMap.put(memLoc, new NewThreadLattice(memLoc));
        return newThreadLatticeMap.get(memLoc);
    }

    public static NewAllocThreadLattice getNewAllocThreadLattice(AllocNode allocNode){
        if(!newAllocThreadLatticeMap.containsKey(allocNode))
            newAllocThreadLatticeMap.put(allocNode, new NewAllocThreadLattice(allocNode));
        return newAllocThreadLatticeMap.get(allocNode);
    }

    public static MethodThreadLattice getMethodThreadLattice(SootMethod method){
        if(!methodThreadLatticeMap.containsKey(method)) {
            methodThreadLatticeMap.put(method, new MethodThreadLattice((method)));
        }
        return methodThreadLatticeMap.get(method);
    }

    public static boolean sameThread(ThreadLattice t1, ThreadLattice t2){
        if(t1.equals(ASYNC_PARALLEL) && t2.equals(ASYNC_PARALLEL))
            return false;
        if(t1.equals(UNKNOWN) && t2.equals(UNKNOWN))
            return false;
        if(t1.equals(UNDETERMINED) && t2.equals(UNDETERMINED))
            return false;
        return t1.equals(t2);
    }

    public static ThreadLattice eval(ThreadLattice currentTL, ThreadLattice newTL){
        if (currentTL.equals(UNKNOWN))
            return getUNKNOWNThreadLattice();
        if(newTL.equals(UNDETERMINED))
            return currentTL;
        if (currentTL.equals(UNDETERMINED))
            return newTL;
        if (currentTL.equals(ASYNC_PARALLEL) && newTL.equals(ASYNC_PARALLEL))
            return newTL;
        if (currentTL.equals(newTL))
            return currentTL;
        return UNKNOWN;
    }

    public static ThreadLattice getAsyncParallelThreadLattice(){
        return ASYNC_PARALLEL;
    }

    public static ThreadLattice getUIThreadLattice(){
        return UI;
    }

    public static ThreadLattice getUNKNOWNThreadLattice(){
        return UNKNOWN;
    }
    public static ThreadLattice getUNDETERMINED(){
        return UNDETERMINED;
    }

    public static ThreadLattice getAsyncSerialThreadLattice(){
        return ASYNC_SERIAL;
    }
    public static ThreadLattice getIntentServiceThreadLattice(){
        return INTENT_SERVICE;
    }
}

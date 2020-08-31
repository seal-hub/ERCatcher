package com.ercatcher.memory;

public class NullAllocMemLoc extends AllocMemLoc {
    private NullAllocMemLoc(){
        super(null);
    };
    private static NullAllocMemLoc self;
    public static NullAllocMemLoc v(){
        if(self == null)
            self = new NullAllocMemLoc();
        return self;
    }
    @Override
    public String toString() {
        return "ML-NULL-ALLOC";
    }
}

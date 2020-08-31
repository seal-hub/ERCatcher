package com.ercatcher.memory;

import soot.toolkits.scalar.AbstractBoundedFlowSet;
import soot.toolkits.scalar.AbstractFlowSet;

import java.util.*;

public class MemoryPairSet extends AbstractBoundedFlowSet<MemoryPair> {
    Map<MemoryAddress, Set<MemoryPair>> addrToPairs = new HashMap<>();

    @Override
    public AbstractFlowSet<MemoryPair> clone() {
        MemoryPairSet copy = new MemoryPairSet();
        for (MemoryAddress addr : addrToPairs.keySet()) {
            copy.addrToPairs.put(addr, new HashSet<>());
            for (MemoryPair memPair : addrToPairs.get(addr))
                copy.addrToPairs.get(addr).add(memPair.getClone());
        }
        return copy;
    }

    @Override
    public boolean isEmpty() {
        return addrToPairs.isEmpty();
    }

    @Override
    public int size() {
        return addrToPairs.size();
    }

    @Override
    public void add(MemoryPair memoryPair) {
        if (!memoryPair.isValid())
            return;
        if (!addrToPairs.containsKey(memoryPair.getAddr()))
            addrToPairs.put(memoryPair.getAddr(), new HashSet<>());
        addrToPairs.get(memoryPair.getAddr()).add(memoryPair);
    }

    @Override
    public void remove(MemoryPair memoryPair) {
        if (!contains(memoryPair))
            return;
        addrToPairs.get(memoryPair.getAddr()).remove(memoryPair);
        if (addrToPairs.get(memoryPair.getAddr()).isEmpty())
            addrToPairs.remove(memoryPair.getAddr());
    }

    public void remove(MemoryAddress addr) {
        if (!contains(addr))
            return;
        addrToPairs.remove(addr);
    }

    public boolean contains(MemoryAddress addr) {
        return addrToPairs.containsKey(addr);
    }

    @Override
    public boolean contains(MemoryPair memoryPair) {
        if (!contains(memoryPair.getAddr()))
            return false;
        return addrToPairs.get(memoryPair.getAddr()).contains(memoryPair);
    }

    @Override
    public Iterator<MemoryPair> iterator() {
        return toList().iterator();
    }

    @Override
    public List<MemoryPair> toList() {
        List<MemoryPair> allMemPairs = new ArrayList<>();
        for (Set<MemoryPair> mPair : addrToPairs.values())
            allMemPairs.addAll(mPair);
        return allMemPairs;
    }

    public Set<MemoryLocation> aliasLoc(MemoryAddress addr) {
        return aliasLoc(addr, false);
    }

    public Set<MemoryLocation> aliasLoc(MemoryAddress addr, boolean refine) {
        Set<MemoryLocation> result = new HashSet<>();
        if (contains(addr)) {
            for (MemoryPair mp : addrToPairs.get(addr)) {
                boolean flag = true;
                if(refine) {
                    if (mp.getLoc() instanceof FieldMemLoc) {
                        FieldMemLoc fMLoc = (FieldMemLoc) mp.getLoc();
                        if (contains(new FieldMemAddr(fMLoc)))
                            flag = false;
                    }
                }
                if(flag){
                    result.add(mp.getLoc());
                }
            }
        }
        return result;
    }
}

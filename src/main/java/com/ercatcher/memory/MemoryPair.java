package com.ercatcher.memory;

import soot.jimple.Stmt;

public class MemoryPair {
    MemoryPair(MemoryAddress addr, MemoryLocation loc, Stmt modificationStmt){
        this.addr = addr;
        this.loc = loc;
        this.modificationStmt = modificationStmt;
    }

    MemoryPair getClone(){
        return new MemoryPair(addr, loc, modificationStmt);
    }

    public MemoryAddress getAddr() {
        return addr;
    }

    public MemoryLocation getLoc() {
        return loc;
    }

    private MemoryAddress addr;
    private MemoryLocation loc;
    private Stmt modificationStmt; // TODO: it can be null after refine. Is it necessary?

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + addr.hashCode();
        result = prime * result + loc.hashCode();
//        result = prime * result + modificationStmt.hashCode();
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if(this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        MemoryPair other = (MemoryPair) obj;
        return this.addr.equals(other.addr) && this.loc.equals(other.loc);
    }

    boolean isValid(){
        return loc.isValid();
    }

}

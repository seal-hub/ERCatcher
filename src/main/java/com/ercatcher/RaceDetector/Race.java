package com.ercatcher.RaceDetector;

public abstract class Race<E, M> {
    public E getWriteEvent() {
        return writeEvent;
    }

    public E getReadEvent() {
        return readEvent;
    }

    public M getMemoryId() {
        return memoryId;
    }

    private E writeEvent;
    private E readEvent;
    private M memoryId;
    protected boolean writeWrite=false;
    public boolean isWW(){
        return writeWrite;
    }
    Race(E writeEvent, E readEvent, M memoryId){
        this.writeEvent = writeEvent;
        this.readEvent = readEvent;
        this.memoryId = memoryId;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        if(isWW()){
            result = prime * result + (writeEvent.hashCode()+readEvent.hashCode());
        }
        else{
            result = prime * result + writeEvent.hashCode();
            result = prime * result + readEvent.hashCode();
        }
        result = prime * result + memoryId.hashCode();
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Race other = (Race) obj;
        if(isWW()){
            return this.memoryId.equals(other.memoryId)
                    && ((this.writeEvent.equals(other.writeEvent) && this.readEvent.equals(other.readEvent))
                        || (this.writeEvent.equals(other.readEvent) && this.readEvent.equals(other.writeEvent)));
        }
        return this.writeEvent.equals(other.writeEvent) && this.readEvent.equals(other.readEvent) && this.memoryId.equals(other.memoryId);
    }

    @Override
    public String toString() {
        return String.format("Write Event: %s Read Event: %s, Memory Id: %s", writeEvent.toString(), readEvent.toString(), memoryId.toString());
    }
}

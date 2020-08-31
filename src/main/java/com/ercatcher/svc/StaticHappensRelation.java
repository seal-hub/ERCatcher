package com.ercatcher.svc;

public enum StaticHappensRelation {
    BEFORE,
    AFTER,
//    PARALLEL,
    INDETERMINATE,
    BEFORE_INDETERMINATE,
    AFTER_INDETERMINATE,
    CYCLE,
    UNKNOWN,
    NOT_EXISTED,
    SAME;
    public StaticHappensRelation eval(StaticHappensRelation observed){
        StaticHappensRelation current = this;
        if(current == NOT_EXISTED || observed == NOT_EXISTED)
            return NOT_EXISTED;
        if(current == CYCLE)
            return CYCLE;
        if(current == UNKNOWN || current == SAME) {
            return observed;
        }
        if(observed == SAME)
            return current;
        if(observed == BEFORE){
            if(current == INDETERMINATE || current == BEFORE_INDETERMINATE)
                return BEFORE_INDETERMINATE;
            if(current == AFTER || current == AFTER_INDETERMINATE)
                return CYCLE;
            return BEFORE;
        }
        if(observed == AFTER){
            if(current == INDETERMINATE || current == AFTER_INDETERMINATE)
                return AFTER_INDETERMINATE;
            if(current == BEFORE || current == BEFORE_INDETERMINATE)
                return CYCLE;
            if(current == AFTER)
                return AFTER;
        }
        if(observed == INDETERMINATE){
            if(current == AFTER || current == AFTER_INDETERMINATE)
                return AFTER_INDETERMINATE;
            if(current == BEFORE || current == BEFORE_INDETERMINATE)
                return BEFORE_INDETERMINATE;
            return INDETERMINATE;
        }
        if(observed == BEFORE_INDETERMINATE){
            if(current == AFTER || current == AFTER_INDETERMINATE)
                return CYCLE;
            return BEFORE_INDETERMINATE;
        }
        if(observed == AFTER_INDETERMINATE){
            if(current == BEFORE || current == BEFORE_INDETERMINATE)
                return CYCLE;
            return BEFORE_INDETERMINATE;
        }
        return CYCLE;
    }
}

package com.ercatcher.RaceDetector;

import java.util.Collection;

public interface RaceDetector<M extends Race> {
    public Collection<M> getRaces();
}

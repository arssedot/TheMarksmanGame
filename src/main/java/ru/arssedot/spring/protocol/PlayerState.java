package ru.arssedot.spring.protocol;

import java.io.Serializable;

public record PlayerState(
        String name,
        double y,
        int score,
        int shots,
        double arrowX,
        double arrowY,
        int colorIndex,
        boolean ready
) implements Serializable {}

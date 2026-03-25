package ru.arssedot.spring.protocol;

import java.io.Serializable;
import java.util.List;

public record GameSnapshot(
        boolean gameRunning,
        boolean gamePaused,
        double nearTargetY,
        double farTargetY,
        double targetSpeed,
        List<PlayerState> players
) implements Serializable {}

package com.myplan.app.ads;

/** Central placement keys — enable/disable later without hardcoding UI. */
public enum AdPlacement {
    TODAY,
    TASKS,
    EXAMS,
    STATS;

    public String key() { return name().toLowerCase(); }
}

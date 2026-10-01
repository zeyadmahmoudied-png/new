package com.myplan.app.sync;

/** Entities eligible for future cloud sync. Secrets/passwords are never in scope. */
public enum SyncEntityType {
    TASK,
    SESSION,
    EXAM,
    SUBJECT,
    COMMITMENT,
    FOCUS,
    SETTINGS,
    PROGRESS,
    ACHIEVEMENT,
    JOURNAL,
    GOAL
}

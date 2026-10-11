package com.planora.service;

/** Where the planner's data lives. */
public interface PlannerStore {
    PlannerData load();

    void save(PlannerData data);
}

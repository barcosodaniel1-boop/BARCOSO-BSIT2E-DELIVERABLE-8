package com.scc.model;

/** One row of the activity_logs (audit trail) table. userId is null for system actions. */
public record ActivityLog(int id, Integer userId, String actionType, String description, String timestamp) {
}
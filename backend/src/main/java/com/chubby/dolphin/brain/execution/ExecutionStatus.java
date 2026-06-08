package com.chubby.dolphin.brain.execution;

public enum ExecutionStatus {
    PENDING,
    VALIDATING,
    EXECUTING,
    EXECUTED,
    PARTIAL_SUCCESS,
    FAILED,
    ROLLED_BACK
}

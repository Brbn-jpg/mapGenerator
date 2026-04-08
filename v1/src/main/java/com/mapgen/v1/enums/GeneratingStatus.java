package com.mapgen.v1.enums;

public enum GeneratingStatus {
    PENDING("Pending"),
    IN_PROGRESS("In progress"),
    COMPLETED("Completed");

    private final String status;

    GeneratingStatus(String status){
        this.status = status;
    }

    public String toString(){
        return this.status;
    }
}

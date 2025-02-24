package me.leon.sniffer.data.enums;

import lombok.Getter;

@Getter
public enum DataType {
    // Movement Data Types
    POSITION("Position and Location Data"),
    VELOCITY("Velocity and Speed Data"),
    GROUND("Ground State Data"),
    FLYING("Flying State Data"),

    // Combat Data Types
    ATTACK("Attack Patterns and Timing"),
    REACH("Reach Distance Data"),
    CRITICAL("Critical Hit Data"),
    AIM("Aim Pattern Data"),

    // Rotation Data Types
    HEAD("Head Movement Data"),
    BODY("Body Rotation Data"),
    ANGLE("Angle Change Data"),
    SENSITIVITY("Mouse Sensitivity Data"),

    // Block Data Types
    BREAK("Block Breaking Data"),
    PLACE("Block Placement Data"),
    INTERACTION("Block Interaction Data"),
    FACE("Block Face Data");

    private final String description;

    DataType(String description) {
        this.description = description;
    }
}
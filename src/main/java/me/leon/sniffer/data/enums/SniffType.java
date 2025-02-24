package me.leon.sniffer.data.enums;

import lombok.Getter;

@Getter
public enum SniffType {
    MOVEMENT("movement", "Analyzes player movement patterns"),
    COMBAT("combat", "Analyzes combat patterns and interactions"),
    ROTATION("rotation", "Analyzes player rotations and head movements"),
    BLOCK("block", "Analyzes block interactions and breaking patterns"),
    ALL("all", "Analyzes all packet types");

    private final String id;
    private final String description;

    SniffType(String id, String description) {
        this.id = id;
        this.description = description;
    }

    public static SniffType fromString(String type) {
        for (SniffType sniffType : values()) {
            if (sniffType.getId().equalsIgnoreCase(type)) {
                return sniffType;
            }
        }
        return null;
    }
}
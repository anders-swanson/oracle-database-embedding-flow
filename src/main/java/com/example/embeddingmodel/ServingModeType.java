package com.example.embeddingmodel;

import lombok.Getter;

/**
 * Serving Mode for OCI GenAI clusters.
 */
@Getter
public enum ServingModeType {

    ON_DEMAND("on-demand"),
    DEDICATED("dedicated");

    private final String mode;

    ServingModeType(String mode) {
        this.mode = mode;
    }
}


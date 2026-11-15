package com.restovision.domain;

public enum AlertType {
    /** Clients assis non servis depuis `threshold` secondes. */
    UNSERVED,
    /** Table à débarrasser depuis `threshold` secondes. */
    TO_CLEAR,
    /** Au moins `threshold` personnes dans la file. */
    QUEUE_LENGTH
}

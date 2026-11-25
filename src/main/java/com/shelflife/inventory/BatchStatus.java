package com.shelflife.inventory;

/** Where a batch is in its life on the shelf. */
public enum BatchStatus {
    /** On the shelf at full price. */
    ACTIVE,
    /** On the shelf at a reduced price because it expires soon. Can still sell out or be wasted. */
    MARKED_DOWN,
    /** Nothing left: the last units were sold. */
    SOLD_OUT,
    /** Nothing left: the last units were thrown away. */
    WASTED;

    /** Open batches still have units on the shelf and can change; closed ones are final. */
    public boolean isOpen() {
        return this == ACTIVE || this == MARKED_DOWN;
    }
}

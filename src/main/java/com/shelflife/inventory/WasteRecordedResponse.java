package com.shelflife.inventory;

/** What staff see after throwing units away: the new waste record and the batch as it is now. */
public record WasteRecordedResponse(WasteResponse waste, BatchResponse batch) {
}

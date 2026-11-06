package com.leslie.teachtrack.question;

import java.util.UUID;

public record QuestionAsset(UUID assetId, String assetType, int sortOrder) {
}

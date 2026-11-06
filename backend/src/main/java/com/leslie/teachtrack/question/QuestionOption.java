package com.leslie.teachtrack.question;

import java.util.UUID;

public record QuestionOption(UUID id, String optionKey, String content, int sortOrder) {
}

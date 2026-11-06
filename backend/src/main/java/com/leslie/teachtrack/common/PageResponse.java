package com.leslie.teachtrack.common;

import java.util.List;

public record PageResponse<T>(List<T> items, int limit, int offset, long total) {
}

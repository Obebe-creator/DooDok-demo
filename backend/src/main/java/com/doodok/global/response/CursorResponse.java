package com.doodok.global.response;

import java.util.List;
import java.util.function.Function;

/**
 * 커서 페이지네이션 목록 응답. (docs/06-api-spec.md 1. 공통 규칙 - 목록 응답)
 *
 * <pre>
 * { "items": [...], "nextCursor": "30", "hasNext": true }
 * </pre>
 */
public record CursorResponse<T>(List<T> items, String nextCursor, boolean hasNext) {

    public static <T> CursorResponse<T> of(List<T> items, String nextCursor, boolean hasNext) {
        return new CursorResponse<>(List.copyOf(items), nextCursor, hasNext);
    }

    /**
     * 요청한 개수보다 1개 더 조회한 결과로 응답을 만든다.
     * 1개가 더 있으면 다음 페이지가 있다는 뜻이고, 그 1개는 응답에서 뺀다.
     *
     * @param fetched         size + 1개까지 조회한 결과
     * @param size            클라이언트가 요청한 개수
     * @param cursorExtractor 마지막 항목에서 다음 커서를 만드는 함수 (예: item -> String.valueOf(item.id()))
     */
    public static <T> CursorResponse<T> fromFetched(List<T> fetched, int size, Function<T, String> cursorExtractor) {
        if (size < 1) {
            throw new IllegalArgumentException("size must be at least 1");
        }
        boolean hasNext = fetched.size() > size;
        List<T> items = hasNext ? fetched.subList(0, size) : fetched;
        String nextCursor = hasNext ? cursorExtractor.apply(items.get(items.size() - 1)) : null;
        return new CursorResponse<>(List.copyOf(items), nextCursor, hasNext);
    }
}

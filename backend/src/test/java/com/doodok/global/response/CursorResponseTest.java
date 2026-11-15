package com.doodok.global.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CursorResponseTest {

    record Item(long id) {
    }

    @Test
    @DisplayName("요청 개수보다 1개 더 조회되면 다음 페이지가 있고, 마지막 항목으로 커서를 만든다")
    void hasNext() {
        List<Item> fetched = List.of(new Item(30), new Item(29), new Item(28));

        CursorResponse<Item> response = CursorResponse.fromFetched(fetched, 2, item -> String.valueOf(item.id()));

        assertThat(response.items()).containsExactly(new Item(30), new Item(29));
        assertThat(response.hasNext()).isTrue();
        assertThat(response.nextCursor()).isEqualTo("29");
    }

    @Test
    @DisplayName("요청 개수 이하로 조회되면 마지막 페이지다")
    void lastPage() {
        List<Item> fetched = List.of(new Item(2), new Item(1));

        CursorResponse<Item> response = CursorResponse.fromFetched(fetched, 2, item -> String.valueOf(item.id()));

        assertThat(response.items()).hasSize(2);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    @DisplayName("결과가 없으면 빈 목록과 함께 마지막 페이지로 응답한다")
    void empty() {
        CursorResponse<Item> response = CursorResponse.fromFetched(List.of(), 20, item -> String.valueOf(item.id()));

        assertThat(response.items()).isEmpty();
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    @DisplayName("size가 1보다 작으면 예외가 발생한다")
    void invalidSize() {
        assertThatThrownBy(() -> CursorResponse.fromFetched(List.of(), 0, item -> "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

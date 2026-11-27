package com.doodok.domain.book;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record BookRegisterRequest(
        @NotNull(message = "필수 값입니다.")
        @Pattern(regexp = "\\d{13}", message = "ISBN-13은 숫자 13자리입니다.")
        String isbn13) {
}

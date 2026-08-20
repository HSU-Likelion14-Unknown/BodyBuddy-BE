package com.centerton.bodybuddy.domain.calendar.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class CalendarTimeMapperTest {

    @Test
    void convertsKoreaDayBoundaryToUtc() {
        LocalDateTime startUtc = CalendarTimeMapper.toUtcStartOfDay(
                LocalDate.of(2026, 8, 21)
        );

        assertThat(startUtc).isEqualTo(LocalDateTime.of(2026, 8, 20, 15, 0));
    }

    @Test
    void groupsLateUtcTimeIntoNextKoreaDate() {
        LocalDate koreaDate = CalendarTimeMapper.toKoreaDate(
                LocalDateTime.of(2026, 8, 20, 16, 30)
        );

        assertThat(koreaDate).isEqualTo(LocalDate.of(2026, 8, 21));
    }

    @Test
    void returnsCalendarMealTimeWithKoreaOffset() {
        OffsetDateTime koreaDateTime = CalendarTimeMapper.toKoreaOffsetDateTime(
                LocalDateTime.of(2026, 8, 20, 16, 30)
        );

        assertThat(koreaDateTime).isEqualTo(
                OffsetDateTime.parse("2026-08-21T01:30:00+09:00")
        );
    }
}

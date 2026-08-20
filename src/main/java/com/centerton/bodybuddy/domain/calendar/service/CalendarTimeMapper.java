package com.centerton.bodybuddy.domain.calendar.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

final class CalendarTimeMapper {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private CalendarTimeMapper() {
    }

    static LocalDateTime toUtcStartOfDay(LocalDate koreaDate) {
        return koreaDate.atStartOfDay(KOREA_ZONE)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
    }

    static LocalDate toKoreaDate(LocalDateTime utcDateTime) {
        return utcDateTime.atOffset(ZoneOffset.UTC)
                .atZoneSameInstant(KOREA_ZONE)
                .toLocalDate();
    }

    static OffsetDateTime toKoreaOffsetDateTime(LocalDateTime utcDateTime) {
        return utcDateTime.atOffset(ZoneOffset.UTC)
                .atZoneSameInstant(KOREA_ZONE)
                .toOffsetDateTime();
    }
}

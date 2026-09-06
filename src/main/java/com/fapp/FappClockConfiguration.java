package com.fapp;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The clock the application reads "today" from.
 *
 * <p>A bean rather than {@code LocalDate.now()} scattered through services, so a rule
 * that depends on the date — a savings goal cannot be created already overdue, a
 * projection starts from this month — can be tested against a fixed day instead of
 * behaving differently depending on when the suite runs.
 */
@Configuration
class FappClockConfiguration {

    @Bean
    Clock systemClock() {
        return Clock.systemUTC();
    }
}

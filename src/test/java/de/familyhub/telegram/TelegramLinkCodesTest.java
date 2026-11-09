package de.familyhub.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

// Einmal-Codes zum Verknüpfen: nur einmal, nur zehn Minuten, nur der neueste je Person
class TelegramLinkCodesTest {

    // Uhr, die der Test vorstellen kann
    private static final class MutableClock extends Clock {

        private Instant now = Instant.parse("2026-10-21T08:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final MutableClock clock = new MutableClock();
    private final TelegramLinkCodes codes = new TelegramLinkCodes(clock);

    @Test
    void codeWorksOnceForItsMember() {
        String code = codes.create("emma");

        assertThat(codes.redeem(code)).contains("emma");
        assertThat(codes.redeem(code)).isEmpty();
        assertThat(codes.redeem("erfunden")).isEmpty();
    }

    @Test
    void codeExpiresAfterTenMinutes() {
        String fresh = codes.create("emma");
        String old = codes.create("lucas");
        clock.advance(Duration.ofMinutes(9));
        assertThat(codes.redeem(fresh)).contains("emma");

        clock.advance(Duration.ofMinutes(2));
        assertThat(codes.redeem(old)).isEmpty();
    }

    @Test
    void newCodeReplacesTheOldOne() {
        String first = codes.create("emma");
        String second = codes.create("emma");

        assertThat(codes.redeem(first)).isEmpty();
        assertThat(codes.redeem(second)).contains("emma");
    }

    @Test
    void codesUseOnlyCharactersTelegramAllowsInStartLinks() {
        for (int i = 0; i < 20; i++) {
            assertThat(codes.create("m" + i)).matches("[A-Za-z0-9_-]{1,64}");
        }
    }
}

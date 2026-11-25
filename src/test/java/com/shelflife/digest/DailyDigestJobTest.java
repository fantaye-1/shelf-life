package com.shelflife.digest;

import static org.assertj.core.api.Assertions.assertThat;

import com.shelflife.TestcontainersConfiguration;
import com.shelflife.catalog.Item;
import com.shelflife.catalog.ItemRepository;
import com.shelflife.inventory.Batch;
import com.shelflife.inventory.BatchRepository;
import com.shelflife.notify.EmailMessage;
import com.shelflife.notify.EmailSender;
import com.shelflife.store.Location;
import com.shelflife.store.LocationRepository;
import com.shelflife.store.Merchant;
import com.shelflife.store.MerchantRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/**
 * Drives the digest job with a movable clock and a sender that records mail. The database is
 * shared by the tests in this class, so each test uses its own recipient addresses and only looks
 * at mail sent to them.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, DailyDigestJobTest.Fakes.class})
class DailyDigestJobTest {

    /** A clock tests can move. */
    static final class MovableClock extends Clock {
        private volatile Instant now = Instant.parse("2026-11-24T12:00:00Z");

        void set(String instant) {
            now = Instant.parse(instant);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(now, zone);
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    /** Records every email, and fails for addresses in {@code failFor}. */
    static final class RecordingSender implements EmailSender {
        final List<EmailMessage> sent = new CopyOnWriteArrayList<>();
        final Set<String> failFor = ConcurrentHashMap.newKeySet();

        @Override
        public void send(EmailMessage message) {
            if (failFor.contains(message.to())) {
                throw new IllegalStateException("mail provider down");
            }
            sent.add(message);
        }

        List<EmailMessage> to(String address) {
            return sent.stream().filter(m -> m.to().equals(address)).toList();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Fakes {
        @Bean
        @Primary
        MovableClock movableClock() {
            return new MovableClock();
        }

        @Bean
        @Primary
        RecordingSender recordingSender() {
            return new RecordingSender();
        }
    }

    @Autowired
    private DailyDigestJob job;

    @Autowired
    private MovableClock clock;

    @Autowired
    private RecordingSender sender;

    @Autowired
    private MerchantRepository merchants;

    @Autowired
    private LocationRepository locations;

    @Autowired
    private ItemRepository items;

    @Autowired
    private BatchRepository batches;

    private UUID zmarket;
    private UUID milk;

    @BeforeEach
    void setUp() {
        zmarket = merchants.save(new Merchant("Zmarket23")).getId();
        Item milkItem = new Item(zmarket, "Whole milk 1 gal");
        milk = items.save(milkItem).getId();
    }

    private static String address() {
        return "owner-" + UUID.randomUUID() + "@zmarket23.example";
    }

    private UUID store(String name, String timezone, String digestEmail) {
        Location location = new Location(zmarket, name, timezone);
        location.changeDigestEmail(digestEmail);
        return locations.save(location).getId();
    }

    private void batch(UUID location, String expiresOn) {
        batches.save(new Batch(zmarket, milk, location, LocalDate.parse(expiresOn), 6, 250));
    }

    @Test
    void sendsOncePerStoreDayAfterTheSendHourInTheStoresOwnTimeZone() {
        String to = address();
        UUID dayton = store("Dayton", "America/New_York", to);
        batch(dayton, "2026-11-26");

        clock.set("2026-11-24T11:30:00Z"); // 6:30 AM in Dayton: too early
        job.runDue();
        assertThat(sender.to(to)).isEmpty();

        clock.set("2026-11-24T12:05:00Z"); // 7:05 AM in Dayton
        job.runDue();
        assertThat(sender.to(to)).hasSize(1);
        EmailMessage email = sender.to(to).get(0);
        assertThat(email.subject()).isEqualTo("Dayton expiry digest: 1 in 3 days");
        assertThat(email.body())
                .contains("Dayton (Zmarket23), as of Tuesday, Nov 24, 2026")
                .contains("- Whole milk 1 gal: 6 left, expires Thu Nov 26, $15.00");

        clock.set("2026-11-24T20:05:00Z"); // later the same store day: no second email
        job.runDue();
        clock.set("2026-11-25T04:05:00Z"); // 11:05 PM Nov 24 in Dayton, though UTC is already Nov 25
        job.runDue();
        assertThat(sender.to(to)).hasSize(1);
        assertThat(locations.findByMerchantIdAndId(zmarket, dayton).orElseThrow().getDigestSentOn())
                .isEqualTo(LocalDate.parse("2026-11-24"));

        clock.set("2026-11-25T12:05:00Z"); // next morning
        job.runDue();
        assertThat(sender.to(to)).hasSize(2);
        assertThat(sender.to(to).get(1).subject()).isEqualTo("Dayton expiry digest: 1 in 3 days");
    }

    @Test
    void eachStoreWaitsForItsOwnMorning() {
        String east = address();
        String west = address();
        batch(store("Dayton", "America/New_York", east), "2026-11-30");
        batch(store("Oakland", "America/Los_Angeles", west), "2026-11-30");

        clock.set("2026-12-01T12:05:00Z"); // 7:05 AM Eastern, 4:05 AM Pacific
        job.runDue();
        assertThat(sender.to(east)).hasSize(1);
        assertThat(sender.to(west)).isEmpty();

        clock.set("2026-12-01T15:05:00Z"); // 7:05 AM Pacific
        job.runDue();
        assertThat(sender.to(east)).hasSize(1);
        assertThat(sender.to(west)).hasSize(1);
        assertThat(sender.to(west).get(0).body()).contains("PULL NOW: expired and still on the shelf (1)");
    }

    @Test
    void skipsStoresWithNothingExpiringOrNoRecipient() {
        String quiet = address();
        store("Quiet store", "America/New_York", quiet);
        UUID farOff = store("Far-off stock", "America/New_York", address());
        batch(farOff, "2027-03-01");
        UUID noEmail = store("No email", "America/New_York", null);
        batch(noEmail, "2026-12-02");
        int before = sender.sent.size();

        clock.set("2026-12-02T13:05:00Z");
        job.runDue();

        assertThat(sender.to(quiet)).isEmpty();
        // Nothing went to any store set up in this test.
        assertThat(sender.sent.subList(before, sender.sent.size()))
                .noneMatch(m -> m.subject().startsWith("Quiet store") || m.subject().startsWith("Far-off stock")
                        || m.subject().startsWith("No email"));
    }

    @Test
    void aFailedSendIsRetriedOnTheNextRun() {
        String to = address();
        UUID dayton = store("Dayton", "America/New_York", to);
        batch(dayton, "2026-12-04");
        sender.failFor.add(to);

        clock.set("2026-12-03T12:05:00Z");
        DailyDigestJob.RunResult failedRun = job.runDue();
        assertThat(failedRun.failed()).isGreaterThanOrEqualTo(1);
        assertThat(sender.to(to)).isEmpty();
        assertThat(locations.findByMerchantIdAndId(zmarket, dayton).orElseThrow().getDigestSentOn()).isNull();

        sender.failFor.remove(to);
        clock.set("2026-12-03T13:05:00Z");
        job.runDue();
        assertThat(sender.to(to)).hasSize(1);
    }

    @Test
    void claimingADayIsAtomic() {
        UUID dayton = store("Dayton", "America/New_York", address());
        LocalDate day = LocalDate.parse("2026-12-10");

        assertThat(locations.claimDigest(dayton, day)).isEqualTo(1);
        assertThat(locations.claimDigest(dayton, day)).isZero();
        assertThat(locations.claimDigest(dayton, day.minusDays(1))).isZero();
        assertThat(locations.releaseDigest(dayton, day)).isEqualTo(1);
        assertThat(locations.claimDigest(dayton, day)).isEqualTo(1);
    }
}

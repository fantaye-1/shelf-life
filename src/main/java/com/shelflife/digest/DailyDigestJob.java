package com.shelflife.digest;

import com.shelflife.inventory.ExpiringReport;
import com.shelflife.inventory.ExpiringService;
import com.shelflife.notify.EmailSender;
import com.shelflife.store.Location;
import com.shelflife.store.LocationRepository;
import com.shelflife.store.Merchant;
import com.shelflife.store.MerchantRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Sends each store its morning expiry digest. Safe to run as often as you like: a store gets at
 * most one digest per store-local day, and only once its own clock has reached the send hour. So
 * an hourly run reaches a Dayton store at 7 AM Eastern and a Los Angeles store three hours later,
 * and a run missed while the server was down is caught up on the next one.
 *
 * <p>A store with nothing expiring gets no email that day. If sending fails, the day is released
 * so the next run tries again.
 */
@Service
public class DailyDigestJob {

    private static final Logger log = LoggerFactory.getLogger(DailyDigestJob.class);

    private final LocationRepository locations;
    private final MerchantRepository merchants;
    private final ExpiringService expiring;
    private final EmailSender sender;
    private final Clock clock;
    private final int sendHour;
    private final int withinDays;

    public DailyDigestJob(LocationRepository locations, MerchantRepository merchants, ExpiringService expiring,
            EmailSender sender, Clock clock,
            @Value("${shelflife.digest.send-hour:7}") int sendHour,
            @Value("${shelflife.digest.within-days:" + ExpiringService.DEFAULT_WITHIN_DAYS + "}") int withinDays) {
        if (sendHour < 0 || sendHour > 23) {
            throw new IllegalArgumentException("shelflife.digest.send-hour must be 0-23, was " + sendHour);
        }
        if (withinDays < 0 || withinDays > ExpiringService.MAX_WITHIN_DAYS) {
            throw new IllegalArgumentException("shelflife.digest.within-days must be 0-"
                    + ExpiringService.MAX_WITHIN_DAYS + ", was " + withinDays);
        }
        this.locations = locations;
        this.merchants = merchants;
        this.expiring = expiring;
        this.sender = sender;
        this.clock = clock;
        this.sendHour = sendHour;
        this.withinDays = withinDays;
    }

    /** Sends every digest that is due now. One store's failure doesn't stop the others. */
    public RunResult runDue() {
        int sent = 0;
        int failed = 0;
        for (Location location : locations.findAllByDigestEmailIsNotNull()) {
            try {
                if (sendIfDue(location)) {
                    sent++;
                }
            } catch (RuntimeException e) {
                failed++;
                log.warn("Digest for location {} failed; will retry on the next run", location.getId(), e);
            }
        }
        if (sent > 0 || failed > 0) {
            log.info("Daily digest run: {} sent, {} failed", sent, failed);
        }
        return new RunResult(sent, failed);
    }

    private boolean sendIfDue(Location location) {
        ZonedDateTime storeNow = ZonedDateTime.now(clock.withZone(location.zone()));
        if (storeNow.getHour() < sendHour) {
            return false;
        }
        LocalDate storeToday = storeNow.toLocalDate();
        LocalDate previous = location.getDigestSentOn();
        if (previous != null && !previous.isBefore(storeToday)) {
            return false;
        }
        if (locations.claimDigest(location.getId(), storeToday) != 1) {
            return false; // another run or instance took it
        }
        try {
            ExpiringReport report = expiring.report(location.getMerchantId(), location.getId(), withinDays);
            if (report.batches().isEmpty()) {
                return false;
            }
            String merchantName = merchants.findById(location.getMerchantId()).map(Merchant::getName).orElse(null);
            sender.send(DigestComposer.compose(location.getDigestEmail(), merchantName, report));
            return true;
        } catch (RuntimeException e) {
            locations.releaseDigest(location.getId(), storeToday);
            throw e;
        }
    }

    /** How many digests one run sent, and how many failed and will be retried. */
    public record RunResult(int sent, int failed) {
    }
}

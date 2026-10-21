package tut.ac.za.AgriFinanceAPIs.security;

import org.junit.jupiter.api.Test;
import tut.ac.za.AgriFinanceAPIs.common.Hashing;
import tut.ac.za.AgriFinanceAPIs.common.RateLimiter;
import tut.ac.za.AgriFinanceAPIs.exportready.ExportReadiness;
import tut.ac.za.AgriFinanceAPIs.reports.SimpleXlsxWriter;
import tut.ac.za.AgriFinanceAPIs.vault.VaultCrypto;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

class PlatformSecurityLogicTest {

    @Test
    void totpMatchesRfc6238ReferenceValue() {
        // RFC 6238 test secret "12345678901234567890" at T=59s gives 94287082 (8 digits) -> 287082 (6 digits).
        String secret = Totp.base32("12345678901234567890".getBytes(StandardCharsets.US_ASCII));
        assertEquals("287082", Totp.codeAt(secret, 1));
        assertTrue(Totp.verify(secret, "287082", 59_000L));
        assertFalse(Totp.verify(secret, "000000", 59_000L));
        assertFalse(Totp.verify(secret, "abc", 59_000L));
    }

    @Test
    void totpSecretsRoundTripThroughBase32() {
        String secret = Totp.newSecret();
        assertEquals(20, Totp.base32Decode(secret).length);
        assertTrue(Totp.otpauthUri("AgriTech", "admin@example.com", secret).startsWith("otpauth://totp/AgriTech:"));
    }

    @Test
    void rateLimiterBlocksAfterMaxAttemptsInWindow() {
        AtomicLong now = new AtomicLong(1_000_000);
        RateLimiter limiter = new RateLimiter(now::get);
        for (int i = 0; i < 5; i++) assertTrue(limiter.tryAcquire("login:x", 5, 60_000));
        assertFalse(limiter.tryAcquire("login:x", 5, 60_000));
        assertTrue(limiter.retryAfterSeconds("login:x", 60_000) > 0);
        now.addAndGet(61_000);
        assertTrue(limiter.tryAcquire("login:x", 5, 60_000));
    }

    @Test
    void hashedTokensAreStableAndRandomTokensDiffer() {
        assertEquals(Hashing.sha256("abc"), Hashing.sha256("abc"));
        assertEquals(64, Hashing.sha256("abc").length());
        assertNotEquals(Hashing.randomToken(32), Hashing.randomToken(32));
    }

    @Test
    void vaultEncryptsAndDetectsTampering() {
        VaultCrypto crypto = new VaultCrypto(Base64.getEncoder().encodeToString(new byte[32]), null);
        byte[] plain = "CIPC certificate".getBytes(StandardCharsets.UTF_8);
        byte[] stored = crypto.encrypt(plain);
        assertNotEquals(new String(plain, StandardCharsets.UTF_8), new String(stored, StandardCharsets.ISO_8859_1));
        assertArrayEquals(plain, crypto.decrypt(stored));
        stored[stored.length - 1] ^= 1;
        assertThrows(IllegalStateException.class, () -> crypto.decrypt(stored));
        assertThrows(IllegalArgumentException.class, () -> new VaultCrypto(Base64.getEncoder().encodeToString(new byte[10]), null));
    }

    @Test
    void exportReadinessScoresAndGatesStages() {
        List<ExportReadiness.Item> items = List.of(
                new ExportReadiness.Item(1, "DONE"), new ExportReadiness.Item(1, "NOT_APPLICABLE"),
                new ExportReadiness.Item(2, "TODO"), new ExportReadiness.Item(3, "DONE"));
        ExportReadiness.Score s = ExportReadiness.score(items);
        assertEquals(67, s.percent());
        assertEquals(2, s.firstIncompleteStage());
        assertTrue(ExportReadiness.canAdvance(items, 1));
        assertFalse(ExportReadiness.canAdvance(items, 2));
        assertFalse(ExportReadiness.exportReady(items));
    }

    @Test
    void xlsxWriterProducesAValidZipWithNumericCells() throws Exception {
        List<List<Object>> rows = new ArrayList<>();
        rows.add(List.of("Month", "Income"));
        rows.add(List.of("2026-03", new BigDecimal("1234.50")));
        byte[] bytes = new SimpleXlsxWriter().sheet("Cash flow", rows).sheet("Notes", List.of(List.of("A & B < C"))).toBytes();
        List<String> names = new ArrayList<>();
        String sheet1 = null;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                names.add(e.getName());
                if (e.getName().equals("xl/worksheets/sheet1.xml")) sheet1 = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        assertTrue(names.contains("[Content_Types].xml"));
        assertTrue(names.contains("xl/worksheets/sheet2.xml"));
        assertNotNull(sheet1);
        assertTrue(sheet1.contains("<v>1234.50</v>"));
        assertEquals("AA", invokeColumn(26));
    }

    private static String invokeColumn(int i) throws Exception {
        var m = SimpleXlsxWriter.class.getDeclaredMethod("column", int.class);
        m.setAccessible(true);
        return (String) m.invoke(null, i);
    }
}

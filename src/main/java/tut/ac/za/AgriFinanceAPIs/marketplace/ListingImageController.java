package tut.ac.za.AgriFinanceAPIs.marketplace;

import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/market")
public class ListingImageController {

    private static final long MAX_BYTES = 2L * 1024 * 1024;
    private static final int MAX_PER_LISTING = 3;

    private final ListingRepository listings;
    private final ListingImageRepository images;

    public ListingImageController(ListingRepository listings, ListingImageRepository images) {
        this.listings = listings;
        this.images = images;
    }

    /** Public on purpose (see SecurityConfig): an img tag cannot send a login header, and these are product photos. */
    @GetMapping("/images/{imageId}")
    public ResponseEntity<byte[]> get(@PathVariable Long imageId) {
        return images.findById(imageId)
                .map(i -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(i.getContentType()))
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic())
                        .body(i.getData()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping(value = "/listings/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public Map<String, Object> upload(@PathVariable Long id, @RequestParam("file") MultipartFile file,
                                      Authentication auth) throws IOException {
        Listing l = ownedListing(id, auth);
        if (file.isEmpty()) throw bad("No file uploaded");
        if (file.getSize() > MAX_BYTES) throw new ResponseStatusException(HttpStatus.valueOf(413), "Photo is too large (max 2 MB)");
        if (images.countByListingId(l.getId()) >= MAX_PER_LISTING) throw bad("A listing can have at most " + MAX_PER_LISTING + " photos");

        byte[] bytes = file.getBytes();
        String type = sniff(bytes);
        if (type == null) throw bad("Only JPEG, PNG or WebP photos are allowed");

        ListingImage img = new ListingImage();
        img.setListingId(l.getId());
        img.setContentType(type);
        img.setData(bytes);
        ListingImage saved = images.save(img);
        return Map.of("id", saved.getId());
    }

    @DeleteMapping("/listings/{id}/images/{imageId}")
    @Transactional
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable Long id, @PathVariable Long imageId, Authentication auth) {
        Listing l = ownedListing(id, auth);
        ListingImage img = images.findById(imageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Photo not found"));
        if (!img.getListingId().equals(l.getId())) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Photo not found");
        images.delete(img);
    }

    private Listing ownedListing(Long id, Authentication auth) {
        Listing l = listings.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found"));
        boolean admin = auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (!admin && !l.getSellerId().equals(auth.getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only change photos on your own listings");
        }
        return l;
    }

    private static ResponseStatusException bad(String m) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, m); }

    /** Decides the type from the file's first bytes, never from the file name or the client's header. */
    private static String sniff(byte[] b) {
        if (b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "image/jpeg";
        if (b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) return "image/png";
        if (b.length > 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return "image/webp";
        return null;
    }
}

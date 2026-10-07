package tut.ac.za.AgriFinanceAPIs.farmer;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/farmers/{id}/avatar")
public class AvatarController {

    private static final long MAX_BYTES = 2L * 1024 * 1024;

    private final FarmerAvatarRepository avatars;
    private final FarmerRepository farmers;

    public AvatarController(FarmerAvatarRepository avatars, FarmerRepository farmers) {
        this.avatars = avatars;
        this.farmers = farmers;
    }

    /** Any signed-in farmer can view any farmer's photo. 204 means "no photo yet". */
    @GetMapping
    public ResponseEntity<byte[]> get(@PathVariable String id) {
        return avatars.findById(id)
                .map(a -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(a.getContentType()))
                        .body(a.getData()))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public ResponseEntity<Void> upload(@PathVariable String id, @RequestParam("file") MultipartFile file,
                                       Authentication auth) throws IOException {
        requireSelf(id, auth);
        if (file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No file uploaded");
        if (file.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.valueOf(413), "Image is too large (max 2 MB)");
        }
        byte[] bytes = file.getBytes();
        String type = sniff(bytes);
        if (type == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only JPEG, PNG or WebP images are allowed");
        }
        if (!farmers.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Farmer not found");

        FarmerAvatar a = avatars.findById(id).orElseGet(FarmerAvatar::new);
        a.setFarmerId(id);
        a.setContentType(type);
        a.setData(bytes);
        a.setUpdatedAt(LocalDateTime.now());
        avatars.save(a);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    @Transactional
    public ResponseEntity<Void> remove(@PathVariable String id, Authentication auth) {
        requireSelf(id, auth);
        if (avatars.existsById(id)) avatars.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private void requireSelf(String id, Authentication auth) {
        if (!id.equals(auth.getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only change your own photo");
        }
    }

    /** Decides the type from the file's first bytes, never from the file name or client header. */
    private String sniff(byte[] b) {
        if (b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "image/jpeg";
        if (b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) return "image/png";
        if (b.length > 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return "image/webp";
        return null;
    }
}
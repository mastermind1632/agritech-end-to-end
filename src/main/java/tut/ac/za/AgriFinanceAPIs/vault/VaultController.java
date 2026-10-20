package tut.ac.za.AgriFinanceAPIs.vault;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/vault")
public class VaultController {

    private final VaultService service;

    public VaultController(VaultService service) {
        this.service = service;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<VaultDocument> upload(@RequestParam("file") MultipartFile file,
                                                 @RequestParam("docType") String docType,
                                                 @RequestParam(value = "expiresAt", required = false)
                                                 @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiresAt,
                                                 @RequestParam(value = "notes", required = false) String notes,
                                                 Authentication auth) throws IOException {
        VaultDocument doc = service.store(auth.getName(), file, docType, expiresAt, notes);
        return ResponseEntity.status(HttpStatus.CREATED).body(doc);
    }

    @GetMapping("/{farmerId}")
    public List<VaultDocument> list(@PathVariable String farmerId, Authentication auth) {
        if (!farmerId.equals(auth.getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only view your own vault");
        }
        return service.list(farmerId);
    }

    @GetMapping("/{id}/meta")
    public VaultDocument meta(@PathVariable String id) {
        return service.get(id);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable String id, Authentication auth) throws IOException {
        VaultDocument doc = service.get(id);
        byte[] content = service.download(id, auth.getName());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + doc.getOriginalFilename() + "\"")
                .contentType(MediaType.parseMediaType(doc.getMimeType()))
                .body(content);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id, Authentication auth) throws IOException {
        service.delete(id, auth.getName());
        return Map.of("deleted", true, "id", id);
    }
}

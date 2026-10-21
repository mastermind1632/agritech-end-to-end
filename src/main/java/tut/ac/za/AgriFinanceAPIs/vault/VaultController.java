package tut.ac.za.AgriFinanceAPIs.vault;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import tut.ac.za.AgriFinanceAPIs.common.Roles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/vault")
public class VaultController {

    private final VaultService vault;
    private final VaultAccess access;

    public VaultController(VaultService vault, VaultAccess access) {
        this.vault = vault;
        this.access = access;
    }

    public record UpdateDocument(String title, String docType, LocalDate expiresOn) { }

    @GetMapping
    public List<Map<String, Object>> list(Authentication auth) {
        return vault.list(auth.getName()).stream().map(VaultController::view).toList();
    }

    @GetMapping("/types")
    public List<String> types() {
        return VaultService.TYPES.stream().sorted().toList();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file,
                                      @RequestParam(value = "title", required = false) String title,
                                      @RequestParam(value = "docType", required = false) String docType,
                                      @RequestParam(value = "expiresOn", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiresOn,
                                      Authentication auth) throws IOException {
        if (file == null || file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No file uploaded");
        return view(vault.store(auth.getName(), title, docType, file.getOriginalFilename(), file.getBytes(), expiresOn, null, null));
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody UpdateDocument body, Authentication auth) {
        return view(vault.update(id, auth.getName(), body.title(), body.docType(), body.expiresOn()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id, Authentication auth) {
        vault.delete(id, auth.getName());
    }

    /** The owner, an admin, or the other party of a dispute the document is evidence for may download it. */
    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable String id, Authentication auth) {
        VaultService.FileData f = vault.read(id);
        if (!access.canView(f.info(), auth.getName(), Roles.isAdmin(auth))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found");
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.info().contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(f.info().fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(f.bytes());
    }

    public static Map<String, Object> view(VaultDocumentInfo i) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("id", i.id());
        m.put("title", i.title());
        m.put("docType", i.docType());
        m.put("fileName", i.fileName());
        m.put("contentType", i.contentType());
        m.put("sizeBytes", i.sizeBytes());
        m.put("expiresOn", i.expiresOn());
        m.put("daysToExpiry", i.daysToExpiry());
        m.put("expiryStatus", i.expiryStatus());
        m.put("linkedModule", i.linkedModule());
        m.put("linkedRef", i.linkedRef());
        m.put("createdAt", i.createdAt());
        return m;
    }
}

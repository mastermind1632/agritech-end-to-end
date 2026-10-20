package tut.ac.za.AgriFinanceAPIs.vault;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import tut.ac.za.AgriFinanceAPIs.consent.ConsentService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class VaultService {

    private final VaultDocumentRepository repo;
    private final VaultCryptoService crypto;
    private final ConsentService consent;
    private final Path storageRoot;

    public VaultService(VaultDocumentRepository repo,
                        VaultCryptoService crypto,
                        ConsentService consent,
                        @Value("${app.vault.storage-path:/app/vault}") String storagePath) {
        this.repo = repo;
        this.crypto = crypto;
        this.consent = consent;
        this.storageRoot = Paths.get(storagePath);
    }

    @Transactional
    public VaultDocument store(String farmerId, MultipartFile file, String docType, LocalDate expiresAt, String notes) throws IOException {
        if (!consent.hasConsent(farmerId, ConsentService.DATA_PROCESSING)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "POPIA consent for DATA_PROCESSING is required before uploading documents.");
        }
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is empty.");
        }
        if (file.getSize() > 20L * 1024 * 1024) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Maximum file size is 20MB.");
        }

        byte[] plaintext = file.getBytes();
        String hash = crypto.sha256Hex(plaintext);
        byte[] encrypted = crypto.encrypt(plaintext);

        Path farmerDir = storageRoot.resolve(farmerId);
        Files.createDirectories(farmerDir);
        String id = UUID.randomUUID().toString();
        Path target = farmerDir.resolve(id + ".enc");
        Files.write(target, encrypted);

        VaultDocument doc = new VaultDocument();
        doc.setId(id);
        doc.setFarmerId(farmerId);
        doc.setDocType(docType);
        doc.setOriginalFilename(file.getOriginalFilename());
        doc.setMimeType(file.getContentType() == null ? "application/octet-stream" : file.getContentType());
        doc.setFileSize(file.getSize());
        doc.setStoragePath(target.toString());
        doc.setSha256Hash(hash);
        doc.setExpiresAt(expiresAt);
        doc.setConsentVerified(true);
        doc.setNotes(notes);
        return repo.save(doc);
    }

    public List<VaultDocument> list(String farmerId) {
        return repo.findByFarmerIdOrderByUploadedAtDesc(farmerId);
    }

    public VaultDocument get(String id) {
        return repo.findById(id).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
    }

    public byte[] download(String id, String requesterId) throws IOException {
        VaultDocument doc = get(id);
        if (!doc.getFarmerId().equals(requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not own this document");
        }
        byte[] encrypted = Files.readAllBytes(Paths.get(doc.getStoragePath()));
        return crypto.decrypt(encrypted);
    }

    @Transactional
    public void delete(String id, String requesterId) throws IOException {
        VaultDocument doc = get(id);
        if (!doc.getFarmerId().equals(requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not own this document");
        }
        Files.deleteIfExists(Paths.get(doc.getStoragePath()));
        repo.delete(doc);
    }
}

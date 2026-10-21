package tut.ac.za.AgriFinanceAPIs.vault;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tut.ac.za.AgriFinanceAPIs.common.AuditService;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Service
public class VaultService {

    public static final long MAX_BYTES = 8L * 1024 * 1024;
    public static final int MAX_DOCUMENTS = 300;
    public static final Set<String> TYPES = Set.of("ID_DOCUMENT", "CIPC_CERTIFICATE", "TAX_CLEARANCE", "VAT_REGISTRATION",
            "BANK_LETTER", "BANK_STATEMENT", "PERMIT", "CERTIFICATE", "PHYTOSANITARY", "INSPECTION_REPORT",
            "SHIPPING_DOCUMENT", "CONTRACT", "INVOICE", "EVIDENCE", "OTHER");

    public record FileData(VaultDocumentInfo info, byte[] bytes) { }

    private final VaultDocumentRepository documents;
    private final VaultCrypto crypto;
    private final AuditService audit;

    public VaultService(VaultDocumentRepository documents, VaultCrypto crypto, AuditService audit) {
        this.documents = documents;
        this.crypto = crypto;
        this.audit = audit;
    }

    @Transactional
    public VaultDocumentInfo store(String ownerId, String title, String docType, String fileName, byte[] bytes,
                                   LocalDate expiresOn, String linkedModule, String linkedRef) {
        if (bytes == null || bytes.length == 0) throw bad("The file is empty");
        if (bytes.length > MAX_BYTES) throw new ResponseStatusException(HttpStatus.valueOf(413), "Documents can be at most 8 MB");
        if (documents.countByOwnerId(ownerId) >= MAX_DOCUMENTS) throw bad("Your vault is full. Delete documents you no longer need.");
        String type = sniff(bytes, fileName);
        if (type == null) throw bad("Only PDF, JPEG, PNG, WebP or CSV files can be stored");
        String t = docType == null || docType.isBlank() ? "OTHER" : docType.trim().toUpperCase();
        if (!TYPES.contains(t)) t = "OTHER";
        VaultDocument d = new VaultDocument();
        d.setOwnerId(ownerId);
        d.setTitle(clip(title == null || title.isBlank() ? fileName : title.trim(), 150));
        d.setDocType(t);
        d.setFileName(clip(cleanName(fileName), 200));
        d.setContentType(type);
        d.setSizeBytes(bytes.length);
        d.setSha256(VaultCrypto.sha256Hex(bytes));
        d.setData(crypto.encrypt(bytes));
        d.setExpiresOn(expiresOn);
        d.setLinkedModule(linkedModule);
        d.setLinkedRef(linkedRef);
        VaultDocument saved = documents.save(d);
        audit.record(ownerId, "DOCUMENT_UPLOADED", "DOCUMENT", saved.getId(), t + " " + d.getFileName());
        return info(saved);
    }

    public List<VaultDocumentInfo> list(String ownerId) {
        return documents.listForOwner(ownerId);
    }

    public VaultDocumentInfo requireOwned(String id, String ownerId) {
        VaultDocumentInfo i = documents.info(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
        if (!i.ownerId().equals(ownerId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found");
        return i;
    }

    /** Decrypts a document. The caller must already have checked that the viewer may see it. */
    @Transactional(readOnly = true)
    public FileData read(String id) {
        VaultDocument d = documents.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
        return new FileData(info(d), crypto.decrypt(d.getData()));
    }

    @Transactional
    public VaultDocumentInfo update(String id, String ownerId, String title, String docType, LocalDate expiresOn) {
        requireOwned(id, ownerId);
        VaultDocument d = documents.findById(id).orElseThrow();
        if (title != null && !title.isBlank()) d.setTitle(clip(title.trim(), 150));
        if (docType != null && TYPES.contains(docType.trim().toUpperCase())) d.setDocType(docType.trim().toUpperCase());
        d.setExpiresOn(expiresOn);
        return info(documents.save(d));
    }

    @Transactional
    public void delete(String id, String ownerId) {
        requireOwned(id, ownerId);
        documents.deleteById(id);
        audit.record(ownerId, "DOCUMENT_DELETED", "DOCUMENT", id, null);
    }

    static VaultDocumentInfo info(VaultDocument d) {
        return new VaultDocumentInfo(d.getId(), d.getOwnerId(), d.getTitle(), d.getDocType(), d.getFileName(),
                d.getContentType(), d.getSizeBytes(), d.getExpiresOn(), d.getLinkedModule(), d.getLinkedRef(), d.getCreatedAt());
    }

    /** File type from the first bytes, never from the name or the browser's claim. */
    public static String sniff(byte[] b, String fileName) {
        if (b.length > 4 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F') return "application/pdf";
        if (b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "image/jpeg";
        if (b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "image/png";
        if (b.length > 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return "image/webp";
        String n = fileName == null ? "" : fileName.toLowerCase();
        if ((n.endsWith(".csv") || n.endsWith(".txt")) && looksLikeText(b)) return "text/csv";
        return null;
    }

    private static boolean looksLikeText(byte[] b) {
        int check = Math.min(b.length, 4096);
        for (int i = 0; i < check; i++) {
            int c = b[i] & 0xFF;
            if (c == 0) return false;
            if (c < 0x09 || (c > 0x0D && c < 0x20)) return false;
        }
        return true;
    }

    private static String cleanName(String n) {
        if (n == null || n.isBlank()) return "document";
        return n.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_");
    }

    private static String clip(String s, int max) { return s.length() > max ? s.substring(0, max) : s; }

    private static ResponseStatusException bad(String m) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, m); }
}

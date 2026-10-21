package tut.ac.za.AgriFinanceAPIs.vault;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface VaultDocumentRepository extends JpaRepository<VaultDocument, String> {

    String INFO = "select new tut.ac.za.AgriFinanceAPIs.vault.VaultDocumentInfo(d.id, d.ownerId, d.title, d.docType, d.fileName, "
            + "d.contentType, d.sizeBytes, d.expiresOn, d.linkedModule, d.linkedRef, d.createdAt) from VaultDocument d ";

    @Query(INFO + "where d.ownerId = :owner order by d.createdAt desc")
    List<VaultDocumentInfo> listForOwner(@Param("owner") String ownerId);

    @Query(INFO + "where d.id = :id")
    Optional<VaultDocumentInfo> info(@Param("id") String id);

    @Query(INFO + "where d.id in :ids")
    List<VaultDocumentInfo> infos(@Param("ids") Collection<String> ids);

    @Query(INFO + "where d.linkedModule = :module and d.linkedRef = :ref order by d.createdAt")
    List<VaultDocumentInfo> linked(@Param("module") String module, @Param("ref") String ref);

    @Query(INFO + "where d.expiresOn is not null and d.expiresOn between :from and :to")
    List<VaultDocumentInfo> expiringBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    long countByOwnerId(String ownerId);
}

package tut.ac.za.AgriFinanceAPIs.statements;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StatementTransactionRepository extends JpaRepository<StatementTransaction, Long> {
    List<StatementTransaction> findByStatementIdOrderByTxnDateAscIdAsc(String statementId);

    List<StatementTransaction> findByFarmerIdOrderByTxnDateAscIdAsc(String farmerId);

    /** Transactions the farmer has confirmed: training data for their personal classifier. */
    @Query("select t from StatementTransaction t, BankStatement s where s.id = t.statementId and s.status = 'CONFIRMED' "
            + "and t.farmerId = :farmerId order by t.id desc")
    List<StatementTransaction> confirmedHistory(@Param("farmerId") String farmerId, org.springframework.data.domain.Pageable page);

    /** Everything from confirmed statements, used for the analysis dashboard. */
    @Query("select t from StatementTransaction t, BankStatement s where s.id = t.statementId and s.status = 'CONFIRMED' "
            + "and t.farmerId = :farmerId order by t.txnDate, t.id")
    List<StatementTransaction> confirmed(@Param("farmerId") String farmerId);

    void deleteByStatementId(String statementId);
}

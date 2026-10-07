package tut.ac.za.AgriFinanceAPIs.chat;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, String> {

    List<ChatMessage> findByRoomOrderByCreatedAtDesc(String room, Pageable pageable);

    @Query("select m from ChatMessage m where m.room like 'dm:%' and m.room like concat('%', :me, '%') order by m.createdAt desc")
    List<ChatMessage> findDirectFor(@Param("me") String me, Pageable pageable);
}
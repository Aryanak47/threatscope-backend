//package com.threatscopebackend.repository.sql;
//
//import com.threatscope.entity.SearchHistory;
//import org.springframework.data.domain.Pageable;
//import org.springframework.data.jpa.repository.JpaRepository;
//import org.springframework.data.jpa.repository.Query;
//import org.springframework.stereotype.Repository;
//
//import java.time.LocalDateTime;
//import java.util.List;
//import java.util.Optional;
//
//@Repository
//public interface SearchHistoryRepository extends JpaRepository<SearchHistory, Long> {
//
//    // Find user's search history
//    List<SearchHistory> findByUser_IdOrderByCreatedAtDesc(Long userId, Pageable pageable);
//    List<SearchHistory> findByUser_IdOrderByCreatedAtDesc(Long userId);
//
//    List<SearchHistory> findTop10ByUser_IdOrderByCreatedAtDesc(Long userId);
//    List<SearchHistory> findTop20ByUser_IdOrderByCreatedAtDesc(Long userId);
//
//    // Find recent searches for a user
//    List<SearchHistory> findByUser_IdAndCreatedAtAfterOrderByCreatedAtDesc(Long userId, LocalDateTime since);
//
//    // Count searches by user
//    long countByUser_Id(Long userId);
//    long countByUser_IdAndCreatedAtAfter(Long userId, LocalDateTime since);
//
//    // Find searches by type
//    List<SearchHistory> findBySearchType(SearchHistory.SearchType searchType);
//
//    // Find last search
//    Optional<SearchHistory> findTopByUser_IdOrderByCreatedAtDesc(Long userId);
//    Optional<SearchHistory> findTopByUser_IdAndQueryOrderByCreatedAtDesc(Long userId, String query);
//
//    // Analytics queries
//    @Query("SELECT h.query, COUNT(h) as count FROM SearchHistory h WHERE h.user.id = ?1 GROUP BY h.query ORDER BY count DESC")
//    List<Object[]> findTopQueriesByUser(Long userId);
//
//    @Query("SELECT COUNT(h) FROM SearchHistory h WHERE h.createdAt >= ?1")
//    long countSearchesSince(LocalDateTime since);

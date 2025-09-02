package com.threatscopebackend.repository.postgresql;

import com.threatscopebackend.entity.postgresql.SearchHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SearchHistoryRepository extends JpaRepository<SearchHistory, Long> {

    // Find user's search history
    Page<SearchHistory> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    List<SearchHistory> findByUserIdOrderByCreatedAtDesc(Long userId);

    // Find recent searches for a user
    List<SearchHistory> findTop10ByUserIdOrderByCreatedAtDesc(Long userId);
    List<SearchHistory> findByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(Long userId, LocalDateTime since);

    // Count searches by user
    long countByUserId(Long userId);
    long countByUserIdAndCreatedAtAfter(Long userId, LocalDateTime since);
    
    // Count searches today
    @Query("SELECT COUNT(s) FROM SearchHistory s WHERE s.user.id = :userId AND s.createdAt >= :startOfDay")
    long countTodaySearches(@Param("userId") Long userId, @Param("startOfDay") LocalDateTime startOfDay);

    // Find searches by type
    List<SearchHistory> findBySearchType(SearchHistory.SearchType searchType);
    List<SearchHistory> findByUserIdAndSearchType(Long userId, SearchHistory.SearchType searchType);

    // Find saved searches
    List<SearchHistory> findByUserIdAndIsSavedTrueOrderByCreatedAtDesc(Long userId);
    List<SearchHistory> findByUserIdAndIsBookmarkedTrueOrderByCreatedAtDesc(Long userId);

    // Find last search
    Optional<SearchHistory> findTopByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<SearchHistory> findTopByUserIdAndQueryOrderByCreatedAtDesc(Long userId, String query);

    // Find shared searches
    Optional<SearchHistory> findByShareToken(String shareToken);
    List<SearchHistory> findByUserIdAndIsSharedTrue(Long userId);

    // Analytics queries
    @Query("SELECT h.query, COUNT(h) as count FROM SearchHistory h WHERE h.user.id = :userId GROUP BY h.query ORDER BY count DESC")
    List<Object[]> findTopQueriesByUser(@Param("userId") Long userId);

    @Query("SELECT h.searchType, COUNT(h) as count FROM SearchHistory h WHERE h.user.id = :userId GROUP BY h.searchType")
    List<Object[]> countBySearchTypeForUser(@Param("userId") Long userId);

    @Query("SELECT COUNT(h) FROM SearchHistory h WHERE h.createdAt >= :since")
    long countSearchesSince(@Param("since") LocalDateTime since);
    
    // Find by user and time range
    @Query("SELECT h FROM SearchHistory h WHERE h.user.id = :userId AND h.createdAt BETWEEN :startDate AND :endDate ORDER BY h.createdAt DESC")
    List<SearchHistory> findByUserIdAndDateRange(@Param("userId") Long userId, 
                                                  @Param("startDate") LocalDateTime startDate, 
                                                  @Param("endDate") LocalDateTime endDate);
}

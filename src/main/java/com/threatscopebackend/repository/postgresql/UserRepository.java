package com.threatscopebackend.repository.postgresql;


import com.threatscopebackend.entity.postgresql.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    Optional<User> findByEmail(String email);
    
    boolean existsByEmail(String email);
    
    @Query("SELECT u FROM User u WHERE u.emailVerificationToken = :token")
    Optional<User> findByEmailVerificationToken(@Param("token") String token);
    
    @Query("SELECT u FROM User u WHERE u.passwordResetToken = :token" +
           " AND u.passwordResetExpires > CURRENT_TIMESTAMP")
    Optional<User> findByPasswordResetToken(@Param("token") String token);
    
    @Query("SELECT u FROM User u WHERE u.isActive = true")
    List<User> findAllActiveUsers();
    
    @Query("SELECT u FROM User u WHERE u.isEmailVerified = false")
    List<User> findUnverifiedUsers();
    
    @Query("SELECT u FROM User u WHERE u.lastLogin < :date")
    List<User> findInactiveUsersSince(@Param("date") java.time.LocalDateTime date);
    
    @Query("SELECT u FROM User u JOIN u.roles r WHERE r.name = :roleName")
    List<User> findByRole(@Param("roleName") String roleName);
    
    @Query("SELECT COUNT(u) > 0 FROM User u JOIN u.roles r WHERE r.name = :roleName")
    boolean existsByRole(@Param("roleName") String roleName);
    
    @Query("SELECT u FROM User u WHERE " +
           "LOWER(u.firstName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(u.lastName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<User> search(@Param("query") String query);
    
    @Query("SELECT COUNT(u) FROM User u WHERE u.createdAt >= CURRENT_DATE")
    long countNewUsersToday();
    
    @Query("SELECT u FROM User u WHERE u.subscription IS NOT NULL")
    List<User> findSubscribedUsers();
    
    @Query("SELECT u FROM User u WHERE u.twoFactorEnabled = true")
    List<User> findUsersWith2FAEnabled();
    
    @Query("SELECT u FROM User u WHERE u.isActive = true AND u.isEmailVerified = true")
    List<User> findActiveAndVerifiedUsers();
    
    @Query("SELECT u FROM User u WHERE u.isActive = true AND u.lastActivity < :inactiveSince")
    List<User> findInactiveUsers(@Param("inactiveSince") java.time.LocalDateTime inactiveSince);
    
    @Query("UPDATE User u SET u.isActive = :active WHERE u.id = :userId")
    void updateUserStatus(@Param("userId") Long userId, @Param("active") boolean active);
    
    @Query("SELECT u FROM User u WHERE u.company = :company")
    List<User> findByCompany(@Param("company") String company);
    
    @Query("SELECT DISTINCT u.company FROM User u WHERE u.company IS NOT NULL")
    List<String> findAllCompanies();
    
    @Query("SELECT u FROM User u WHERE u.id IN :userIds")
    List<User> findByIds(@Param("userIds") List<Long> userIds);
}

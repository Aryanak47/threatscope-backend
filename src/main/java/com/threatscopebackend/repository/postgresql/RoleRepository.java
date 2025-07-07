package com.threatscopebackend.repository.postgresql;

import com.threatscope.entity.postgresql.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {
    
    Optional<Role> findByName(Role.RoleName name);
    
    boolean existsByName(Role.RoleName name);
    
    @Query("SELECT r FROM Role r LEFT JOIN FETCH r.users WHERE r.name = :name")
    Optional<Role> findByNameWithUsers(@Param("name") Role.RoleName name);
    
    @Query("SELECT r FROM Role r LEFT JOIN FETCH r.users")
    List<Role> findAllWithUsers();
    
    @Query("SELECT r FROM Role r LEFT JOIN FETCH r.users u WHERE u.id = :userId")
    List<Role> findByUserId(@Param("userId") Long userId);
    
    @Query("SELECT r FROM Role r WHERE r.name IN :names")
    List<Role> findByNames(@Param("names") List<Role.RoleName> names);
    
    @Query("SELECT r FROM Role r WHERE r.name = :name AND r.id != :id")
    Optional<Role> findByNameAndNotId(@Param("name") Role.RoleName name, @Param("id") Long id);
    
    @Query("SELECT COUNT(r) FROM Role r WHERE r.name = :name")
    long countByName(@Param("name") Role.RoleName name);
    
    @Query("SELECT r FROM Role r ORDER BY r.name")
    List<Role> findAllOrderByName();
    
    @Query("SELECT r FROM Role r WHERE r.name LIKE %:query% ORDER BY r.name")
    List<Role> search(@Param("query") String query);
}

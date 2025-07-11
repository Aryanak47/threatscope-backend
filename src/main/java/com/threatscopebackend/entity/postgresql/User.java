package com.threatscopebackend.entity.postgresql;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;




@Entity
@Table(name = "users", uniqueConstraints = {
    @UniqueConstraint(columnNames = "email")
})
@Data
@NoArgsConstructor
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = true)
    private String password;

    
    @Column
    private String providerId;

    private String phoneNumber;
    private String timezone = "UTC";
    private String language = "en";
    private String avatarUrl;
    private String jobTitle;
    private String department;
    private String company;
    private String country;
    private String bio;

    @Column(nullable = false)
    private boolean isActive = true;

    @Column(nullable = false)
    private boolean isEmailVerified = false;

    private boolean twoFactorEnabled = false;
    private String twoFactorSecret;
    private String emailVerificationToken;
    private LocalDateTime emailVerificationExpires;
    private String passwordResetToken;
    private LocalDateTime passwordResetExpires;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    private LocalDateTime lastLogin;
    private LocalDateTime lastPasswordChange;
    private LocalDateTime lastActivity;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Subscription subscription;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<MonitoringItem> monitoringItems = new HashSet<>();


    // Helper methods
    public void addRole(Role role) {
        this.roles.add(role);
        role.getUsers().add(this);
    }

    public void removeRole(Role role) {
        this.roles.remove(role);
        role.getUsers().remove(this);
    }

    public boolean hasRole(Role.RoleName roleName) {
        return roles.stream().anyMatch(role -> role.getName() == roleName);
    }
}

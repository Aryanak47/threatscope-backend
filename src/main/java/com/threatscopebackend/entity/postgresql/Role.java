package com.threatscopebackend.entity.postgresql;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "roles")
@Data
@NoArgsConstructor
public class Role {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, unique = true, nullable = false)
    private RoleName name;

    @ManyToMany(mappedBy = "roles")
    private Set<User> users = new HashSet<>();

    public Role(RoleName name) {
        this.name = name;
    }

    public enum RoleName {
        ROLE_USER,
        ROLE_ADMIN,
        ROLE_MODERATOR,
        ROLE_ANALYST,
        ROLE_ENTERPRISE
    }
}

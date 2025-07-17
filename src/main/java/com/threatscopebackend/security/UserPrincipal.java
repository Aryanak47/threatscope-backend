package com.threatscopebackend.security;

import com.fasterxml.jackson.annotation.JsonIgnore;

import com.threatscopebackend.entity.postgresql.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Getter
public class UserPrincipal implements UserDetails {
    private static final long serialVersionUID = 1L;

    private final Long id;
    private final String name;
    private final String username;
    private final String email;
    
    @JsonIgnore
    private final String password;
    
    private final Collection<? extends GrantedAuthority> authorities;
    private final boolean enabled;
    private final boolean accountNonExpired;
    private final boolean accountNonLocked;
    private final boolean credentialsNonExpired;

    public UserPrincipal(Long id, String name, String username, String email, String password, 
                        Collection<? extends GrantedAuthority> authorities, boolean enabled) {
        this.id = id;
        this.name = name;
        this.username = username;
        this.email = email;
        this.password = password;
        this.authorities = authorities;
        this.enabled = enabled;
        this.accountNonExpired = true;
        this.accountNonLocked = true;
        this.credentialsNonExpired = true;
    }

    public static UserPrincipal create(User user) {
        List<GrantedAuthority> authorities;
        
        // Handle roles collection safely to avoid ConcurrentModificationException
        if (user.getRoles() != null && !user.getRoles().isEmpty()) {
            // Create a defensive copy to avoid concurrent modification
            authorities = user.getRoles().stream()
                    .map(role -> new SimpleGrantedAuthority(role.getName().name()))
                    .collect(Collectors.toList());
        } else {
            // Default role if no roles are assigned
            authorities = Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"));
        }

        return new UserPrincipal(
                user.getId(),
                user.getFirstName() + " " + user.getLastName(),
                user.getEmail(),
                user.getEmail(),
                user.getPassword(),
                authorities,
                user.isActive() && user.isEmailVerified()
        );
    }

    public static UserPrincipal create(Long id, String email) {
        return new UserPrincipal(
                id,
                null,
                email,
                email,
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")),
                true
        );
    }

    @Override
    public boolean isAccountNonExpired() {
        return accountNonExpired;
    }

    @Override
    public boolean isAccountNonLocked() {
        return accountNonLocked;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return credentialsNonExpired;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserPrincipal that = (UserPrincipal) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
    
    /**
     * Creates an anonymous user with minimal privileges
     * @return UserPrincipal representing an anonymous user
     */
    public static UserPrincipal anonymousUser() {
        return new UserPrincipal(
                null, // id
                "Anonymous User",
                "anonymous@threatscope.com",
                "anonymous@threatscope.com",
                null, // no password
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_ANONYMOUS")),
                true
        );
    }
}

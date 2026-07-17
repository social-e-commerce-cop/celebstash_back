package com.celebstash.backend.model;

import com.celebstash.backend.model.enums.AccountStatus;
import com.celebstash.backend.model.enums.AuthProvider;
import com.celebstash.backend.model.enums.Gender;
import com.celebstash.backend.model.enums.Role;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "users")
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fullName;

    @Column(unique = true)
    private String username;

    @Column(unique = true)
    private String email;

    @Column(unique = true)
    private String phoneNumber;

    @Column(nullable = false)
    private String password;

    @Column(length = 1000)
    private String bio;

    private String profilePicture;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    @Enumerated(EnumType.STRING)
    private AuthProvider provider;

    private String providerId;

    @Enumerated(EnumType.STRING)
    private Role role;

    @Enumerated(EnumType.STRING)
    private AccountStatus status;

    private boolean emailVerified;
    private boolean phoneVerified;

    @Column(nullable = false)
    @ColumnDefault("false")
    private boolean accountVerified = false;


    private LocalDateTime accountVerifiedAt;

    private String fandomName;

    @PrePersist
    @PreUpdate
    private void updateAccountVerifiedAt() {
        if (accountVerified && accountVerifiedAt == null) {
            accountVerifiedAt = LocalDateTime.now();
        }
    }

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private UserSettings settings;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getUsername() {
        // Return the username field if set, otherwise fall back to email or phone
        return username != null ? username : (email != null ? email : phoneNumber);
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !AccountStatus.LOCKED.equals(status);
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return AccountStatus.VERIFIED.equals(status) || AccountStatus.ACTIVE.equals(status);
    }
}

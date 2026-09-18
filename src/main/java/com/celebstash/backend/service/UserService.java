package com.celebstash.backend.service;

import com.celebstash.backend.dto.user.FollowUserDTO;
import com.celebstash.backend.dto.user.UserPublicDTO;
import com.celebstash.backend.dto.user.UserProfileUpdateRequest;
import com.celebstash.backend.exception.AppException;
import com.celebstash.backend.model.User;
import com.celebstash.backend.model.enums.AccountStatus;
import com.celebstash.backend.model.enums.AuthProvider;
import com.celebstash.backend.model.enums.Role;
import com.celebstash.backend.repository.FollowerRepository;
import com.celebstash.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final FollowerRepository followerRepository;

    @Transactional(readOnly = true)
    public List<UserPublicDTO> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::toPublicDTO)
                .collect(Collectors.toList());
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByEmailOrPhoneOrUsername(username)
                .orElseThrow(() ->
                        new UsernameNotFoundException(
                                "User not found with email, phone or username: " + username
                        )
                );
    }

    @Transactional
    public User createUser(
            String fullName,
            String username,
            String identifier,
            String password,
            boolean isEmail
    ) {
        Optional<User> existingOpt = isEmail
                ? userRepository.findByEmail(identifier)
                : userRepository.findByPhoneNumber(identifier);

        User user;

        if (existingOpt.isPresent()) {
            user = existingOpt.get();

            if (fullName != null && !fullName.isBlank()) {
                user.setFullName(fullName);
            }

            if (username != null && !username.isBlank()) {
                user.setUsername(username);
            }

            if (password != null && !password.isBlank()) {
                user.setPassword(passwordEncoder.encode(password));
            }

        } else {
            user = User.builder()
                    .fullName(fullName)
                    .username(username)
                    .password(passwordEncoder.encode(password))
                    .role(Role.USER)
                    .provider(AuthProvider.LOCAL)
                    .status(AccountStatus.PENDING)
                    .build();

            if (isEmail) {
                user.setEmail(identifier);
                user.setEmailVerified(false);
            } else {
                user.setPhoneNumber(identifier);
                user.setPhoneVerified(false);
            }
        }

        return userRepository.save(user);
    }

    @Transactional
    public User updateUserStatus(User user, AccountStatus status) {
        user.setStatus(status);
        return userRepository.save(user);
    }

    @Transactional
    public User verifyUser(User user, boolean isEmail) {
        if (isEmail) {
            user.setEmailVerified(true);
        } else {
            user.setPhoneVerified(true);
        }

        user.setStatus(AccountStatus.VERIFIED);

        return userRepository.save(user);
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public Optional<User> findByPhoneNumber(String phoneNumber) {
        return userRepository.findByPhoneNumber(phoneNumber);
    }

    public Optional<User> findByEmailOrPhoneNumber(String identifier) {
        return userRepository.findByEmailOrPhoneNumber(identifier, identifier);
    }

    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    public boolean existsByPhoneNumber(String phoneNumber) {
        return userRepository.existsByPhoneNumber(phoneNumber);
    }

    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    @Transactional
    public User updatePassword(User user, String newPassword) {
        user.setPassword(passwordEncoder.encode(newPassword));
        return userRepository.save(user);
    }

    /**
     * Get the currently authenticated user
     *
     * @return the current user
     * @throws AppException if no user is authenticated
     */
    public User getCurrentUser() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AppException(
                    "Not authenticated",
                    HttpStatus.UNAUTHORIZED
            );
        }

        Object principal = authentication.getPrincipal();
        String username;

        if (principal instanceof UserDetails) {
            username = ((UserDetails) principal).getUsername();
        } else {
            username = principal.toString();
        }

        return userRepository.findByEmailOrPhoneOrUsername(username)
                .orElseThrow(() ->
                        new AppException(
                                "User not found",
                                HttpStatus.NOT_FOUND
                        )
                );
    }

    @Transactional
    public User updateProfile(UserProfileUpdateRequest request) {
        User currentUser = getCurrentUser();

        if (request.getFullName() != null) {
            currentUser.setFullName(request.getFullName());
        }

        if (request.getUsername() != null && !request.getUsername().isBlank()) {
            String newUsername = request.getUsername().trim();
            if (!newUsername.equalsIgnoreCase(currentUser.getUsername()) && userRepository.existsByUsername(newUsername)) {
                throw new AppException("Username is already taken", HttpStatus.BAD_REQUEST);
            }
            currentUser.setUsername(newUsername);
        }

        if (request.getGender() != null) {
            currentUser.setGender(request.getGender());
        }

        if (request.getBio() != null) {
            currentUser.setBio(request.getBio());
        }

        if (request.getProfilePicture() != null) {
            currentUser.setProfilePicture(request.getProfilePicture());
        }

        if (request.getFandomName() != null) {
            currentUser.setFandomName(request.getFandomName());
        }

        return userRepository.save(currentUser);
    }

    public User getUserFromPrincipal(java.security.Principal principal) {
        if (principal == null) {
            return getCurrentUser();
        }

        String identifier = principal.getName();

        return userRepository.findByEmailOrPhoneOrUsername(identifier)
                .orElseGet(this::getCurrentUser);
    }

    @Transactional(readOnly = true)
    public UserPublicDTO getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new AppException("User not found", HttpStatus.NOT_FOUND));
        return toPublicDTO(user);
    }

    @Transactional(readOnly = true)
    public UserPublicDTO getUserByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AppException("User not found", HttpStatus.NOT_FOUND));
        return toPublicDTO(user);
    }

    public UserPublicDTO toPublicDTO(User user) {
        long followersCount = followerRepository.countByFollowing(user);
        long followingCount = followerRepository.countByFollower(user);
        java.time.LocalDateTime createdAtDate = user.getAccountVerifiedAt() != null
                ? user.getAccountVerifiedAt()
                : java.time.LocalDateTime.of(2026, 1, 1, 0, 0);
        return UserPublicDTO.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .username(user.getUsername())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .bio(user.getBio())
                .profilePicture(user.getProfilePicture())
                .role(user.getRole() != null ? user.getRole().name() : "USER")
                .status(user.getStatus() != null ? user.getStatus().name() : "ACTIVE")
                .accountVerified(user.isAccountVerified())
                .fandomName(user.getFandomName())
                .followersCount(followersCount)
                .followingCount(followingCount)
                .createdAt(createdAtDate)
                .build();
    }

    @Transactional(readOnly = true)
    public List<UserPublicDTO> searchUsers(String query) {
        if (query == null || query.isBlank()) {
            return userRepository.findAll().stream().limit(20).map(this::toPublicDTO).collect(Collectors.toList());
        }
        String q = query.trim().toLowerCase();
        return userRepository.findAll().stream()
                .filter(u -> (u.getUsername() != null && u.getUsername().toLowerCase().contains(q)) ||
                             (u.getFullName() != null && u.getFullName().toLowerCase().contains(q)))
                .limit(20)
                .map(this::toPublicDTO)
                .collect(Collectors.toList());
    }
}
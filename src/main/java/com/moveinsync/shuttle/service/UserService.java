package com.moveinsync.shuttle.service;

import com.moveinsync.shuttle.domain.AppUser;
import com.moveinsync.shuttle.domain.Role;
import com.moveinsync.shuttle.domain.Tier;
import com.moveinsync.shuttle.dto.Views.AuthResponse;
import com.moveinsync.shuttle.dto.Views.UserView;
import com.moveinsync.shuttle.exception.ApiException;
import com.moveinsync.shuttle.repository.UserRepository;
import com.moveinsync.shuttle.security.JwtService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public UserService(UserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    @Transactional
    public UserView create(String username, String password, String phone, String costCentre, Tier tier, Role role) {
        String name = username.trim().toLowerCase();
        if (users.existsByUsername(name)) {
            throw ApiException.conflict("USERNAME_TAKEN", "Username is already taken");
        }
        try {
            return UserView.from(users.saveAndFlush(new AppUser(name, encoder.encode(password), role,
                    costCentre.trim().toUpperCase(), tier == null ? Tier.STANDARD : tier, phone)));
        } catch (DataIntegrityViolationException e) { // lost a registration race
            throw ApiException.conflict("USERNAME_TAKEN", "Username is already taken");
        }
    }

    @Transactional(readOnly = true)
    public AuthResponse login(String username, String password) {
        AppUser user = users.findByUsername(username.trim().toLowerCase()).orElse(null);
        // identical message for unknown user / wrong password: no user enumeration
        if (user == null || !encoder.matches(password, user.getPasswordHash())) {
            throw ApiException.unauthorized("Invalid username or password");
        }
        return new AuthResponse(jwt.generate(user), "Bearer", jwt.ttlMinutes(), user.getId(), user.getUsername(), user.getRole());
    }
}

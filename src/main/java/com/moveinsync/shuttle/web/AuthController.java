package com.moveinsync.shuttle.web;

import com.moveinsync.shuttle.domain.Role;
import com.moveinsync.shuttle.dto.Requests.LoginRequest;
import com.moveinsync.shuttle.dto.Requests.RegisterRequest;
import com.moveinsync.shuttle.dto.Views.AuthResponse;
import com.moveinsync.shuttle.dto.Views.UserView;
import com.moveinsync.shuttle.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserService users;

    public AuthController(UserService users) {
        this.users = users;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserView register(@Valid @RequestBody RegisterRequest r) {
        return users.create(r.username(), r.password(), r.phone(), r.costCentre(), r.tier(), Role.EMPLOYEE);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest r) {
        return users.login(r.username(), r.password());
    }
}

package com.moveinsync.shuttle.security;

import com.moveinsync.shuttle.domain.Role;

public record AuthPrincipal(Long userId, String username, Role role) {}

package com.moveinsync.shuttle.config;

import com.moveinsync.shuttle.domain.AppUser;
import com.moveinsync.shuttle.domain.Role;
import com.moveinsync.shuttle.domain.Tier;
import com.moveinsync.shuttle.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DataSeeder implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private final UserRepository users;
    private final PasswordEncoder encoder;

    public DataSeeder(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @Override
    public void run(String... args) {
        seed("admin", "Nitin7880", Role.ADMIN, "IT", Tier.PLATINUM, "+917880314386");
        seed("approver", "Approver7880", Role.APPROVER, "IT", Tier.GOLD, "+919835678675");
        seed("suraj", "Suraj7880", Role.EMPLOYEE, "SALES", Tier.PLATINUM, "+919675487654");
        seed("rahul", "Rahul7880", Role.EMPLOYEE, "SALES", Tier.GOLD, "+917456764589");
        seed("ajay", "Ajay7880", Role.EMPLOYEE, "SALES", Tier.STANDARD, "+919563210876");
        seed("mohit", "Mohit7880", Role.EMPLOYEE, "MARKETING", Tier.STANDARD, "+919567435876");
        seed("aman", "Aman7880", Role.EMPLOYEE, "MARKETING", Tier.SILVER, "+917564365789");
        seed("raj", "Raj7880", Role.EMPLOYEE, "FINANCE", Tier.STANDARD, "+919764589625");
        seed("himanshu", "Himanshu7880", Role.EMPLOYEE, "FINANCE", Tier.GOLD, "+917543680965");
    }

    private void seed(String username, String password, Role role, String cc, Tier tier, String phone) {
        if (!users.existsByUsername(username)) {
            users.save(new AppUser(username, encoder.encode(password), role, cc, tier, phone));
            log.info("event=seeded_user username={} role={}", username, role);
        }
    }
}
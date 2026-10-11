package com.moveinsync.shuttle.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "app_user", uniqueConstraints = @UniqueConstraint(name = "uk_user_username", columnNames = "username"))
public class AppUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false, length = 50)
    private String username;

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false, length = 50)
    private String costCentre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Tier tier;

    @Column(nullable = false, length = 20)
    private String phone;

    protected AppUser() {}

    public AppUser(String username, String passwordHash, Role role, String costCentre, Tier tier, String phone) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.costCentre = costCentre;
        this.tier = tier;
        this.phone = phone;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
    public String getCostCentre() { return costCentre; }
    public Tier getTier() { return tier; }
    public String getPhone() { return phone; }
}

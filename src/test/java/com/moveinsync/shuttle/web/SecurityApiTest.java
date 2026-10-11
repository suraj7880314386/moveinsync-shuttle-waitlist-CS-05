package com.moveinsync.shuttle.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moveinsync.shuttle.domain.AppUser;
import com.moveinsync.shuttle.domain.Role;
import com.moveinsync.shuttle.domain.Tier;
import com.moveinsync.shuttle.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:sectest;LOCK_TIMEOUT=30000;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;

    @BeforeEach
    void admin() {
        if (!users.existsByUsername("testadmin")) {
            users.save(new AppUser("testadmin", encoder.encode("Admin@1234"), Role.ADMIN, "HQ", Tier.PLATINUM, "+919999999999"));
        }
    }

    private String login(String user, String pass) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + user + "\",\"password\":\"" + pass + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("token").asText();
    }

    @Test
    void protectedEndpointWithoutTokenIs401() throws Exception {
        mvc.perform(get("/api/trips")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void wrongPasswordIs401WithGenericMessage() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"testadmin\",\"password\":\"nope-nope\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    void employeeCannotCreateTripButAdminCan() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"emp-sec\",\"password\":\"Passw0rd!\",\"phone\":\"+919811111111\",\"costCentre\":\"eng\"}"))
                .andExpect(status().isCreated());
        String empToken = login("emp-sec", "Passw0rd!");
        String adminToken = login("testadmin", "Admin@1234");
        Instant dep = Instant.now().plus(3, ChronoUnit.HOURS);
        String trip = "{\"routeName\":\"Sec Test\",\"departureTime\":\"" + dep + "\",\"checkInCutoff\":\"" + dep.minus(30, ChronoUnit.MINUTES) + "\",\"capacity\":2}";

        mvc.perform(post("/api/admin/trips").header("Authorization", "Bearer " + empToken)
                .contentType(MediaType.APPLICATION_JSON).content(trip)).andExpect(status().isForbidden());

        String created = mvc.perform(post("/api/admin/trips").header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(trip)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = mapper.readTree(created);
        long tripId = node.get("id").asLong();

        mvc.perform(post("/api/trips/" + tripId + "/bookings").header("Authorization", "Bearer " + empToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"seats\":1}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void validationErrorsAre400WithFieldDetails() throws Exception {
        String adminToken = login("testadmin", "Admin@1234");
        mvc.perform(post("/api/admin/trips").header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"routeName\":\"\",\"capacity\":0}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}

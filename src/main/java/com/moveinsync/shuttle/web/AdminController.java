package com.moveinsync.shuttle.web;

import com.moveinsync.shuttle.dto.Requests.CreateUserRequest;
import com.moveinsync.shuttle.dto.Views.*;
import com.moveinsync.shuttle.notification.NotificationService;
import com.moveinsync.shuttle.domain.Role;
import com.moveinsync.shuttle.service.BackupService;
import com.moveinsync.shuttle.service.ReconciliationService;
import com.moveinsync.shuttle.service.UserService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    private final UserService users;
    private final NotificationService notifications;
    private final BackupService backups;
    private final ReconciliationService reconciliation;

    public AdminController(UserService users, NotificationService notifications, BackupService backups,
                           ReconciliationService reconciliation) {
        this.users = users;
        this.notifications = notifications;
        this.backups = backups;
        this.reconciliation = reconciliation;
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public UserView createUser(@Valid @RequestBody CreateUserRequest r) {
        return users.create(r.username(), r.password(), r.phone(), r.costCentre(), r.tier(), r.role() == null ? Role.EMPLOYEE : r.role());
    }

    @GetMapping("/notifications")
    public List<NotificationView> notifications() {
        return notifications.recent().stream().map(NotificationView::from).toList();
    }

    @PostMapping("/backup")
    public BackupResult backup() {
        return new BackupResult(backups.backup());
    }

    @PostMapping("/reconcile")
    public ReconcileResult reconcile() {
        return reconciliation.reconcileAll();
    }
}

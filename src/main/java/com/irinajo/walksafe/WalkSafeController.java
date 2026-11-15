package com.irinajo.walksafe;

import com.irinajo.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class WalkSafeController {

    public record NewContact(@NotBlank @Size(max = 60) String name, @NotBlank String phone, boolean isDefault) {}
    public record UpdateContact(@Size(max = 60) String name, Boolean isDefault) {}
    public record StartSession(@NotBlank @Size(max = 64) String clientSessionId) {}
    public record PointsBatch(@NotNull @Size(max = 120) List<WalkSafeService.Point> points) {}

    private final WalkSafeService service;

    public WalkSafeController(WalkSafeService service) {
        this.service = service;
    }

    @GetMapping("/walk-safe/contacts")
    public List<WalkSafeService.ContactDto> list(@AuthenticationPrincipal Jwt jwt) {
        return service.list(CurrentUser.id(jwt));
    }

    @PostMapping("/walk-safe/contacts")
    public WalkSafeService.ContactDto add(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody NewContact body) {
        return service.add(CurrentUser.id(jwt), body.name(), body.phone(), body.isDefault());
    }

    @PatchMapping("/walk-safe/contacts/{id}")
    public WalkSafeService.ContactDto update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                             @Valid @RequestBody UpdateContact body) {
        return service.update(CurrentUser.id(jwt), id, body.name(), body.isDefault());
    }

    /** Idempotent: deleting a contact that's already gone still returns 204. */
    @DeleteMapping("/walk-safe/contacts/{id}")
    public ResponseEntity<Void> remove(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.remove(CurrentUser.id(jwt), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/walk-safe/contacts/{id}/reset-link")
    public WalkSafeService.ContactDto resetLink(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.resetLink(CurrentUser.id(jwt), id);
    }

    @PostMapping("/walk-safe/sessions")
    public WalkSafeService.SessionDto start(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody StartSession body) {
        return service.start(CurrentUser.id(jwt), body.clientSessionId());
    }

    @PostMapping("/walk-safe/sessions/{id}/points")
    public WalkSafeService.PointsResult points(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                               @Valid @RequestBody PointsBatch body) {
        return service.addPoints(CurrentUser.id(jwt), id, body.points());
    }

    @PostMapping("/walk-safe/sessions/{id}/end")
    public WalkSafeService.SessionDto end(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.end(CurrentUser.id(jwt), id);
    }
}

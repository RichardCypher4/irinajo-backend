package com.irinajo.sync;

import com.irinajo.security.CurrentUser;
import com.irinajo.sync.SyncDtos.Changes;
import com.irinajo.sync.SyncDtos.UploadBatch;
import com.irinajo.sync.SyncDtos.UploadResult;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SyncController {
    private final SyncService sync;

    public SyncController(SyncService sync) {
        this.sync = sync;
    }

    /** Batch of journey upserts/deletes and earned keepsakes. Safe to retry. */
    @PostMapping("/sync/journeys")
    public UploadResult upload(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UploadBatch batch) {
        return sync.upload(CurrentUser.id(jwt), batch);
    }

    @GetMapping("/sync/changes")
    public Changes changes(@AuthenticationPrincipal Jwt jwt,
                           @RequestParam(defaultValue = "0") long since,
                           @RequestParam(defaultValue = "200") int limit,
                           @RequestParam(defaultValue = "all") String scope) {
        return sync.changes(CurrentUser.id(jwt), since, limit, scope);
    }
}

package com.irinajo.admin;

import com.irinajo.common.ApiException;
import com.irinajo.common.ChangeSequence;
import com.irinajo.content.Tip;
import com.irinajo.content.TipRepository;
import com.irinajo.content.VoicePack;
import com.irinajo.content.VoicePackRepository;
import com.irinajo.explore.CuratedWalk;
import com.irinajo.explore.CuratedWalkRepository;
import com.irinajo.geo.LatLng;
import com.irinajo.geo.Polyline;
import com.irinajo.storage.StorageService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Simple admin API (ROLE_ADMIN only) to edit tips, curated walks and voice packs without an app release.
 * Make someone an admin with: UPDATE users SET role = 'ADMIN' WHERE phone = '+234...';
 */
@RestController
@RequestMapping("/admin")
public class AdminController {

    public record TipInput(@NotBlank @Pattern(regexp = "^(en|pcm)$") String language,
                           @NotBlank @Size(max = 30) String category,
                           @Size(max = 120) String title,
                           @NotBlank @Size(max = 2000) String body) {}

    public record WalkInput(@NotBlank @Size(max = 120) String name,
                            @NotBlank @Pattern(regexp = "^(PARKS|CAFES|LANDMARKS|QUIET)$") String category,
                            @NotNull @DecimalMin("0.1") BigDecimal distanceKm,
                            @Min(1) int estMinutes,
                            @NotBlank @Pattern(regexp = "^(EASY|MODERATE|HARD)$") String difficulty,
                            @NotBlank @Pattern(regexp = "^(GOOD|FAIR|TAKE_CARE)$") String safetyDay,
                            @NotBlank @Pattern(regexp = "^(GOOD|FAIR|TAKE_CARE)$") String safetyNight,
                            @NotBlank String polyline,
                            boolean loop,
                            boolean active) {}

    private final TipRepository tips;
    private final CuratedWalkRepository walks;
    private final VoicePackRepository packs;
    private final StorageService storage;
    private final ChangeSequence seq;

    public AdminController(TipRepository tips, CuratedWalkRepository walks, VoicePackRepository packs,
                           StorageService storage, ChangeSequence seq) {
        this.tips = tips;
        this.walks = walks;
        this.packs = packs;
        this.storage = storage;
        this.seq = seq;
    }

    // ---------- Tips ----------

    @GetMapping("/tips")
    public List<Tip> listTips() {
        return tips.findAll().stream().filter(t -> !t.isDeleted()).toList();
    }

    @PostMapping("/tips")
    public Tip createTip(@Valid @RequestBody TipInput in) {
        Tip t = new Tip();
        apply(t, in);
        return tips.save(t);
    }

    @PutMapping("/tips/{id}")
    public Tip updateTip(@PathVariable UUID id, @Valid @RequestBody TipInput in) {
        Tip t = tips.findById(id).orElseThrow(() -> ApiException.notFound("TIP_NOT_FOUND", "Tip not found"));
        apply(t, in);
        return tips.save(t);
    }

    /** Soft delete so the app learns about it in the next versioned download. */
    @DeleteMapping("/tips/{id}")
    public ResponseEntity<Void> deleteTip(@PathVariable UUID id) {
        tips.findById(id).ifPresent(t -> {
            t.setDeleted(true);
            t.setVersion(seq.nextContentVersion());
            tips.save(t);
        });
        return ResponseEntity.noContent().build();
    }

    private void apply(Tip t, TipInput in) {
        t.setLanguage(in.language());
        t.setCategory(in.category());
        t.setTitle(in.title());
        t.setBody(in.body());
        t.setDeleted(false);
        t.setVersion(seq.nextContentVersion());
    }

    // ---------- Curated walks ----------

    @GetMapping("/walks")
    public List<CuratedWalk> listWalks() {
        return walks.findAll();
    }

    @PostMapping("/walks")
    public CuratedWalk createWalk(@Valid @RequestBody WalkInput in) {
        CuratedWalk w = new CuratedWalk();
        apply(w, in);
        return walks.save(w);
    }

    @PutMapping("/walks/{id}")
    public CuratedWalk updateWalk(@PathVariable UUID id, @Valid @RequestBody WalkInput in) {
        CuratedWalk w = walks.findById(id).orElseThrow(() -> ApiException.notFound("WALK_NOT_FOUND", "Walk not found"));
        apply(w, in);
        return walks.save(w);
    }

    @DeleteMapping("/walks/{id}")
    public ResponseEntity<Void> deleteWalk(@PathVariable UUID id) {
        walks.findById(id).ifPresent(w -> {
            w.setActive(false);
            walks.save(w);
        });
        return ResponseEntity.noContent().build();
    }

    private void apply(CuratedWalk w, WalkInput in) {
        List<LatLng> pts = Polyline.decode(in.polyline());
        if (pts.size() < 2) throw ApiException.badRequest("POLYLINE_INVALID", "Route needs at least 2 points");
        w.setName(in.name());
        w.setCategory(in.category());
        w.setDistanceKm(in.distanceKm());
        w.setEstMinutes(in.estMinutes());
        w.setDifficulty(in.difficulty());
        w.setSafetyDay(in.safetyDay());
        w.setSafetyNight(in.safetyNight());
        w.setPolyline(in.polyline());
        w.setStartLat(pts.get(0).lat());
        w.setStartLng(pts.get(0).lng());
        w.setLoop(in.loop());
        w.setActive(in.active());
        w.setRatedOn(LocalDate.now());
    }

    // ---------- Voice packs ----------

    /** Upload a finished pack (Opus). Bumps the version so phones download the new recording. */
    @PostMapping(value = "/voice-packs", consumes = "multipart/form-data")
    @Transactional
    public VoicePack uploadVoicePack(@RequestParam @Pattern(regexp = "^(ada|tunde)$") String speaker,
                                     @RequestParam @Pattern(regexp = "^(en|pcm)$") String language,
                                     @RequestPart("file") MultipartFile file) throws IOException {
        if (!speaker.matches("^(ada|tunde)$") || !language.matches("^(en|pcm)$")) {
            throw ApiException.badRequest("VOICE_PACK_INVALID", "speaker must be ada|tunde and language en|pcm");
        }
        byte[] bytes = file.getBytes();
        VoicePack p = packs.findBySpeakerAndLanguage(speaker, language).orElseGet(() -> {
            VoicePack n = new VoicePack();
            n.setSpeaker(speaker);
            n.setLanguage(language);
            return n;
        });
        int version = p.getVersion() + 1;
        String key = "voice/" + speaker + "-" + language + "-v" + version + ".opus";
        storage.put(key, bytes, "audio/ogg");
        p.setVersion(version);
        p.setObjectKey(key);
        p.setSizeBytes(bytes.length);
        p.setSha256(sha256Hex(bytes));
        return packs.save(p);
    }

    private static String sha256Hex(byte[] b) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(b));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

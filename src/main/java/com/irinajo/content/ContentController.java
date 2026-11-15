package com.irinajo.content;

import com.irinajo.storage.StorageService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/** Public, versioned content: the app only downloads what changed since its version. */
@RestController
public class ContentController {

    public record TipDto(UUID id, String language, String category, String title, String body) {}
    public record TipsResponse(long version, boolean upToDate, List<TipDto> upserts, List<UUID> deleted) {}
    public record VoicePackDto(String speaker, String language, int version, String url, long sizeBytes, String sha256) {}

    private final TipRepository tips;
    private final VoicePackRepository packs;
    private final StorageService storage;

    public ContentController(TipRepository tips, VoicePackRepository packs, StorageService storage) {
        this.tips = tips;
        this.packs = packs;
        this.storage = storage;
    }

    @GetMapping("/content/tips")
    public ResponseEntity<TipsResponse> tips(@RequestParam(defaultValue = "0") long version) {
        long current = tips.currentVersion();
        if (version >= current) {
            return cached(new TipsResponse(current, true, List.of(), List.of()));
        }
        var changed = tips.changedSince(version);
        List<TipDto> upserts = changed.stream().filter(t -> !t.isDeleted())
                .map(t -> new TipDto(t.getId(), t.getLanguage(), t.getCategory(), t.getTitle(), t.getBody())).toList();
        List<UUID> deleted = changed.stream().filter(Tip::isDeleted).map(Tip::getId).toList();
        return cached(new TipsResponse(current, false, upserts, deleted));
    }

    /** Manifest the app checks; a re-recorded pack gets a new version and reaches phones without an app update. */
    @GetMapping("/content/voice-packs")
    public ResponseEntity<List<VoicePackDto>> voicePacks() {
        var list = packs.findAll().stream()
                .map(p -> new VoicePackDto(p.getSpeaker(), p.getLanguage(), p.getVersion(),
                        storage.publicUrl(p.getObjectKey()), p.getSizeBytes(), p.getSha256()))
                .toList();
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic()).body(list);
    }

    private static <T> ResponseEntity<T> cached(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePublic()).body(body);
    }
}

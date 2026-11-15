package com.irinajo.account;

import com.irinajo.auth.OtpService;
import com.irinajo.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Web page for account deletion requests (Google Play requirement). The person proves they own
 * the number with a one-time code, then the account is deleted straight away.
 */
@RestController
public class DeletionPageController {

    public record ConfirmRequest(@NotBlank String phone, @NotBlank String code) {}

    private final OtpService otp;
    private final UserRepository users;
    private final AccountService account;

    public DeletionPageController(OtpService otp, UserRepository users, AccountService account) {
        this.otp = otp;
        this.users = users;
        this.account = account;
    }

    @GetMapping(value = "/account/delete", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> page() {
        return ResponseEntity.ok().header("X-Robots-Tag", "noindex").body(PAGE);
    }

    @PostMapping("/account/delete/confirm")
    public Map<String, Object> confirm(@Valid @RequestBody ConfirmRequest body) {
        String phone = otp.verify(body.phone(), body.code());
        users.findByPhone(phone).ifPresent(u -> account.delete(u.getId()));
        // Same answer whether or not an account existed, so the page can't be used to probe numbers.
        return Map.of("deleted", true);
    }

    private static final String PAGE = """
            <!doctype html><html lang="en"><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1"><meta name="robots" content="noindex">
            <title>Delete your Irin Ajo account</title>
            <style>body{font-family:system-ui,sans-serif;max-width:420px;margin:40px auto;padding:0 16px;color:#1d2a22}
            input,button{width:100%;padding:12px;margin:6px 0;font-size:16px;box-sizing:border-box}
            button{background:#b3261e;color:#fff;border:0;border-radius:8px}#msg{margin-top:12px}</style></head>
            <body><h1>Delete your Irin Ajo account</h1>
            <p>This permanently deletes your profile, photos, journeys, keepsakes and Walk Safe data. It can't be undone.</p>
            <p>You can also delete your account inside the app: Profile &rarr; Settings &rarr; Delete account.</p>
            <label>Phone number<input id="phone" placeholder="+2348012345678" inputmode="tel"></label>
            <button id="send" type="button">Send code</button>
            <label>6-digit code<input id="code" inputmode="numeric" maxlength="6"></label>
            <button id="del" type="button">Delete my account</button>
            <div id="msg"></div>
            <script>
            const msg=t=>document.getElementById('msg').textContent=t;
            const post=(u,b)=>fetch(u,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(b)})
              .then(async r=>{const j=await r.json().catch(()=>({}));if(!r.ok)throw new Error((j.error&&j.error.message)||'Error');return j;});
            document.getElementById('send').onclick=()=>post('/auth/otp/request',{phone:phone.value,channel:'sms'})
              .then(()=>msg('Code sent. Enter it above.')).catch(e=>msg(e.message));
            document.getElementById('del').onclick=()=>{if(!confirm('Delete your account permanently?'))return;
              post('/account/delete/confirm',{phone:phone.value,code:code.value})
              .then(()=>msg('Your account has been deleted.')).catch(e=>msg(e.message));};
            </script></body></html>
            """;
}

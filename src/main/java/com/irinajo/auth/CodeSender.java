package com.irinajo.auth;

import com.irinajo.common.ApiException;
import com.irinajo.common.Mask;
import com.irinajo.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Sends one-time codes. SMS via Termii (or "log" in development); WhatsApp via the
 * WhatsApp Business Platform (Cloud API) using an approved authentication template.
 */
@Component
public class CodeSender {
    private static final Logger log = LoggerFactory.getLogger(CodeSender.class);

    private final AppProperties props;
    private final RestClient http;

    public CodeSender(AppProperties props, RestClient http) {
        this.props = props;
        this.http = http;
    }

    public void send(String phone, String code, String channel) {
        if ("whatsapp".equals(channel) && props.whatsapp().enabled()) {
            sendWhatsapp(phone, code);
        } else {
            sendSms(phone, code);
        }
    }

    private void sendSms(String phone, String code) {
        String provider = props.sms().provider();
        if ("log".equalsIgnoreCase(provider)) {
            log.info("[DEV SMS] code for {} is {}", Mask.phone(phone), code);
            return;
        }
        if ("termii".equalsIgnoreCase(provider)) {
            try {
                http.post()
                        .uri(props.sms().termiiBaseUrl() + "/api/sms/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of(
                                "api_key", props.sms().termiiApiKey(),
                                "to", phone.substring(1),                 // Termii expects 234...
                                "from", props.sms().termiiSenderId(),
                                "sms", "Your Irin Ajo code is " + code + ". It expires in 10 minutes. Don't share it.",
                                "type", "plain",
                                "channel", "dnd"))                         // DND route reaches numbers on DND
                        .retrieve()
                        .toBodilessEntity();
                return;
            } catch (Exception e) {
                log.error("Termii send failed for {}: {}", Mask.phone(phone), e.getMessage());
                throw ApiException.upstream("SMS_SEND_FAILED", "We couldn't send the code. Try again or use WhatsApp.");
            }
        }
        throw new IllegalStateException("Unknown SMS provider: " + provider);
    }

    private void sendWhatsapp(String phone, String code) {
        var wa = props.whatsapp();
        Map<String, Object> body = Map.of(
                "messaging_product", "whatsapp",
                "to", phone.substring(1),
                "type", "template",
                "template", Map.of(
                        "name", wa.templateName(),
                        "language", Map.of("code", "en"),
                        "components", List.of(
                                Map.of("type", "body", "parameters", List.of(Map.of("type", "text", "text", code))),
                                Map.of("type", "button", "sub_type", "url", "index", "0",
                                        "parameters", List.of(Map.of("type", "text", "text", code))))));
        try {
            http.post()
                    .uri("https://graph.facebook.com/v21.0/" + wa.phoneNumberId() + "/messages")
                    .header("Authorization", "Bearer " + wa.accessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.error("WhatsApp send failed for {}: {}", Mask.phone(phone), e.getMessage());
            throw ApiException.upstream("WHATSAPP_SEND_FAILED", "We couldn't send the code on WhatsApp. Try SMS.");
        }
    }
}

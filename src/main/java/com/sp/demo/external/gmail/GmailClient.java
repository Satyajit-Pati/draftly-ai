package com.sp.demo.external.gmail;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartBody;
import com.google.api.services.gmail.model.MessagePartHeader;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.sp.demo.domain.entity.OAuthToken;
import com.sp.demo.external.google.TokenRefreshService;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GmailClient {

  private final TokenRefreshService tokenRefreshService;

  public record GmailMessageDetails(
      String gmailMessageId,
      String threadId,
      String from,
      String subject,
      String messageIdHeader,
      String referencesHeader,
      String bodyText,
      String snippet
  ) {
  }

  public String sendEmail(OAuthToken token,
      String to,
      String subject,
      String body) throws Exception {

    try {
      return sendWithAccessToken(
          token,
          to,
          subject,
          body
      );

    } catch (Exception ex) {

      // ⭐ refresh token
      token = tokenRefreshService.refreshToken(token);

      // ⭐ retry ONCE
      return sendWithAccessToken(
          token,
          to,
          subject,
          body
      );
    }
  }

  public String sendReply(OAuthToken token, GmailMessageDetails original, String replyBody) throws Exception {
    try {
      return sendReplyWithAccessToken(token, original, replyBody);
    } catch (Exception ex) {
      token = tokenRefreshService.refreshToken(token);
      return sendReplyWithAccessToken(token, original, replyBody);
    }
  }

  public List<GmailMessageDetails> fetchUnread(OAuthToken token, long maxResults) throws Exception {
    try {
      return fetchUnreadWithAccessToken(token, maxResults);
    } catch (Exception ex) {
      token = tokenRefreshService.refreshToken(token);
      return fetchUnreadWithAccessToken(token, maxResults);
    }
  }


  private Gmail gmailService(OAuthToken token) {
    GoogleCredentials credentials = GoogleCredentials.create(
        new AccessToken(token.getAccessToken(), token.getExpiresAt() == null ? null : Date.from(token.getExpiresAt())));

    HttpRequestInitializer requestInitializer = new HttpCredentialsAdapter(credentials);

    try {
      return new Gmail.Builder(
          GoogleNetHttpTransport.newTrustedTransport(),
          GsonFactory.getDefaultInstance(),
          requestInitializer
      ).setApplicationName("draftly-ai").build();
    } catch (GeneralSecurityException | IOException e) {
      throw new RuntimeException(e);
    }
  }

  private String sendWithAccessToken(OAuthToken token,
      String to,
      String subject,
      String body ){

    Gmail gmail = gmailService(token);

    Session session = Session.getInstance(new Properties());
    MimeMessage mimeMessage = new MimeMessage(session);

    try {
      mimeMessage.setRecipients(
          jakarta.mail.Message.RecipientType.TO, to);
    } catch (MessagingException e) {
      throw new RuntimeException(e);
    }

    try {
      mimeMessage.setSubject(subject);
    } catch (MessagingException e) {
      throw new RuntimeException(e);
    }
    try {
      mimeMessage.setText(body);
    } catch (MessagingException e) {
      throw new RuntimeException(e);
    }

    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    try {
      mimeMessage.writeTo(buffer);
    } catch (IOException | MessagingException e) {
      throw new RuntimeException(e);
    }

    String encodedEmail =
        Base64.getUrlEncoder().encodeToString(buffer.toByteArray());

    Message message = new Message();
    message.setRaw(encodedEmail);

    Message sentMessage =
        null;
    try {
      sentMessage = gmail.users()
          .messages()
          .send("me", message)
          .execute();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    return sentMessage.getId();
  }

  private String sendReplyWithAccessToken(OAuthToken token, GmailMessageDetails original, String replyBody) {
    Gmail gmail = gmailService(token);

    Session session = Session.getInstance(new Properties());
    MimeMessage mimeMessage = new MimeMessage(session);

    String to = extractEmailAddress(original.from());
    String subject = ensureRePrefix(original.subject());

    try {
      mimeMessage.setRecipients(jakarta.mail.Message.RecipientType.TO, to);
      mimeMessage.setSubject(subject);
      if (original.messageIdHeader() != null && !original.messageIdHeader().isBlank()) {
        mimeMessage.setHeader("In-Reply-To", original.messageIdHeader());

        String refs = original.referencesHeader();
        if (refs == null || refs.isBlank()) {
          refs = original.messageIdHeader();
        } else if (!refs.contains(original.messageIdHeader())) {
          refs = refs + " " + original.messageIdHeader();
        }
        mimeMessage.setHeader("References", refs);
      }

      mimeMessage.setText(replyBody);
    } catch (MessagingException e) {
      throw new RuntimeException(e);
    }

    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    try {
      mimeMessage.writeTo(buffer);
    } catch (IOException | MessagingException e) {
      throw new RuntimeException(e);
    }

    String encodedEmail = Base64.getUrlEncoder().encodeToString(buffer.toByteArray());

    Message message = new Message();
    message.setRaw(encodedEmail);
    if (original.threadId() != null && !original.threadId().isBlank()) {
      message.setThreadId(original.threadId());
    }

    try {
      Message sentMessage = gmail.users()
          .messages()
          .send("me", message)
          .execute();
      return sentMessage.getId();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private List<GmailMessageDetails> fetchUnreadWithAccessToken(OAuthToken token, long maxResults) {
    Gmail gmail = gmailService(token);

    try {
      var listResp = gmail.users()
          .messages()
          .list("me")
          .setQ("is:unread in:inbox")
          .setMaxResults(maxResults)
          .execute();

      List<Message> messages = listResp.getMessages();
      if (messages == null || messages.isEmpty()) {
        return List.of();
      }

      return messages.stream().map(m -> getMessageDetails(gmail, m.getId())).toList();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public GmailMessageDetails getMessageDetails(OAuthToken token, String gmailMessageId) throws Exception {
    try {
      return getMessageDetails(gmailService(token), gmailMessageId);
    } catch (Exception ex) {
      token = tokenRefreshService.refreshToken(token);
      return getMessageDetails(gmailService(token), gmailMessageId);
    }
  }

  private GmailMessageDetails getMessageDetails(Gmail gmail, String gmailMessageId) {
    try {
      Message msg = gmail.users()
          .messages()
          .get("me", gmailMessageId)
          .setFormat("full")
          .execute();

      String threadId = msg.getThreadId();
      String snippet = msg.getSnippet();

      MessagePart payload = msg.getPayload();
      String from = header(payload, "From").orElse(null);
      String subject = header(payload, "Subject").orElse("");
      String messageIdHeader = header(payload, "Message-Id").orElseGet(() -> header(payload, "Message-ID").orElse(null));
      String referencesHeader = header(payload, "References").orElse(null);

      String bodyText = extractText(payload);

      return new GmailMessageDetails(
          gmailMessageId,
          threadId,
          from,
          subject,
          messageIdHeader,
          referencesHeader,
          bodyText,
          snippet
      );
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private Optional<String> header(MessagePart payload, String name) {
    if (payload == null || payload.getHeaders() == null) {
      return Optional.empty();
    }

    for (MessagePartHeader h : payload.getHeaders()) {
      if (h.getName() != null && h.getName().equalsIgnoreCase(name)) {
        return Optional.ofNullable(h.getValue());
      }
    }
    return Optional.empty();
  }

  private String extractText(MessagePart part) {
    if (part == null) {
      return "";
    }

    String mimeType = part.getMimeType();
    if (mimeType != null && mimeType.toLowerCase(Locale.ROOT).startsWith("text/plain")) {
      return decodeBody(part.getBody());
    }

    if (mimeType != null && mimeType.toLowerCase(Locale.ROOT).startsWith("text/html")) {
      String html = decodeBody(part.getBody());
      if (html != null && !html.isBlank()) {
        return html.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
      }
    }

    if (part.getParts() != null) {
      for (MessagePart p : part.getParts()) {
        String nested = extractText(p);
        if (nested != null && !nested.isBlank()) {
          return nested;
        }
      }
    }

    return "";
  }

  private String decodeBody(MessagePartBody body) {
    if (body == null || body.getData() == null) {
      return "";
    }
    byte[] decoded = Base64.getUrlDecoder().decode(body.getData());
    return new String(decoded);
  }

  private String extractEmailAddress(String fromHeader) {
    if (fromHeader == null) {
      return "";
    }
    int lt = fromHeader.indexOf('<');
    int gt = fromHeader.indexOf('>');
    if (lt >= 0 && gt > lt) {
      return fromHeader.substring(lt + 1, gt).trim();
    }
    return fromHeader.trim();
  }

  private String ensureRePrefix(String subject) {
    if (subject == null) {
      return "Re:";
    }
    String s = subject.trim();
    if (s.toLowerCase(Locale.ROOT).startsWith("re:")) {
      return s;
    }
    return "Re: " + s;
  }

}


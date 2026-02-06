package com.sp.demo.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TokenCryptoService {

  private static final int IV_BYTES = 12;
  private static final int TAG_BITS = 128;

  private final SecureRandom secureRandom = new SecureRandom();

  private final String base64Key;

  public TokenCryptoService(@Value("${token.crypto.key:}") String base64Key) {
    this.base64Key = base64Key;
  }

  public String encrypt(String plaintext) {
    if (plaintext == null) {
      return null;
    }
    if (base64Key == null || base64Key.isBlank()) {
      return plaintext;
    }

    try {
      byte[] keyBytes = Base64.getDecoder().decode(base64Key);
      SecretKey key = new SecretKeySpec(keyBytes, "AES");

      byte[] iv = new byte[IV_BYTES];
      secureRandom.nextBytes(iv);

      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));

      byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

      ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
      buffer.put(iv);
      buffer.put(ciphertext);

      return Base64.getEncoder().encodeToString(buffer.array());
    } catch (GeneralSecurityException e) {
      throw new RuntimeException("Token encryption failed", e);
    }
  }

  public String decrypt(String encrypted) {
    if (encrypted == null) {
      return null;
    }
    if (base64Key == null || base64Key.isBlank()) {
      return encrypted;
    }

    try {
      byte[] raw = Base64.getDecoder().decode(encrypted);
      ByteBuffer buffer = ByteBuffer.wrap(raw);

      byte[] iv = new byte[IV_BYTES];
      buffer.get(iv);

      byte[] ciphertext = new byte[buffer.remaining()];
      buffer.get(ciphertext);

      byte[] keyBytes = Base64.getDecoder().decode(base64Key);
      SecretKey key = new SecretKeySpec(keyBytes, "AES");

      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));

      byte[] plaintext = cipher.doFinal(ciphertext);
      return new String(plaintext, StandardCharsets.UTF_8);
    } catch (GeneralSecurityException e) {
      throw new RuntimeException("Token decryption failed", e);
    }
  }
}

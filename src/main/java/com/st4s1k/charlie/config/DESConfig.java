package com.st4s1k.charlie.config;

import com.st4s1k.charlie.service.DES;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import static javax.crypto.Cipher.*;
import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE;

@Configuration
public class DESConfig {

  @Value("charlie.cipher")
  private String cipher;

  @Bean
  @Scope(SCOPE_PROTOTYPE)
  public DES getDes() throws
      NoSuchAlgorithmException,
      NoSuchPaddingException,
      InvalidKeyException {
    final var encryptCipher = getInstance(cipher);
    final var decryptCipher = getInstance(cipher);
    final var key = KeyGenerator.getInstance(cipher).generateKey();
    encryptCipher.init(ENCRYPT_MODE, key);
    decryptCipher.init(DECRYPT_MODE, key);
    return new DES(encryptCipher, decryptCipher);
  }
}

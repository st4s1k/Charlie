package com.st4s1k.charlie.service;

import com.sun.mail.util.BASE64DecoderStream;
import com.sun.mail.util.BASE64EncoderStream;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.SecretKey;

import static java.nio.charset.StandardCharsets.UTF_8;

public class DES {

  private final Cipher encryptCipher;
  private final Cipher decryptCipher;

  public DES(
      final Cipher encryptCipher,
      final Cipher decryptCipher) {
    this.encryptCipher = encryptCipher;
    this.decryptCipher = decryptCipher;
  }

  public String encrypt(final String str) throws
      BadPaddingException,
      IllegalBlockSizeException {
    final var utf8 = str.getBytes(UTF_8);
    final var enc = BASE64EncoderStream.encode(encryptCipher.doFinal(utf8));
    return new String(enc);
  }

  public String decrypt(final String str) throws
      BadPaddingException,
      IllegalBlockSizeException {
    final var dec = BASE64DecoderStream.decode(str.getBytes());
    final var utf8 = decryptCipher.doFinal(dec);
    return new String(utf8, UTF_8);
  }
}

package com.st4s1k.charlie.config;

import com.jcraft.jsch.JSch;
import com.st4s1k.charlie.service.CharlieService;
import com.st4s1k.charlie.service.CharlieTelegramBot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;

@Configuration
public class CharlieConfiguration {

  @Bean
  public CharlieService getCharlieService(
      final @Value("${charlie.messages.start}") String startMessage,
      final @Value("${charlie.messages.help}") String helpMessage,
      final @Value("${charlie.publicKeyFileName}") String publicKeyFileName,
      final @Value("${charlie.messages.keyGenHint}") String keyGenHint
  ) {
    return new CharlieService(
        startMessage,
        helpMessage,
        publicKeyFileName,
        keyGenHint,
        new HashMap<>()
    );
  }

  @Bean
  public CharlieTelegramBot getCharlieTelegramBot(
      final @Autowired JSch jsch,
      final @Autowired CharlieService charlieService,
      final @Value("${charlie.token}") String token,
      final @Value("${charlie.username}") String username,
      final @Value("${jsch.dotSsh}") String dotSsh
  ) {
    return new CharlieTelegramBot(
        jsch,
        charlieService,
        token,
        username,
        dotSsh,
        new HashMap<>()
    );
  }
}

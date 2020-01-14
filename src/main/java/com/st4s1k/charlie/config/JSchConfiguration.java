package com.st4s1k.charlie.config;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JSchConfiguration {

  @Bean
  public JSch getJSch(
      final @Value("${jsch.knownHosts.file}") String knownHostsFile) throws JSchException {
    final var jsch = new JSch();
    jsch.setKnownHosts(knownHostsFile);
    return jsch;
  }
}

package com.st4s1k.charlie.config;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JSchConfiguration {

  @Value("${jsch.knownHosts.file}")
  private String knownHostsFile;

  @Bean
  public JSch getJSch() throws JSchException {
    final var jsch = new JSch();
    jsch.setKnownHosts(knownHostsFile);
    JSch.setConfig("StrictHostKeyChecking", "no");
    JSch.setConfig("PreferredAuthentications", "publickey,password");
    return jsch;
  }
}

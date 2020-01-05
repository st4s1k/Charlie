package com.st4s1k.charlie.data.model;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.st4s1k.charlie.service.SSHManager;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
public class ConnectionInfo {

  @EqualsAndHashCode.Exclude
  private final JSch jsch;

  private Session session;
  private String hostname;
  private Integer port;
  private String username;
  private String password;

  private boolean awaitHostname;
  private boolean awaitPort;
  private boolean awaitUsername;
  private boolean awaitPassword;

  private boolean setupComplete;

  private SSHManager sshManager;

  public ConnectionInfo() {
    this.jsch = new JSch();

    this.session = null;
    this.hostname = null;
    this.port = null;
    this.username = null;
    this.password = null;

    this.awaitHostname = false;
    this.awaitPort = false;
    this.awaitUsername = false;
    this.awaitPassword = false;

    this.setupComplete = true;
  }
}

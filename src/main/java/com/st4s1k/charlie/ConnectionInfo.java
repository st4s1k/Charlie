package com.st4s1k.charlie;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
class ConnectionInfo {

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

  public boolean isAnyAwaiting() {
    return awaitHostname
        || awaitUsername
        || awaitPort
        || awaitPassword;
  }
}

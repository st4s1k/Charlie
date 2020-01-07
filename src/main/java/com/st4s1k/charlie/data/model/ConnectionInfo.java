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
  private SSHManager sshManager;

  private String hostname;
  private Integer port;
  private String username;
  private String password;

  public ConnectionInfo() {
    this.jsch = new JSch();

    this.session = null;
    this.hostname = null;
    this.port = null;
    this.username = null;
    this.password = null;
  }

  public boolean allSet() {
    return hostname != null
        && username != null
        && port != null
        && password != null;
  }

  public void setUpSshManager() {
    sshManager = new SSHManager(
        username,
        password,
        hostname, "",
        port);
  }
}

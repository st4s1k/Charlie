/*
 * SSHManager
 *
 * @author cabbott
 * @version 1.0
 */
package com.st4s1k.charlie.service;

import com.jcraft.jsch.*;

import java.io.IOException;
import java.io.InputStream;

public class SSHManager {

  private JSch jschSSHChannel;
  private String strUserName;
  private String strConnectionIP;
  private int intConnectionPort;
  private String strPassword;
  private Session sesConnection;
  private int intTimeOut;

  private void doCommonConstructorActions(
      final String userName,
      final String password,
      final String connectionIP,
      final String knownHostsFileName) {
    jschSSHChannel = new JSch();

    try {
      jschSSHChannel.setKnownHosts(knownHostsFileName);
    } catch (JSchException e) {
      e.printStackTrace();
    }

    strUserName = userName;
    strPassword = password;
    strConnectionIP = connectionIP;
  }

  public SSHManager(
      final String userName,
      final String password,
      final String connectionIP,
      final String knownHostsFileName) {
    doCommonConstructorActions(userName, password,
        connectionIP, knownHostsFileName);
    intConnectionPort = 22;
    intTimeOut = 60000;
  }

  public SSHManager(
      final String userName,
      final String password,
      final String connectionIP,
      final String knownHostsFileName,
      final int connectionPort) {
    doCommonConstructorActions(userName, password, connectionIP,
        knownHostsFileName);
    intConnectionPort = connectionPort;
    intTimeOut = 60000;
  }

  public SSHManager(
      final String userName,
      final String password,
      final String connectionIP,
      final String knownHostsFileName,
      final int connectionPort,
      final int timeOutMilliseconds) {
    doCommonConstructorActions(userName, password, connectionIP,
        knownHostsFileName);
    intConnectionPort = connectionPort;
    intTimeOut = timeOutMilliseconds;
  }

  public String connect() {
    String errorMessage = null;

    try {
      sesConnection = jschSSHChannel.getSession(strUserName,
          strConnectionIP, intConnectionPort);
      sesConnection.setPassword(strPassword);
      // UNCOMMENT THIS FOR TESTING PURPOSES, BUT DO NOT USE IN PRODUCTION
      sesConnection.setConfig("StrictHostKeyChecking", "no");
      sesConnection.connect(intTimeOut);
    } catch (JSchException e) {
      errorMessage = e.getMessage();
    }

    return errorMessage;
  }

  public String sendCommand(String command) {
    StringBuilder outputBuffer = new StringBuilder();

    try {
      Channel channel = sesConnection.openChannel("exec");
      ((ChannelExec) channel).setCommand(command);
      InputStream commandOutput = channel.getInputStream();
      channel.connect();
      int readByte = commandOutput.read();

      while (readByte != 0xffffffff) {
        outputBuffer.append((char) readByte);
        readByte = commandOutput.read();
      }

      channel.disconnect();
    } catch (IOException | JSchException x) {
      return null;
    }

    return outputBuffer.toString();
  }

  public void close() {
    sesConnection.disconnect();
  }

}
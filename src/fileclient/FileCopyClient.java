package fileclient;

/* FileCopyClient.java
 Version 0.1 - Muss ergaenzt werden!!
 Praktikum 3 Rechnernetze BAI4 HAW Hamburg
 Autoren:
 */

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

import data.FCbuffer;
import data.FCpacket;

public class FileCopyClient extends Thread {

  // -------- Constants
  public final static boolean TEST_OUTPUT_MODE = true;

  public final int SERVER_PORT = 23000;

  public final static int UDP_PACKET_SIZE = 1008;

  // -------- Public parms
  public final String servername;

  public final String sourcePath;

  public final String destPath;

  public final int windowSize;

  public long serverErrorRate;

  // -------- Variables
  // current default timeout in nanoseconds
  private long      timeoutValue = 10000000000L;
  
  private FCbuffer buffer;
  
  private DatagramSocket socket;
  
  private InetAddress hostAdress;
  
  private int nextSeqNum = 1;
  
  private Receiver receiver;

  // Constructor
  public FileCopyClient(String serverArg, String sourcePathArg,
    String destPathArg, String windowSizeArg, String errorRateArg) 
        throws UnknownHostException, SocketException {
    servername = serverArg;
    sourcePath = sourcePathArg;
    destPath = destPathArg;
    windowSize = Integer.parseInt(windowSizeArg);
    serverErrorRate = Long.parseLong(errorRateArg);
    hostAdress = InetAddress.getByName(servername);
    buffer = new FCbuffer(windowSize, 0L);
    socket = new DatagramSocket();
    receiver = new Receiver(this.buffer, this.socket);
  }

  public void runFileCopyClient() throws Exception {
    FCpacket fcPacket = makeControlPacket();
    DatagramPacket packet = 
        new DatagramPacket(fcPacket.getData(), fcPacket.getLen(), hostAdress, SERVER_PORT);
    testOut(new String(fcPacket.getData()));
    this.buffer.add(fcPacket);
    socket.send(packet);
    startTimer(fcPacket);
    receiver.start();
    InputStream fileStream = new FileInputStream(sourcePath);
    byte[] bytePacket = new byte[UDP_PACKET_SIZE];
    while (fileStream.read(bytePacket) != 0) {
      while (this.buffer.isFull()) {
        // block while buffer is full
      } 
      FCpacket fileSlice = new FCpacket(nextSeqNum++, bytePacket, bytePacket.length);
      this.buffer.add(fileSlice);
      packet = new DatagramPacket(fileSlice.getData(), fileSlice.getLen(), hostAdress, SERVER_PORT);
      socket.send(packet);
      startTimer(fileSlice);
    }
    fileStream.close();
  }

  /**
   * Timer operations
   * @param packet
   */
  public void startTimer(FCpacket packet) {
    /* Create, save and start timer for the given FCpacket */
    FC_Timer timer = new FC_Timer(timeoutValue, this, packet.getSeqNum());
    packet.setTimer(timer);
    timer.start();
  }

  public void cancelTimer(FCpacket packet) {
    /* Cancel timer for the given FCpacket */
    testOut("Cancel Timer for packet" + packet.getSeqNum());

    if (packet.getTimer() != null) {
      packet.getTimer().interrupt();
    }
  }

  /**
   * Implementation specific task performed at timeout
   * @throws IOException 
   */
  public void timeoutTask(long seqNum) {
    testOut("Timeout: Timeout of " + seqNum + ".");
    FCpacket lostPacket = buffer.getBySeqNum(seqNum);
    DatagramPacket packet = 
        new DatagramPacket(lostPacket.getData(), lostPacket.getLen(), hostAdress, SERVER_PORT);
    boolean done = false;
    while (!done) {
      try {
        socket.send(packet);
        done = true;
      } catch (IOException e) {
        testOut("Timeout: IOException!");
      }
    }
    startTimer(lostPacket);
  }


  /**
   *
   * Computes the current timeout value (in nanoseconds)
   */
  public void computeTimeoutValue(long sampleRTT) {

  // ToDo
  }


  /**
   *
   * Return value: FCPacket with (0 destPath;windowSize;errorRate)
   */
  public FCpacket makeControlPacket() {
   /* Create first packet with seq num 0. Return value: FCPacket with
     (0 destPath ; windowSize ; errorRate) */
    String sendString = destPath + ";" + windowSize + ";" + serverErrorRate;
    byte[] sendData = null;
    try {
      sendData = sendString.getBytes("UTF-8");
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    }
    return new FCpacket(0, sendData, sendData.length);
  }

  public void testOut(String out) {
    if (TEST_OUTPUT_MODE) {
      System.err.printf("%,d %s: %s\n", System.nanoTime(), Thread
          .currentThread().getName(), out);
    }
  }

  public static void main(String argv[]) throws Exception {
    if (argv.length != 5) {
      System.err.println("Invalid arguments!");
      System.err.println("Please type: ");
      System.err.println("             ./<>  servername source-path "
                       + "destination-path window-size error-rate");
      return;
    }
    FileCopyClient myClient = new FileCopyClient(argv[0], argv[1], argv[2],
        argv[3], argv[4]);
    myClient.runFileCopyClient();
  }

}

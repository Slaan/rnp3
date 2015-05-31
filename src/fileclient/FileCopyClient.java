package fileclient;

/*
 * FileCopyClient.java Version 0.1 - Muss erg�nzt werden!! Praktikum 3 Rechnernetze BAI4 HAW
 * Hamburg Autoren:
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
import java.util.LinkedList;
import java.util.concurrent.Semaphore;

public class FileCopyClient extends Thread {

  // -------- Constants
  public final static boolean TEST_OUTPUT_MODE = true;

  public final int SERVER_PORT = 23000;

  public final static int UDP_PACKET_SIZE = 1008;

  // -------- Public parms
  public InetAddress serveradress;

  public String sourcePath;

  public String destPath;

  public int windowSize;

  public long serverErrorRate;

  // -------- Variables
  // current default timeout in nanoseconds
  private long timeoutValue = 100000000L;

  private DatagramSocket socket;

  private long seqNum = 0L;

  private LinkedList<FCpacket> sendBuf;

  private Semaphore bufferlock;

  // ... ToDo


  // Constructor
  public FileCopyClient(String serverArg, String sourcePathArg, String destPathArg,
      String windowSizeArg, String errorRateArg) throws UnknownHostException {
    serveradress = InetAddress.getByName(serverArg);
    sourcePath = sourcePathArg;
    destPath = destPathArg;
    windowSize = Integer.parseInt(windowSizeArg);
    serverErrorRate = Long.parseLong(errorRateArg);
    try {
      socket = new DatagramSocket(8080);
    } catch (SocketException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    sendBuf = new LinkedList<FCpacket>();
  }

  public void runFileCopyClient() throws IOException {

    bufferlock = new Semaphore(1, true);
    FCpacket sendFc = makeControlPacket();
    DatagramPacket sendpackage =
        new DatagramPacket(sendFc.getSeqNumBytesAndData(), sendFc.getLen() + 8, serveradress,
            SERVER_PORT);
    socket.send(sendpackage);
    socket.receive(sendpackage);
    FCpacket recvpack = new FCpacket(sendpackage.getData(), sendpackage.getLength());
    testOut("Package " + recvpack.getSeqNum() + " ACKd");
    InputStream fs = new FileInputStream(sourcePath);
    byte[] bytePacket = new byte[UDP_PACKET_SIZE];
    new Receiver(sendBuf, socket, bufferlock).start();;
    while (fs.read(bytePacket) != -1) {
      while (sendBuf.size() >= windowSize) {
        if (sendBuf.size()<windowSize) {
          break;
        }
      }
      FCpacket part = new FCpacket(++seqNum, bytePacket, bytePacket.length);
      DatagramPacket pack =
          new DatagramPacket(part.getSeqNumBytesAndData(), part.getLen() + 8, serveradress,
              SERVER_PORT);
      System.out.println("sendPackage No " + seqNum);
      socket.send(pack);
      try {
        bufferlock.acquire();
        sendBuf.add(part);
      } catch (InterruptedException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      } finally {
        bufferlock.release();
      }
      startTimer(part);
    }
    fs.close();
    System.out.println("Client: FileTransfer done");
  }

  /**
   *
   * Timer Operations
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
   * 
   * @throws IOException
   */
  public void timeoutTask(long seqNum) {
    try {
      bufferlock.acquire();
      for (FCpacket part : sendBuf) {
        if (part.getSeqNum() == seqNum) {
          DatagramPacket pack =
              new DatagramPacket(part.getSeqNumBytesAndData(), part.getLen() + 8, serveradress,
                  SERVER_PORT);
          try {
            socket.send(pack);
          } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
          }
          startTimer(part);
        }
      }
    } catch (InterruptedException e1) {
      // TODO Auto-generated catch block
      e1.printStackTrace();
    } finally {    
      bufferlock.release();
    }
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
    /*
     * Create first packet with seq num 0. Return value: FCPacket with (0 destPath ; windowSize ;
     * errorRate)
     */
    String sendString = destPath + ";" + windowSize + ";" + serverErrorRate;
    System.out.println(sendString);
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
      System.err.printf("%,d %s: %s\n", System.nanoTime(), Thread.currentThread().getName(), out);
    }
  }

  public static void main(String argv[]) throws Exception {
    FileCopyClient myClient = new FileCopyClient(argv[0], argv[1], argv[2], argv[3], argv[4]);
    myClient.runFileCopyClient();
  }

}

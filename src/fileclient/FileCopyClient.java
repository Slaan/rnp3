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

  public final static int UDP_PACKET_SIZE = 1000;

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
  
  private long jitter = 0L;
  
  private long rtt;

  // ... Endausgabe
  
  private long total_rtt;
  
  private long total_send_pack;
  
  private long received_acks;
  
  private long retransmit_packs;


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
      socket.setSoTimeout(5000);
    } catch (SocketException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }

    sendBuf = new LinkedList<FCpacket>();
    total_rtt=0L;
    total_send_pack=0L;
    received_acks=0L;
    retransmit_packs=0L;
  }

  public void runFileCopyClient() throws IOException {

    bufferlock = new Semaphore(1, true);
    long starttime = System.nanoTime();
    FCpacket sendFc = makeControlPacket();  
    DatagramPacket sendpackage =
        new DatagramPacket(sendFc.getSeqNumBytesAndData(), sendFc.getLen() + 8, serveradress,
            SERVER_PORT);
    long timestamp = System.nanoTime();
    socket.send(sendpackage);
    total_send_pack++;
    socket.receive(sendpackage);
    rtt = System.nanoTime() - timestamp + 10000000L;
    timeoutValue = rtt + 50000L;
    total_rtt += rtt;
    received_acks++;
    FCpacket recvpack = new FCpacket(sendpackage.getData(), sendpackage.getLength());
    testOut("Package " + recvpack.getSeqNum() + " ACKd");
    InputStream fs = new FileInputStream(sourcePath);
    byte[] bytePacket = new byte[UDP_PACKET_SIZE];
    Receiver rec = new Receiver(sendBuf, socket, bufferlock, this);
    rec.start();
    while (fs.read(bytePacket) != -1) {
      boolean done = false;
      while (!done) {
        try {
          bufferlock.acquire();
          if (sendBuf.size()<windowSize) {
            break;
          }
        } catch (InterruptedException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        } finally {
          bufferlock.release();
        }
      }
      FCpacket part = new FCpacket(++seqNum, bytePacket, bytePacket.length);
      DatagramPacket pack =
          new DatagramPacket(part.getSeqNumBytesAndData(), part.getLen() + 8, serveradress,
              SERVER_PORT);
      System.out.println("sendPackage No " + seqNum);
      socket.send(pack);
      total_send_pack++;
      try {
        bufferlock.acquire();
        part.setTimestamp(System.nanoTime());
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
    try {
      rec.join();
    } catch (InterruptedException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    long total_time = System.nanoTime() - starttime;
    int tt_milli = (int) total_time/1000000;
    System.out.println("-------------------- END OF DATA TRANSFER ------------------");
    System.out.println("Total_Time: " + tt_milli +  "ms");
    System.out.println("Number of Retransmit: " + retransmit_packs);
    System.out.println("Number of Received Acks: " + received_acks);
    long average_rtt = total_rtt/received_acks;
    System.out.println("Average RTT: " + average_rtt + " ns");
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

  public void add_total_rtt(long rtt) {
    total_rtt+=rtt;
  }
  
  public void incremet_send_packs() {
    total_send_pack++;
  }
  
  public void increment_retransmit_packs() {
    retransmit_packs++;
  }
  
  public void increment_received_acks() {
    received_acks++;
  }
  
  /**
   * Implementation specific task performed at timeout
   * 
   * @throws IOException
   */
  public void timeoutTask(long seqNum) {
    
      bufferlock.acquireUninterruptibly();
      for (FCpacket part : sendBuf) {
        if (part.getSeqNum() == seqNum) {
          DatagramPacket pack =
              new DatagramPacket(part.getSeqNumBytesAndData(), part.getLen() + 8, serveradress,
                  SERVER_PORT);
          try {
            socket.send(pack);
            increment_retransmit_packs();
            incremet_send_packs();
          } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
          }
          startTimer(part);
          part.setTimestamp(System.nanoTime());
        }
      }
      bufferlock.release();
    timeoutValue = timeoutValue*2;
  }

  /**
   *
   * Computes the current timeout value (in nanoseconds)
   */
  public void computeTimeoutValue(long sampleRTT) {

    int timeoutsec = (int) (timeoutValue/1000000);
    double x = 0.25;
    double y = x/2;
    long expRTT = (long) ((1-y) *sampleRTT + y*timeoutValue);
    long absolut = Math.abs(sampleRTT - rtt);
    long newjitter = (long) ((1-x) * jitter + x *  absolut);
    rtt = sampleRTT;
    timeoutValue = expRTT + 4*newjitter;
    System.out.println("new timeout: " + timeoutsec + "micro s");
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

package com.geeksville.mavlink

import java.io._
import com.geeksville.akka.InstrumentedActor
import org.mavlink.messages.MAVLinkMessage
import com.geeksville.util.ThreadTools
import com.geeksville.util.Using._
import org.mavlink._
import com.geeksville.util.DebugInputStream
import com.geeksville.util.ByteOnlyInputStream
import com.geeksville.util.Throttled
import com.geeksville.logback.Logging
import com.geeksville.akka.PoisonPill
import java.net.ConnectException
import scala.concurrent._
import scala.util.Random
import java.net.SocketTimeoutException

// with SerialPortEventListener

/**
 * Talks mavlink out a serial port
 *
 * @param sysIdOverride if set, we will replace any received sysIds with this alternative (useful for remapping sysId based on interface)
 */
class MavlinkStream(outgen: => OutputStream, ingen: => InputStream, val sysIdOverride: Option[Int] = None) extends MavlinkSender with MavlinkReceiver {

  log.debug("MavlinkStream starting")
  MavlinkStream.isIgnoreReceive = false

  /**
   * We use generators to init these variables, because android doesn't allow network access from the
   * 'main' thread
   */
  private lazy val out = outgen
  private lazy val instream = ingen // new DebugInputStream(ingen)

  /// This skanky hack is to make sure that we only touch the inputstream if it has already been created
  private var isInstreamValid = false

  /// The id we expect for vehicles on this port (possibly will be overridden)
  val expectedSysId = 1

  val rxThread = ThreadTools.createDaemon("streamRx")(rxWorker)

  /**
   * If true we will pretend to drop many packets
   */
  var simulateUnreliable = false

  private val rand = new Random(System.currentTimeMillis)

  //rxThread.setPriority(Thread.MAX_PRIORITY)
  rxThread.start()

  // Mission control does this, seems to be necessary to keep device from hanging up on us
  //out.write("\r\n\r\n\r\n".map(_.toByte).toArray)

  private def shouldDrop = simulateUnreliable && rand.nextInt(10) < 2

  protected def doSendMavlink(bytes: Array[Byte]) {
    //log.debug("Sending ser (sysId=%d): %s".format(msg.sysId, msg))

    try {
      blocking {
        out.write(bytes)
        out.flush()
      }
    } catch {
      case ex: IOException =>
        log.error("Error sending packet: " + ex.getMessage)
    }
  }

  override def postStop() {
    log.debug("MavlinkStream postStop")

    // This should cause the rx thread to bail
    if (isInstreamValid)
      instream.close()

    super.postStop()
  }

  private def rxWorker() {
    log.debug("MavlinkStream thread running")
    try {
      using(instream) { stream =>
        isInstreamValid = true

        val reader = new MAVLinkReader(new DataInputStream(stream), IMAVLinkMessage.MAVPROT_PACKET_START_V10)

        var lostBytes = 0
        var badSeq = 0

        val messageThrottle = new Throttled(60 * 1000)
        var oldLost = 0L
        var oldNumPacket = 0L
        var numPacket = 0L
        var prevSeq = -1

        val overrideId = sysIdOverride.getOrElse(-1)

        while (!self.isTerminated) {
          try {
            //log.debug("Reading next packet")
            val msg = Option(reader.getNextMessage())
            msg.foreach { s =>
              numPacket += 1

              // Reassign sysId if requested
              if (overrideId != -1 && s.sysId == expectedSysId)
                s.sysId = overrideId

              //log.debug("RxSer: " + s)
              if (reader.getLostBytes > lostBytes) {
                // The android version of the library lets an extra two bytes sneak in.  FIXME.  For now
                // ignore silently because it seems okay (I bet the bytes are ftdi header bytes)
                // if (reader.getLostBytes != lostBytes + 2)
                //log.warn("Serial RX has dropped %d bytes in total...".format(reader.getLostBytes))
                lostBytes = reader.getLostBytes
              }

              if (reader.getBadSequence > badSeq) {
                badSeq = reader.getBadSequence
                //log.warn("Serial RX has %d bad sequences in total...".format(badSeq))
              }

              messageThrottle { dt =>
                val numSec = dt / 1000.0

                val newLost = reader.getLostBytes
                val dropPerSec = (newLost - oldLost) / numSec
                oldLost = newLost

                val mPerSec = (numPacket - oldNumPacket) / numSec
                oldNumPacket = numPacket

                log.info("msgs per sec %s, bytes dropped per sec=%s".format(mPerSec, dropPerSec))
              }

              // Dups are normal, the 3dr radio will duplicate packets if it has nothing better to do
              if (s.sequence != prevSeq && !MavlinkStream.isIgnoreReceive) //  for profiling
                if (!shouldDrop)
                  handlePacket(s)

              prevSeq = s.sequence
            }
          } catch {

            case ex: EOFException =>
              // Kill our actor if our port gets closed
              self ! PoisonPill

            case ex: IOException =>
              if (!self.isTerminated) {
                log.error("Killing mavlink stream due to: " + ex)
                self ! PoisonPill
              }
          }
        }
      }
    } catch {
      case ex: ConnectException =>
        log.error("Failure to connect: " + ex.getMessage)
        self ! PoisonPill

      case ex: SocketTimeoutException =>
        log.error("Socket timeout: " + ex.getMessage)
        self ! PoisonPill
    }

    log.debug("Exiting mavlink reader: " + this)
  }
}

object MavlinkStream {
  var isIgnoreReceive = false
}
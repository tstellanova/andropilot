package com.geeksville.ftdi

import LibFtdi._
import java.nio.ByteBuffer
import com.geeksville.util.DebugInputStream
import com.geeksville.logback.Logging

class FtdiDevice(vendor: Int, product: Int, serial: String = null) extends Logging {
  val handle = ftdi_new()

  private var isClosed = false

  private var readRetriesLeft = 10

  /// A lock object to make sure we are out of read before exiting
  private val inRead = new Object

  private val rxBuf = ByteBuffer.allocateDirect(8192)
  rxBuf.limit(0)

  val out = new FtdiOutputStream(this)
  val in = new FtdiInputStream(this)

  checkError(ftdi_usb_open_desc(handle, vendor, product, null, serial))
  checkError(ftdi_usb_purge_buffers(handle))

  /// Make sure we tear down our FTDI object cleanly when our app exits (otherwise we blow chunks on Windows)
  Runtime.getRuntime().addShutdownHook(new Thread() {
    override def run() { close(); }
  });

  def close() {
    if (!isClosed) {
      isClosed = true

      inRead.synchronized {
        // Wait here until any previously running read exits
      }

      // We do not want to check for errors here (usb bulk read
      // errors expected)
      ftdi_usb_close(handle)

      // We don't call free, because there might still be another thread using this data structure
      // This means we have a very small leak, but it saves us from doing the close in two stages.  FIXME
      // ftdi_free(handle)
    }
  }

  def read(buf: ByteBuffer, size: Int): Int = {
    inRead.synchronized {
      // If the port is already closed don't even attempt a read
      while (!isClosed) {
        val result = ftdi_read_data(handle, buf, size)
        //logger.debug("ftdi read returned " + result)

        if (!isClosed)
          // Got an error?
          if (result < 0) {
            val msg = ftdi_get_error_string(handle)

            // The following failure seems to occur rarely on Windows - try to retry
            // LibFTDI error usb bulk read failed
            if (msg.contains("bulk read failed") && readRetriesLeft > 0) {
              logger.error("Ignoring mystery " + msg)
              readRetriesLeft -= 1

              /// Allow a little time for the buggy FTDI code to start up
              Thread.sleep(200)
            } else
              throw new FtdiException("LibFTDI error " + msg)
          } else
            return result
      }

      -1
    }
  }

  def write(buf: Array[Byte], size: Int) { checkError(ftdi_write_data(handle, buf, size)) }

  def setLatencyTimer(latency: Int) = checkError(ftdi_set_latency_timer(handle, latency.toByte))

  def setReadDataChunksize(size: Int) { checkError(ftdi_read_data_set_chunksize(handle, size)) }
  def setWriteDataChunksize(size: Int) { checkError(ftdi_write_data_set_chunksize(handle, size)) }
  def setBaudRate(size: Int) { checkError(ftdi_set_baudrate(handle, size)) }
  def setFlowControl(ctrl: Int) { checkError(ftdi_setflowctrl(handle, ctrl)) }
  def setDTR(ctrl: Int) { checkError(ftdi_setdtr(handle, ctrl)) }
  def setRTS(ctrl: Int) { checkError(ftdi_setrts(handle, ctrl)) }

  /// Read a single character, or -1 if the stream is closed
  def read(): Int = {
    // Libftdi seems to blow chunks if you quit with a pending read running

    //logger.debug("Calling read")
    while (!rxBuf.hasRemaining && !isClosed) {
      rxBuf.rewind()

      val numRead = read(rxBuf, rxBuf.capacity)

      if (numRead == -1)
        return -1

      rxBuf.limit(numRead)
    }

    if (isClosed)
      -1
    else
      rxBuf.get().toInt & 0xff
  }

  /// @return whatever was passed in
  def checkError(errCode: INT) = {
    if (errCode < 0)
      throw new FtdiException("LibFTDI error " + ftdi_get_error_string(handle))
    errCode
  }
}
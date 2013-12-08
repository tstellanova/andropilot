package com.geeksville.andropilot.service

import com.geeksville.akka.InstrumentedActor
import com.geeksville.flight.VehicleModel
import android.content.Context
import android.os.Bundle
import com.geeksville.util.Throttled
import com.ridemission.scandroid.AndroidLogger
import android.hardware._
import com.geeksville.util.MathTools
import com.ridemission.scandroid.UsesPreferences
import com.geeksville.andropilot.AndropilotPrefs
import com.geeksville.flight.MsgModeChanged
import com.geeksville.akka.PoisonPill
import com.geeksville.mavlink.MsgHeartbeatLost
import android.os.Handler
import com.geeksville.util.ThrottleByBucket
import com.geeksville.util.ThreadTools._
import com.geeksville.flight.MsgFenceBreached
import com.geeksville.flight.MsgWaypointCurrentChanged
import com.geeksville.flight.MsgSysStatusChanged
import com.geeksville.flight.MsgReportBug
import com.geeksville.flight.StatusText
import com.ridemission.scandroid.UsesResources
import com.geeksville.andropilot.R
import com.bugsense.trace.BugSenseHandler
import com.geeksville.akka.MockAkka
import scala.concurrent.duration._
import com.geeksville.flight.MsgStatusChanged
import com.geeksville.mavlink.MsgArmChanged
import com.geeksville.flight.Location

/**
 * Do any background speech announcements based on vehicle state
 */
class Speaker(val context: AndropilotService, val v: VehicleModel) extends InstrumentedActor with UsesResources with AndroidLogger with AndropilotPrefs {
  private val subscription = v.eventStream.subscribe(this)

  private val handler = new Handler

  private lazy val throttleAlt = new ThrottleByBucket(speechAltBucket)
  private val throttleBattery = new ThrottleByBucket(10)

  val warningChecker = MockAkka.scheduler.schedule(60 seconds, 60 seconds) { () =>
    val warning = if (context.isLowVolt)
      R.string.spk_warn_volt
    else if (context.isLowBatPercent)
      R.string.spk_warn_battery
    else if (context.isLowRssi)
      R.string.spk_warn_radio
    else if (context.isLowNumSats)
      R.string.spk_warn_gps
    else
      -1

    if (warning != -1 && handler != null)
      handler.post { () =>
        v.sysStatusFaults.foreach { f =>
          context.speak(f + " failure")
        }
        context.speak(S(warning))
      }
  }

  override def postStop() {
    warningChecker.cancel()
    v.eventStream.removeSubscription(subscription)
    super.postStop()
  }

  override def onReceive = {
    case l: Location =>
      //log.debug("Received alt " + v.bestAltitude)
      throttleAlt(v.bestAltitude.toInt) { alt =>
        handler.post { () =>
          //log.warn(s"Announcing alt $alt")
          context.speak(s"$alt meters")
        }
      }

    case MsgSysStatusChanged =>
      for { pct <- v.batteryPercent } yield {
        throttleBattery((pct * 100).toInt) { pct =>
          handler.post { () =>
            debug("Speak battery: " + pct)
            context.speak(S(R.string.spk_percent).format(pct))
          }
        }
      }

    case MsgFenceBreached =>
      handler.post { () => context.speak("Fence Breached", urgent = true) }

    case MsgWaypointCurrentChanged(n) =>
      handler.post { () =>
        context.speak("Waypoint " + n)
      }

    case MsgReportBug(m) =>
      handler.post { () =>
        val e = new Exception(m)
        BugSenseHandler.sendExceptionMessage("model_bug", "state_machine", e)
        // context.speak("Warning, non fatal bug")
      }

    case MsgHeartbeatLost =>
      handler.post { () =>
        context.speak("Heartbeat lost", urgent = true)
      }

    case StatusText(s, severity) =>
      handleStatus(s, severity)

    case MsgArmChanged(armed) =>
      handler.post { () =>
        if (v.hasHeartbeat)
          context.speak(if (armed) "Armed" else "Disarmed")
      }

    case MsgModeChanged(mode) =>
      handler.post { () =>
        if (v.hasHeartbeat)
          context.speak(mode)
      }
  }

  private def handleStatus(s: String, severity: Int) {
    debug("Status changed: " + s)
    if (severity != MsgStatusChanged.SEVERITY_USER_RESPONSE) {
      val isImportant = severity >= MsgStatusChanged.SEVERITY_HIGH
      // toast(s, isImportant) - we show this on the map view now

      if (isImportant)
        handler.post { () =>
          context.speak(s)
        }
    }
  }

}


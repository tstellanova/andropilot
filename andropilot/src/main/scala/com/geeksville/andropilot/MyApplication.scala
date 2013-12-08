package com.geeksville.andropilot

import android.app.Application
import com.flurry.android.FlurryAgent
import com.bugsense.trace.BugSenseHandler
import com.ridemission.scandroid.AndroidLogger
import com.geeksville.logback.MuteAllFilter
import com.geeksville.util.AnalyticsAdapter
import com.geeksville.util.AnalyticsService

class AndroidAnalytics extends AnalyticsAdapter {
  def reportException(msg: String, ex: Throwable) {
    val e = ex match {
      case e: Exception =>
        e
      case e @ _ =>
        new Exception(e)
    }

    BugSenseHandler.sendExceptionMessage(msg, msg, e)
  }
}

// @ReportsCrashes(formKey = "dDFiVzhuNkNjVWJjUDFMVzJWbXBxZkE6MQ") /*
/* mode = ReportingInteractionMode.TOAST,
  forceCloseDialogAfterToast = false, // optional, default false
  resToastText = R.string.crash_toast_text)
  * 
  */
class MyApplication extends Application with AndropilotPrefs {
  override def context = this

  var lastSunspotCheck = 0L

  override def onCreate() {
    // The following line triggers the initialization of ACRA
    // ACRA.init(this)
    BugSenseHandler.initAndStartSession(this, "2a5e5e70")
    AnalyticsService.handler = new AndroidAnalytics
    val username = dshareUsername
    if (!username.isEmpty)
      BugSenseHandler.setUserIdentifier(dshareUsername)

    // Turn off our two types of logging
    AndroidLogger.enable = developerMode
    MuteAllFilter.mute = !developerMode

    // BugSenseHandler.setLogging(true)

    FlurryAgent.setCaptureUncaughtExceptions(false) // So we get the reports through google instead
    super.onCreate()
  }
}
package com.geeksville.andropilot

import com.ridemission.scandroid.UsesPreferences

trait AndropilotPrefs extends UsesPreferences {

  def curLatitude = floatPreference("cur_lat", 0.0f)
  def curLongitude = floatPreference("cur_lon", 0.0f)
  def paramsToFile = boolPreference("params_to_file", false)
  def speechAltBucket = intPreference("speech_altbucket", 10)

  /// Let user select follow me interval, but restrict to between 10 sec and 200ms
  def followMeInterval = math.min(10.0f, math.max(0.2f, floatPreference("follow_interval", 2.0f)))

  def minVoltage = floatPreference("min_volt", 9.5f)
  def minBatPercent = intPreference("min_batpct", 25) / 100.0f
  def minRssiSpan = intPreference("min_rssi_span", 2)
  def minNumSats = intPreference("min_numsats", 5)
  def isKeepScreenOn = boolPreference("force_screenon", true)
  def followPlane = boolPreference("follow_plane", false)
  def guideAlt = intPreference("guide_alt", 50)

  def loggingEnabled = boolPreference("log_to_file", true)
  def baudWireless = intPreference("baud_wireless", 57600)
  def baudDirect = intPreference("baud_direct", 115200)

  def fenceMinAlt = intPreference("fence_min", 30)
  def fenceMaxAlt = intPreference("fence_max", 100)
  def fenceMode = intPreference("fence_action", 1)

  def useFlurry = boolPreference("use_flurry", true)
  def useOldArducopter = boolPreference("use_old_ac", false)
  def runWebserver = boolPreference("run_webserver", false)
  def allowOtherHosts = boolPreference("allow_otherhosts", false)

  def loggingKeepBoring = boolPreference("log_keep_boring", false)

  def dshareUpload = boolPreference("dshare_upload", true)
  def dshareDeleteSent = boolPreference("dshare_deletesent", false)
  def dshareUsername = stringPreference("dshare_username", "").trim
  def dsharePassword = stringPreference("dshare_password", "").trim

  def developerMode = boolPreference("developer_mode", false)

  // This is now hidden in the UI - basically on all the time
  def stayAwakeEnabled = boolPreference("stay_awake2", true)

  object UDPMode extends Enumeration {
    val Disabled = Value("Disabled")
    val Uplink = Value("Uplink")
    val Downlink = Value("Downlink")
    val TCPUplink = Value("TCPUplink")
  }

  def udpMode = {
    //debug("UDP prefs mode: " + stringPreference("udp_mode", ""))
    enumPreference("udp_mode", UDPMode, UDPMode.Disabled)
  }

  def inboundPort = intPreference("inbound_port", 14550)
  def outboundUdpHost = stringPreference("outbound_udp_host", "192.168.0.4")
  def outboundPort = intPreference("outbound_port", 14550)

  def minDistance = floatPreference("minfollow_distance", 0.0f)
  def maxDistance = floatPreference("maxfollow_distance", 0.0f)
}
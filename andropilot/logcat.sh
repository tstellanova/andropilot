adb shell setprop log.redirect-stdio true
adb logcat *:D | grep -v UsbRequest

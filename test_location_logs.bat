@echo off
echo Starting location log monitoring...
echo Make sure your Android device is connected via USB and USB debugging is enabled.
echo.
echo Installing the updated APK...
adb install -r "app\build\outputs\apk\debug\app-debug.apk"
echo.
echo Clearing logcat buffer...
adb logcat -c
echo.
echo Starting logcat to monitor location logs...
echo Look for logs with tags: LocationHelper, SyncService, Location, LocationFetch
echo.
echo Press Ctrl+C to stop monitoring
echo.
adb logcat -s LocationHelper:* SyncService:* Location:* LocationFetch:* Dash:*
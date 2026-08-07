# Kuhoo Desktop Dev Script
# Run this once - it watches for file changes and auto-restarts the app
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot"
Write-Host "🐦 Starting Kuhoo Music in continuous mode..." -ForegroundColor Cyan
Write-Host "   Save any file to trigger auto-rebuild & restart" -ForegroundColor DarkGray
Write-Host ""
.\gradlew -t :desktop:run --no-configuration-cache

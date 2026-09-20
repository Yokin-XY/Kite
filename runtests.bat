@echo off
set "JAVA_HOME=D:\Dev\java\jdk-21.0.12.1+1"
cd /d D:\xm\Kite
call gradlew.bat :app:assembleDebug --console=plain

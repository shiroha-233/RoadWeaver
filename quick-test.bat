@echo off
echo ========================================
echo Quick NeoForge Build Test
echo ========================================
echo.
echo Cleaning...
call gradlew.bat clean --quiet
echo.
echo Building NeoForge module...
call gradlew.bat :neoforge:build

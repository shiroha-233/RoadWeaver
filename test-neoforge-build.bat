@echo off
echo ========================================
echo Testing NeoForge Build Configuration
echo ========================================
echo.

echo Step 1: Clean build
call gradlew.bat clean
if %errorlevel% neq 0 (
    echo [ERROR] Clean failed!
    exit /b 1
)
echo [OK] Clean successful
echo.

echo Step 2: Build NeoForge module
call gradlew.bat :neoforge:build
if %errorlevel% neq 0 (
    echo [ERROR] NeoForge build failed!
    exit /b 1
)
echo [OK] NeoForge build successful
echo.

echo ========================================
echo All tests passed!
echo ========================================

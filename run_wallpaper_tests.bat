@echo off
REM 壁纸服务测试运行脚本
REM 使用方法：双击运行或在命令行执行 run_wallpaper_tests.bat

echo ========================================
echo Aurora 壁纸服务测试
echo ========================================
echo.

REM 检查设备连接
echo [1/4] 检查设备连接...
adb devices
if %ERRORLEVEL% NEQ 0 (
    echo 错误: 未找到 adb 命令
    echo 请确保 Android SDK 已安装并添加到 PATH
    pause
    exit /b 1
)

echo.
echo [2/4] 清理旧的测试结果...
gradlew.bat clean

echo.
echo [3/4] 编译并安装测试...
echo 这可能需要几分钟，请耐心等待...
gradlew.bat connectedDebugAndroidTest

echo.
echo [4/4] 测试完成！
echo.
echo 查看测试报告：
echo   app\build\reports\androidTests\connected\index.html
echo.
echo 查看详细日志：
echo   在 Android Studio 的 Logcat 中过滤 "WallpaperServiceTest" 或 "StressTest"
echo.

pause

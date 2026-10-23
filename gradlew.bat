@echo off
setlocal
set "APP_HOME=%~dp0"
if defined JAVA_HOME goto findJavaFromJavaHome
set "JAVA_EXE=java.exe"
%JAVA_EXE% -version >NUL 2>&1
if %ERRORLEVEL% equ 0 goto execute
echo Java not found. Set JAVA_HOME to a JDK 17 installation. 1>&2
exit /b 1

:findJavaFromJavaHome
set "JAVA_HOME=%JAVA_HOME:"=%"
set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
if exist "%JAVA_EXE%" goto execute
echo JAVA_HOME does not point to a valid Java installation. 1>&2
exit /b 1

:execute
"%JAVA_EXE%" -Xmx64m -Xms64m %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=gradlew" -classpath "%APP_HOME%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
set "EXIT_CODE=%ERRORLEVEL%"
endlocal & exit /b %EXIT_CODE%

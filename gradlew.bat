@ECHO OFF
SET DIR=%~dp0
IF "%DIR%"=="" SET DIR=.
IF NOT "%JAVA_HOME%"=="" (
  "%JAVA_HOME%\bin\java.exe" -classpath "%DIR%\gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
) ELSE (
  java -classpath "%DIR%\gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
)

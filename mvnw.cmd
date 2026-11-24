@REM ----------------------------------------------------------------------------
@REM Maven Wrapper (only-script flavour) for ShelfLife, Windows version.
@REM Reads distributionUrl from .mvn\wrapper\maven-wrapper.properties, downloads
@REM and unpacks it once into %USERPROFILE%\.m2\wrapper\dists, then runs it.
@REM Optional: MAVEN_USER_HOME, MVNW_REPOURL (mirror), MVNW_VERBOSE=true
@REM ----------------------------------------------------------------------------
@echo off
setlocal

set "MAVEN_PROJECTBASEDIR=%~dp0"
if "%MAVEN_PROJECTBASEDIR:~-1%"=="\" set "MAVEN_PROJECTBASEDIR=%MAVEN_PROJECTBASEDIR:~0,-1%"

set "MVNW_PS_TMP=%TEMP%\mvnw-%RANDOM%%RANDOM%.txt"
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ErrorActionPreference = 'Stop';" ^
  "$props = Join-Path $env:MAVEN_PROJECTBASEDIR '.mvn\wrapper\maven-wrapper.properties';" ^
  "$url = ((Get-Content $props) | Where-Object { $_ -match '^\s*distributionUrl\s*=' } | Select-Object -First 1) -replace '^\s*distributionUrl\s*=\s*','';" ^
  "if (-not $url) { throw 'distributionUrl is not set in ' + $props };" ^
  "if ($env:MVNW_REPOURL) { $url = $env:MVNW_REPOURL.TrimEnd('/') + '/org/apache/maven/' + ($url -replace '^.*?/org/apache/maven/','') };" ^
  "$file = $url.Substring($url.LastIndexOf('/') + 1);" ^
  "$name = $file -replace '\.zip$','' -replace '-bin$','';" ^
  "$userHome = if ($env:MAVEN_USER_HOME) { $env:MAVEN_USER_HOME } else { Join-Path $env:USERPROFILE '.m2' };" ^
  "$sha = [Security.Cryptography.SHA256]::Create();" ^
  "$hash = (-join ($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($url)) | ForEach-Object { $_.ToString('x2') })).Substring(0,32);" ^
  "$dist = Join-Path (Join-Path (Join-Path $userHome 'wrapper\dists') $name) $hash;" ^
  "$mvn = Join-Path $dist ($name + '\bin\mvn.cmd');" ^
  "if (-not (Test-Path $mvn)) {" ^
  "  if ($env:MVNW_VERBOSE -eq 'true') { Write-Host ('Downloading ' + $url) };" ^
  "  New-Item -ItemType Directory -Force -Path $dist | Out-Null;" ^
  "  $tmp = Join-Path $dist ('tmp.' + [Guid]::NewGuid().ToString('N'));" ^
  "  New-Item -ItemType Directory -Path $tmp | Out-Null;" ^
  "  try {" ^
  "    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12;" ^
  "    (New-Object Net.WebClient).DownloadFile($url, (Join-Path $tmp $file));" ^
  "    Expand-Archive -Path (Join-Path $tmp $file) -DestinationPath $tmp;" ^
  "    if (-not (Test-Path (Join-Path $dist $name))) { Move-Item (Join-Path $tmp $name) $dist };" ^
  "  } finally { Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue };" ^
  "};" ^
  "Set-Content -Path $env:MVNW_PS_TMP -Value $mvn -NoNewline"
if errorlevel 1 (
  echo mvnw: could not set up Maven 1>&2
  del "%MVNW_PS_TMP%" 2>nul
  exit /b 1
)

set /p MAVEN_CMD=<"%MVNW_PS_TMP%"
del "%MVNW_PS_TMP%" 2>nul
if "%MVNW_VERBOSE%"=="true" echo Using Maven at %MAVEN_CMD%

"%MAVEN_CMD%" %*
exit /b %ERRORLEVEL%

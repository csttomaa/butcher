@echo off
REM Creates the git repository and makes the first commit.
REM Safe to run more than once: it only initialises if there is no repo yet,
REM and a second run just commits whatever has changed since.
setlocal
cd /d "%~dp0"

where git >nul 2>&1
if errorlevel 1 (
    echo Git is not installed, or not on your PATH.
    echo Get it from https://git-scm.com/download/win and run this again.
    pause
    exit /b 1
)

if exist ".git" (
    echo Repository already exists - committing any changes instead.
) else (
    echo Creating repository...
    git init -b main
    if errorlevel 1 ( pause & exit /b 1 )
)

git config user.name >nul 2>&1
if errorlevel 1 (
    echo.
    echo Git does not know who you are yet. Run these two lines once:
    echo     git config --global user.name "Your Name"
    echo     git config --global user.email "you@example.com"
    echo then run this file again.
    pause
    exit /b 1
)

echo.
echo Staging ^(build/, .gradle/, run/ and .idea/ are ignored^)...
git add -A
git -c core.pager=cat status --short

echo.
git commit -m "Scatha Butcher: cleaver cutscene, /butcher menu, generated art"
if errorlevel 1 echo Nothing new to commit.

echo.
echo ---- history ----
git -c core.pager=cat log --oneline -n 5
echo.
echo Done. You can now run  claude  in this folder.
pause

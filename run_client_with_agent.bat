@echo off
chcp 65001 >nul
title Automata Valkyries - Minecraft Client Launcher
cd /d "%~dp0"

echo ======================================================================
echo    Automata Valkyries - Minecraft Client 起動メニュー
echo ======================================================================
echo  [1] Eprotect+ Java エージェントのみ (標準)
echo  [2] Eprotect+ + Javaエージェント同梱MOD (Shizuku Blade + ECA / Last Sword)
echo  [3] Eprotect+ + 猫羽雫刀 (Shizuku Blade: Javaエージェント同梱)
echo  [4] Eprotect+ + Epic Core API / 最後の剣 (ECA: Javaエージェント同梱)
echo  [5] 全即死・チートMOD全入り (Witherzilla, Omni-Mobs, No-Sugar, ECA, Shizuku)
echo.
echo  ※ ./libs/ に置いた MOD jar や ./agents/ に置いた Agent jar は
echo    自動的に検知されてどのモードでも一緒に読み込まれます。
echo ======================================================================
set /p MODE="起動モードを選択してください [1-5] (既定: 1): "

if "%MODE%"=="" set MODE=1

if "%MODE%"=="1" (
    echo [起動中] Eprotect+ Java エージェントで起動します...
    call .\gradlew.bat runClient -PwithEprotectPlus
) else if "%MODE%"=="2" (
    echo [起動中] Eprotect+ + Javaエージェント同梱MOD (Shizuku + ECA) で起動します...
    call .\gradlew.bat runClient -PwithEprotectPlus -PwithAllAgents
) else if "%MODE%"=="3" (
    echo [起動中] Eprotect+ + 猫羽雫刀 (Shizuku Blade) で起動します...
    call .\gradlew.bat runClient -PwithEprotectPlus -PwithSlashBlade
) else if "%MODE%"=="4" (
    echo [起動中] Eprotect+ + ECA / 最後の剣 で起動します...
    call .\gradlew.bat runClient -PwithEprotectPlus -PwithLastSword
) else if "%MODE%"=="5" (
    echo [起動中] 全即死・チートMOD全入りで起動します...
    call .\gradlew.bat runClient -PwithEprotectPlus -PwithAllKillMods
) else (
    echo [エラー] 不正な選択です。標準モードで起動します...
    call .\gradlew.bat runClient -PwithEprotectPlus
)

pause

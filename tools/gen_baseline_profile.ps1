# Sinh lại app/src/main/baseline-prof.txt trên máy ảo/máy thật API 33+ đang cắm (adb devices).
# Hành trình ở baselineprofile/.../Journey.kt — CHỈ ĐỌC trên server prod, không mở chương.
# App được cài đè (giữ dữ liệu, không gỡ). Chạy ~7 phút. Dùng: .\tools\gen_baseline_profile.ps1
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot)
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :baselineprofile:connectedDebugAndroidTest `
    "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true" `
    "-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=BaselineProfile"
if ($LASTEXITCODE) { throw "Sinh profile thất bại — xem baselineprofile/build/outputs/androidTest-results" }
$gen = Get-ChildItem baselineprofile\build\outputs\connected_android_test_additional_output -Recurse -Filter 'Generator_generate-startup-prof.txt' |
    Sort-Object LastWriteTime | Select-Object -Last 1
# Hai luật bao trùm giữ Reader/Tu Tiên (hành trình không mở được) được biên dịch sẵn; còn lại là luật đo thật, gồm cả thư viện
$rules = @('HSPLcom/thanhnb/novelnative/**->**(**)**', 'Lcom/thanhnb/novelnative/**;') + (Get-Content $gen.FullName)
[IO.File]::WriteAllText("$PWD\app\src\main\baseline-prof.txt", ($rules -join "`n") + "`n")
"Đã ghi $($rules.Count) luật vào app/src/main/baseline-prof.txt"

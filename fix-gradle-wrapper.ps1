# Script to fix Gradle wrapper
Write-Host "Fixing Gradle Wrapper..." -ForegroundColor Yellow

$wrapperUrl = "https://raw.githubusercontent.com/gradle/gradle/v8.0.1/gradle/wrapper/gradle-wrapper.jar"
$wrapperPath = "gradle\wrapper\gradle-wrapper.jar"

# Try to download from GitHub
try {
    Write-Host "Attempting to download wrapper from GitHub..." -ForegroundColor Cyan
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    $webClient = New-Object System.Net.WebClient
    $webClient.DownloadFile($wrapperUrl, $wrapperPath)
    Write-Host "Successfully downloaded wrapper!" -ForegroundColor Green
} catch {
    Write-Host "GitHub download failed, trying alternative source..." -ForegroundColor Yellow
    
    # Alternative: Try to copy from Gradle cache
    $cachePath = "$env:USERPROFILE\.gradle\wrapper\dists\gradle-8.0.1-bin\*\gradle-8.0.1\bin\gradle-wrapper.jar"
    $found = Get-ChildItem -Path $cachePath -ErrorAction SilentlyContinue | Select-Object -First 1
    
    if ($found) {
        Write-Host "Found wrapper in cache: $($found.FullName)" -ForegroundColor Cyan
        Copy-Item $found.FullName $wrapperPath -Force
        Write-Host "Copied from cache!" -ForegroundColor Green
    } else {
        Write-Host "ERROR: Could not fix wrapper automatically." -ForegroundColor Red
        Write-Host "Please run: gradle wrapper --gradle-version 8.0.1" -ForegroundColor Yellow
        exit 1
    }
}

# Verify the wrapper
if (Test-Path $wrapperPath) {
    $fileInfo = Get-Item $wrapperPath
    Write-Host "Wrapper file size: $($fileInfo.Length) bytes" -ForegroundColor Green
    Write-Host "Gradle wrapper fixed successfully!" -ForegroundColor Green
} else {
    Write-Host "ERROR: Wrapper file not found after fix attempt." -ForegroundColor Red
    exit 1
}

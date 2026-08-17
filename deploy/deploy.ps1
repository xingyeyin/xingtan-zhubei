# 杏坛智备 · 一键部署脚本（Windows 服务器/本机）
# 前置：已安装 Docker Desktop 并启动

param(
    [switch]$Build = $true
)

Set-Location $PSScriptRoot

if (-not (Test-Path ".env")) {
    Copy-Item ".env.example" ".env"
    Write-Host "已生成 .env，请先编辑填写大模型 API Key 等配置" -ForegroundColor Yellow
}

if ($Build) {
    Write-Host "正在构建前端..."
    Push-Location ..\frontend\admin
    npm install --no-audit --no-fund
    npm run build
    Pop-Location

    Push-Location ..\frontend\teacher
    npm install --no-audit --no-fund
    npm run build:h5
    Pop-Location
}

Write-Host "正在启动服务（PostgreSQL/Redis/MinIO/后端/Nginx）..."
docker compose up -d --build

Write-Host "部署完成。验证: curl http://localhost/api/admin/ping"

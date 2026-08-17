# 杏坛智备 · 数据库备份脚本（Windows 计划任务或手动执行）
# 用法：.\backup.ps1

param(
    [string]$BackupDir = "E:\比赛\互联网+\backups",
    [string]$PgDump = "C:\Program Files\PostgreSQL\16\bin\pg_dump.exe"
)

$stamp = Get-Date -Format "yyyyMMdd_HHmmss"
$dir = Join-Path $BackupDir $stamp
New-Item -ItemType Directory -Force -Path $dir | Out-Null

if (Test-Path $PgDump) {
    & $PgDump -h localhost -U xingtan -d xingtan -F c -f (Join-Path $dir "xingtan.dump")
    Write-Output "数据库备份完成: $dir"
} else {
    Write-Warning "未找到 pg_dump，请安装 PostgreSQL 或使用 Docker 容器执行备份"
}

# 清理 30 天前的备份
Get-ChildItem $BackupDir -Directory | Where-Object { $_.LastWriteTime -lt (Get-Date).AddDays(-30) } | Remove-Item -Recurse -Force
Write-Output "已清理 30 天前的旧备份"

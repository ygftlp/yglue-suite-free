# 测试脚本 - 检查 yglue-orchestrator 后端服务

Write-Host "=== yglue-orchestrator 后端服务检查 ===" -ForegroundColor Cyan

# 1. 检查端口是否被占用
Write-Host "`n1. 检查 8090 端口..." -ForegroundColor Yellow
$port = Get-NetTCPConnection -LocalPort 8090 -ErrorAction SilentlyContinue
if ($port) {
    Write-Host "✓ 端口 8090 已被占用，服务可能正在运行" -ForegroundColor Green
    Write-Host "  进程: $($port.OwningProcess)" -ForegroundColor Gray
} else {
    Write-Host "✗ 端口 8090 未被占用，服务未启动" -ForegroundColor Red
    Write-Host "  请启动 yglue-orchestrator 模块" -ForegroundColor Yellow
    exit 1
}

# 2. 测试健康检查接口
Write-Host "`n2. 测试健康检查接口..." -ForegroundColor Yellow
try {
    $healthUrl = "http://localhost:8090/actuator/health"
    $response = Invoke-WebRequest -Uri $healthUrl -Method Get -TimeoutSec 5
    if ($response.StatusCode -eq 200) {
        Write-Host "✓ 健康检查接口正常: $healthUrl" -ForegroundColor Green
        Write-Host "  响应: $($response.Content)" -ForegroundColor Gray
    }
} catch {
    Write-Host "✗ 健康检查接口失败: $($_.Exception.Message)" -ForegroundColor Red
}

# 3. 测试代码快照上传接口
Write-Host "`n3. 测试代码快照上传接口..." -ForegroundColor Yellow
try {
    $uploadUrl = "http://localhost:8090/api/projects/test-project/code-snapshots"
    $testData = @{
        projectName = "test-project"
        classes = @()
        dependencies = @()
        selectedJars = @()
        jarMetadata = @()
    } | ConvertTo-Json -Depth 10
    
    $response = Invoke-WebRequest -Uri $uploadUrl -Method Post -Body $testData -ContentType "application/json" -TimeoutSec 5
    if ($response.StatusCode -eq 200) {
        Write-Host "✓ 代码快照上传接口正常: $uploadUrl" -ForegroundColor Green
    }
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    if ($statusCode) {
        Write-Host "✗ 代码快照上传接口返回: HTTP $statusCode" -ForegroundColor Red
        Write-Host "  这可能是正常的（例如验证失败）" -ForegroundColor Yellow
    } else {
        Write-Host "✗ 代码快照上传接口失败: $($_.Exception.Message)" -ForegroundColor Red
    }
}

Write-Host "`n=== 检查完成 ===" -ForegroundColor Cyan

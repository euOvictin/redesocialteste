# Script de verificação da infraestrutura para Windows PowerShell
# Verifica se todos os serviços estão funcionando corretamente

$ErrorActionPreference = "SilentlyContinue"

Write-Host "========================================" -ForegroundColor Green
Write-Host "Verificação de Infraestrutura" -ForegroundColor Green
Write-Host "Rede Social Brasileira" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""

$failures = 0

# Função para verificar serviço
function Check-Service {
    param(
        [string]$ServiceName,
        [scriptblock]$CheckCommand
    )
    
    Write-Host "Verificando $ServiceName... " -NoNewline
    
    try {
        $result = & $CheckCommand
        if ($LASTEXITCODE -eq 0 -or $result) {
            Write-Host "✓ OK" -ForegroundColor Green
            return $true
        } else {
            Write-Host "✗ FALHOU" -ForegroundColor Red
            return $false
        }
    } catch {
        Write-Host "✗ FALHOU" -ForegroundColor Red
        return $false
    }
}

# Verificar Docker
Write-Host "1. Verificando Docker..." -ForegroundColor Yellow
if (!(Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Host "Docker não está instalado!" -ForegroundColor Red
    exit 1
}
Write-Host "Docker está instalado" -ForegroundColor Green
Write-Host ""

# Verificar Docker Compose
Write-Host "2. Verificando Docker Compose..." -ForegroundColor Yellow
if (!(Get-Command docker-compose -ErrorAction SilentlyContinue)) {
    Write-Host "Docker Compose não está instalado!" -ForegroundColor Red
    exit 1
}
Write-Host "Docker Compose está instalado" -ForegroundColor Green
Write-Host ""

# Verificar se containers estão rodando
Write-Host "3. Verificando containers..." -ForegroundColor Yellow
$containers = @(
    "rede-social-postgres",
    "rede-social-mongodb",
    "rede-social-redis",
    "rede-social-kafka",
    "rede-social-zookeeper",
    "rede-social-elasticsearch",
    "rede-social-jaeger",
    "rede-social-prometheus",
    "rede-social-grafana"
)

$runningContainers = docker ps --format "{{.Names}}"

foreach ($container in $containers) {
    if ($runningContainers -contains $container) {
        Write-Host "  ✓ $container está rodando" -ForegroundColor Green
    } else {
        Write-Host "  ✗ $container NÃO está rodando" -ForegroundColor Red
        $failures++
    }
}
Write-Host ""

# Verificar PostgreSQL
Write-Host "4. Verificando PostgreSQL..." -ForegroundColor Yellow
if (!(Check-Service "PostgreSQL Connection" { docker exec rede-social-postgres pg_isready -U postgres })) { $failures++ }
if (!(Check-Service "PostgreSQL Database" { docker exec rede-social-postgres psql -U postgres -d rede_social -c "SELECT 1" })) { $failures++ }
if (!(Check-Service "PostgreSQL Schemas" { docker exec rede-social-postgres psql -U postgres -d rede_social -c "\dn" | Select-String "user_service" })) { $failures++ }
Write-Host ""

# Verificar MongoDB
Write-Host "5. Verificando MongoDB..." -ForegroundColor Yellow
if (!(Check-Service "MongoDB Connection" { docker exec rede-social-mongodb mongosh --eval "db.adminCommand('ping')" --quiet })) { $failures++ }
if (!(Check-Service "MongoDB Database" { docker exec rede-social-mongodb mongosh -u admin -p admin --authenticationDatabase admin rede_social --eval "db.getName()" --quiet })) { $failures++ }
if (!(Check-Service "MongoDB Collections" { docker exec rede-social-mongodb mongosh -u admin -p admin --authenticationDatabase admin rede_social --eval "db.getCollectionNames()" --quiet | Select-String "posts" })) { $failures++ }
Write-Host ""

# Verificar Redis
Write-Host "6. Verificando Redis..." -ForegroundColor Yellow
if (!(Check-Service "Redis Connection" { docker exec rede-social-redis redis-cli -a redis123 ping })) { $failures++ }
if (!(Check-Service "Redis Info" { docker exec rede-social-redis redis-cli -a redis123 info server })) { $failures++ }
Write-Host ""

# Verificar Kafka
Write-Host "7. Verificando Kafka..." -ForegroundColor Yellow
if (!(Check-Service "Kafka Broker" { docker exec rede-social-kafka kafka-broker-api-versions --bootstrap-server localhost:9092 })) { $failures++ }

Write-Host "Listando tópicos Kafka... " -NoNewline
try {
    $topics = docker exec rede-social-kafka kafka-topics --bootstrap-server localhost:9092 --list 2>$null
    if ($LASTEXITCODE -eq 0) {
        Write-Host "✓ OK" -ForegroundColor Green
        if ($topics) {
            Write-Host "  Tópicos encontrados:"
            $topics | ForEach-Object { Write-Host "    $_" }
        } else {
            Write-Host "  Nenhum tópico criado ainda"
        }
    } else {
        Write-Host "✗ FALHOU" -ForegroundColor Red
        $failures++
    }
} catch {
    Write-Host "✗ FALHOU" -ForegroundColor Red
    $failures++
}
Write-Host ""

# Verificar Elasticsearch
Write-Host "8. Verificando Elasticsearch..." -ForegroundColor Yellow
if (!(Check-Service "Elasticsearch Health" { 
    $response = Invoke-WebRequest -Uri "http://localhost:9200/_cluster/health" -UseBasicParsing
    $response.Content -match "green|yellow"
})) { $failures++ }
if (!(Check-Service "Elasticsearch Indices" { Invoke-WebRequest -Uri "http://localhost:9200/_cat/indices" -UseBasicParsing })) { $failures++ }
Write-Host ""

# Verificar Jaeger
Write-Host "9. Verificando Jaeger..." -ForegroundColor Yellow
if (!(Check-Service "Jaeger UI" { 
    $response = Invoke-WebRequest -Uri "http://localhost:16686" -UseBasicParsing
    $response.Content -match "Jaeger"
})) { $failures++ }
if (!(Check-Service "Jaeger API" { Invoke-WebRequest -Uri "http://localhost:16686/api/services" -UseBasicParsing })) { $failures++ }
Write-Host ""

# Verificar Prometheus
Write-Host "10. Verificando Prometheus..." -ForegroundColor Yellow
if (!(Check-Service "Prometheus UI" { Invoke-WebRequest -Uri "http://localhost:9090/-/healthy" -UseBasicParsing })) { $failures++ }
if (!(Check-Service "Prometheus Targets" { Invoke-WebRequest -Uri "http://localhost:9090/api/v1/targets" -UseBasicParsing })) { $failures++ }
Write-Host ""

# Verificar Grafana
Write-Host "11. Verificando Grafana..." -ForegroundColor Yellow
if (!(Check-Service "Grafana UI" { 
    $response = Invoke-WebRequest -Uri "http://localhost:3000/api/health" -UseBasicParsing
    $response.Content -match "ok"
})) { $failures++ }
Write-Host ""

# Resumo
Write-Host "========================================" -ForegroundColor Green
Write-Host "Resumo da Verificação" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""

if ($failures -eq 0) {
    Write-Host "✓ Todos os serviços estão funcionando corretamente!" -ForegroundColor Green
    Write-Host ""
    Write-Host "Serviços disponíveis:"
    Write-Host "  - PostgreSQL:     localhost:5432"
    Write-Host "  - MongoDB:        localhost:27017"
    Write-Host "  - Redis:          localhost:6379"
    Write-Host "  - Kafka:          localhost:9093"
    Write-Host "  - Elasticsearch:  http://localhost:9200"
    Write-Host "  - Jaeger UI:      http://localhost:16686"
    Write-Host "  - Prometheus:     http://localhost:9090"
    Write-Host "  - Grafana:        http://localhost:3000"
    Write-Host ""
    exit 0
} else {
    Write-Host "✗ $failures verificação(ões) falharam!" -ForegroundColor Red
    Write-Host ""
    Write-Host "Tente:"
    Write-Host "  1. Verificar logs: docker-compose logs [serviço]"
    Write-Host "  2. Reiniciar serviços: docker-compose restart"
    Write-Host "  3. Recriar containers: docker-compose down; docker-compose up -d"
    Write-Host ""
    exit 1
}

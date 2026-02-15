#!/bin/bash

# Script de verificação da infraestrutura
# Verifica se todos os serviços estão funcionando corretamente

set -e

# Cores
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Verificação de Infraestrutura${NC}"
echo -e "${GREEN}Rede Social Brasileira${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# Função para verificar serviço
check_service() {
    local service_name=$1
    local check_command=$2
    
    echo -n "Verificando $service_name... "
    
    if eval "$check_command" > /dev/null 2>&1; then
        echo -e "${GREEN}✓ OK${NC}"
        return 0
    else
        echo -e "${RED}✗ FALHOU${NC}"
        return 1
    fi
}

# Contador de falhas
failures=0

# Verificar Docker
echo -e "${YELLOW}1. Verificando Docker...${NC}"
if ! command -v docker &> /dev/null; then
    echo -e "${RED}Docker não está instalado!${NC}"
    exit 1
fi
echo -e "${GREEN}Docker está instalado${NC}"
echo ""

# Verificar Docker Compose
echo -e "${YELLOW}2. Verificando Docker Compose...${NC}"
if ! command -v docker-compose &> /dev/null; then
    echo -e "${RED}Docker Compose não está instalado!${NC}"
    exit 1
fi
echo -e "${GREEN}Docker Compose está instalado${NC}"
echo ""

# Verificar se containers estão rodando
echo -e "${YELLOW}3. Verificando containers...${NC}"
containers=(
    "rede-social-postgres"
    "rede-social-mongodb"
    "rede-social-redis"
    "rede-social-kafka"
    "rede-social-zookeeper"
    "rede-social-elasticsearch"
    "rede-social-jaeger"
    "rede-social-prometheus"
    "rede-social-grafana"
)

for container in "${containers[@]}"; do
    if docker ps --format '{{.Names}}' | grep -q "^${container}$"; then
        echo -e "  ${GREEN}✓${NC} $container está rodando"
    else
        echo -e "  ${RED}✗${NC} $container NÃO está rodando"
        ((failures++))
    fi
done
echo ""

# Verificar PostgreSQL
echo -e "${YELLOW}4. Verificando PostgreSQL...${NC}"
check_service "PostgreSQL Connection" \
    "docker exec rede-social-postgres pg_isready -U postgres" || ((failures++))

check_service "PostgreSQL Database" \
    "docker exec rede-social-postgres psql -U postgres -d rede_social -c 'SELECT 1'" || ((failures++))

check_service "PostgreSQL Schemas" \
    "docker exec rede-social-postgres psql -U postgres -d rede_social -c '\dn' | grep -q 'user_service'" || ((failures++))
echo ""

# Verificar MongoDB
echo -e "${YELLOW}5. Verificando MongoDB...${NC}"
check_service "MongoDB Connection" \
    "docker exec rede-social-mongodb mongosh --eval 'db.adminCommand(\"ping\")' --quiet" || ((failures++))

check_service "MongoDB Database" \
    "docker exec rede-social-mongodb mongosh -u admin -p admin --authenticationDatabase admin rede_social --eval 'db.getName()' --quiet" || ((failures++))

check_service "MongoDB Collections" \
    "docker exec rede-social-mongodb mongosh -u admin -p admin --authenticationDatabase admin rede_social --eval 'db.getCollectionNames()' --quiet | grep -q 'posts'" || ((failures++))
echo ""

# Verificar Redis
echo -e "${YELLOW}6. Verificando Redis...${NC}"
check_service "Redis Connection" \
    "docker exec rede-social-redis redis-cli -a redis123 ping" || ((failures++))

check_service "Redis Info" \
    "docker exec rede-social-redis redis-cli -a redis123 info server" || ((failures++))
echo ""

# Verificar Kafka
echo -e "${YELLOW}7. Verificando Kafka...${NC}"
check_service "Kafka Broker" \
    "docker exec rede-social-kafka kafka-broker-api-versions --bootstrap-server localhost:9092" || ((failures++))

echo -n "Listando tópicos Kafka... "
topics=$(docker exec rede-social-kafka kafka-topics --bootstrap-server localhost:9092 --list 2>/dev/null)
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ OK${NC}"
    if [ -n "$topics" ]; then
        echo "  Tópicos encontrados:"
        echo "$topics" | sed 's/^/    /'
    else
        echo "  Nenhum tópico criado ainda"
    fi
else
    echo -e "${RED}✗ FALHOU${NC}"
    ((failures++))
fi
echo ""

# Verificar Elasticsearch
echo -e "${YELLOW}8. Verificando Elasticsearch...${NC}"
check_service "Elasticsearch Health" \
    "curl -s http://localhost:9200/_cluster/health | grep -q 'green\|yellow'" || ((failures++))

check_service "Elasticsearch Indices" \
    "curl -s http://localhost:9200/_cat/indices" || ((failures++))
echo ""

# Verificar Jaeger
echo -e "${YELLOW}9. Verificando Jaeger...${NC}"
check_service "Jaeger UI" \
    "curl -s http://localhost:16686 | grep -q 'Jaeger'" || ((failures++))

check_service "Jaeger API" \
    "curl -s http://localhost:16686/api/services" || ((failures++))
echo ""

# Verificar Prometheus
echo -e "${YELLOW}10. Verificando Prometheus...${NC}"
check_service "Prometheus UI" \
    "curl -s http://localhost:9090/-/healthy" || ((failures++))

check_service "Prometheus Targets" \
    "curl -s http://localhost:9090/api/v1/targets" || ((failures++))
echo ""

# Verificar Grafana
echo -e "${YELLOW}11. Verificando Grafana...${NC}"
check_service "Grafana UI" \
    "curl -s http://localhost:3000/api/health | grep -q 'ok'" || ((failures++))
echo ""

# Resumo
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Resumo da Verificação${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

if [ $failures -eq 0 ]; then
    echo -e "${GREEN}✓ Todos os serviços estão funcionando corretamente!${NC}"
    echo ""
    echo "Serviços disponíveis:"
    echo "  - PostgreSQL:     localhost:5432"
    echo "  - MongoDB:        localhost:27017"
    echo "  - Redis:          localhost:6379"
    echo "  - Kafka:          localhost:9093"
    echo "  - Elasticsearch:  http://localhost:9200"
    echo "  - Jaeger UI:      http://localhost:16686"
    echo "  - Prometheus:     http://localhost:9090"
    echo "  - Grafana:        http://localhost:3000"
    echo ""
    exit 0
else
    echo -e "${RED}✗ $failures verificação(ões) falharam!${NC}"
    echo ""
    echo "Tente:"
    echo "  1. Verificar logs: docker-compose logs [serviço]"
    echo "  2. Reiniciar serviços: docker-compose restart"
    echo "  3. Recriar containers: docker-compose down && docker-compose up -d"
    echo ""
    exit 1
fi

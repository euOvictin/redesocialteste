# 🚀 Quick Start - Rede Social Brasileira

Guia rápido para iniciar o ambiente de desenvolvimento.

## ⚡ Início Rápido (5 minutos)

### 1. Pré-requisitos

Certifique-se de ter instalado:
- [Docker](https://docs.docker.com/get-docker/) 20.10+
- [Docker Compose](https://docs.docker.com/compose/install/) 2.0+

### 2. Clone e Configure

```bash
# Clone o repositório
git clone https://github.com/seu-usuario/rede-social-brasileira.git
cd rede-social-brasileira

# Copie as variáveis de ambiente (já configuradas para desenvolvimento)
cp .env.example .env
```

### 3. Inicie a Infraestrutura

**Linux/Mac:**
```bash
# Usando Make (recomendado)
make dev

# OU usando Docker Compose diretamente
docker-compose up -d
sleep 10
docker-compose exec kafka kafka-topics --bootstrap-server localhost:9092 --create --topic user.events --partitions 3 --replication-factor 1
docker-compose exec kafka kafka-topics --bootstrap-server localhost:9092 --create --topic content.events --partitions 3 --replication-factor 1
docker-compose exec kafka kafka-topics --bootstrap-server localhost:9092 --create --topic social.events --partitions 3 --replication-factor 1
docker-compose exec kafka kafka-topics --bootstrap-server localhost:9092 --create --topic notification.events --partitions 3 --replication-factor 1
```

**Windows (PowerShell):**
```powershell
# Inicie os serviços
docker-compose up -d

# Aguarde os serviços iniciarem
Start-Sleep -Seconds 10

# Crie os tópicos do Kafka
docker-compose exec kafka kafka-topics --bootstrap-server localhost:9092 --create --topic user.events --partitions 3 --replication-factor 1
docker-compose exec kafka kafka-topics --bootstrap-server localhost:9092 --create --topic content.events --partitions 3 --replication-factor 1
docker-compose exec kafka kafka-topics --bootstrap-server localhost:9092 --create --topic social.events --partitions 3 --replication-factor 1
docker-compose exec kafka kafka-topics --bootstrap-server localhost:9092 --create --topic notification.events --partitions 3 --replication-factor 1
```

### 4. Verifique a Infraestrutura

**Linux/Mac:**
```bash
# Usando script de verificação
chmod +x scripts/verify-infrastructure.sh
./scripts/verify-infrastructure.sh

# OU usando Make
make health
```

**Windows (PowerShell):**
```powershell
# Execute o script de verificação
.\scripts\verify-infrastructure.ps1
```

### 5. Acesse os Serviços

Abra seu navegador e acesse:

- **Jaeger (Tracing)**: http://localhost:16686
- **Prometheus (Métricas)**: http://localhost:9090
- **Grafana (Dashboards)**: http://localhost:3000
  - Usuário: `admin`
  - Senha: `admin`

## 📊 Verificar Status

### Listar Containers

```bash
docker-compose ps
```

### Ver Logs

```bash
# Todos os serviços
docker-compose logs -f

# Serviço específico
docker-compose logs -f postgres
docker-compose logs -f mongodb
docker-compose logs -f kafka
```

### Verificar Saúde

**Linux/Mac:**
```bash
make health
```

**Windows/Manual:**
```bash
# PostgreSQL
docker-compose exec postgres pg_isready -U postgres

# MongoDB
docker-compose exec mongodb mongosh --eval "db.adminCommand('ping')" --quiet

# Redis
docker-compose exec redis redis-cli -a redis123 ping

# Elasticsearch
curl http://localhost:9200/_cluster/health
```

## 🗄️ Acessar Bancos de Dados

### PostgreSQL

```bash
# Via Docker
docker-compose exec postgres psql -U postgres -d rede_social

# Via cliente local (se instalado)
psql -h localhost -U postgres -d rede_social
```

**Credenciais:**
- Host: `localhost:5432`
- Usuário: `postgres`
- Senha: `postgres`
- Database: `rede_social`

### MongoDB

```bash
# Via Docker
docker-compose exec mongodb mongosh -u admin -p admin --authenticationDatabase admin rede_social

# Via cliente local (se instalado)
mongosh "mongodb://admin:admin@localhost:27017/rede_social?authSource=admin"
```

**Credenciais:**
- Host: `localhost:27017`
- Usuário: `admin`
- Senha: `admin`
- Database: `rede_social`

### Redis

```bash
# Via Docker
docker-compose exec redis redis-cli -a redis123

# Via cliente local (se instalado)
redis-cli -h localhost -p 6379 -a redis123
```

**Credenciais:**
- Host: `localhost:6379`
- Senha: `redis123`

## 🔧 Comandos Úteis

### Parar Serviços

```bash
docker-compose down
```

### Reiniciar Serviços

```bash
docker-compose restart
```

### Limpar Tudo (⚠️ Remove dados!)

**Linux/Mac:**
```bash
make clean
```

**Windows/Manual:**
```bash
docker-compose down -v
```

### Ver Tópicos do Kafka

```bash
docker-compose exec kafka kafka-topics --bootstrap-server localhost:9092 --list
```

### Criar Tópico no Kafka

```bash
docker-compose exec kafka kafka-topics --bootstrap-server localhost:9092 \
  --create --topic meu-topico --partitions 3 --replication-factor 1
```

### Backup de Bancos de Dados

**PostgreSQL:**
```bash
docker-compose exec -T postgres pg_dump -U postgres rede_social > backup-postgres.sql
```

**MongoDB:**
```bash
docker-compose exec -T mongodb mongodump --username admin --password admin \
  --authenticationDatabase admin --db rede_social --archive > backup-mongodb.archive
```

## 🐛 Troubleshooting

### Porta já em uso

Se alguma porta estiver em uso, edite o arquivo `.env` e altere as portas:

```bash
# Exemplo: mudar porta do PostgreSQL
POSTGRES_PORT=5433
```

Depois reinicie:
```bash
docker-compose down
docker-compose up -d
```

### Container não inicia

Verifique os logs:
```bash
docker-compose logs [nome-do-serviço]
```

Tente recriar o container:
```bash
docker-compose up -d --force-recreate [nome-do-serviço]
```

### Elasticsearch com erro de memória

Aumente a memória disponível para o Docker:
- Docker Desktop: Settings → Resources → Memory (mínimo 4GB)

Ou reduza a memória do Elasticsearch no `docker-compose.yml`:
```yaml
environment:
  - "ES_JAVA_OPTS=-Xms256m -Xmx256m"
```

### Kafka não conecta

Verifique se o Zookeeper está rodando:
```bash
docker-compose logs zookeeper
```

Reinicie o Kafka:
```bash
docker-compose restart kafka
```

### Limpar cache do Docker

Se tiver problemas persistentes:
```bash
docker system prune -a --volumes
```

⚠️ **ATENÇÃO**: Isso remove TODOS os containers, imagens e volumes não utilizados!

## 📚 Próximos Passos

1. ✅ Infraestrutura configurada
2. 📝 Implementar microsserviços (ver `tasks.md`)
3. 🧪 Escrever testes
4. 🚀 Deploy

## 🆘 Precisa de Ajuda?

- 📖 Leia a [documentação completa](README.md)
- 🔍 Veja os [logs de tracing](http://localhost:16686)
- 📊 Monitore as [métricas](http://localhost:9090)
- 📈 Visualize os [dashboards](http://localhost:3000)

## ✅ Checklist de Verificação

- [ ] Docker e Docker Compose instalados
- [ ] Containers rodando (`docker-compose ps`)
- [ ] PostgreSQL acessível (porta 5432)
- [ ] MongoDB acessível (porta 27017)
- [ ] Redis acessível (porta 6379)
- [ ] Kafka acessível (porta 9093)
- [ ] Elasticsearch acessível (porta 9200)
- [ ] Jaeger UI acessível (http://localhost:16686)
- [ ] Prometheus acessível (http://localhost:9090)
- [ ] Grafana acessível (http://localhost:3000)
- [ ] Tópicos do Kafka criados

Se todos os itens estão marcados, você está pronto para começar o desenvolvimento! 🎉

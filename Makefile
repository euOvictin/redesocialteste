.PHONY: help up down restart logs clean ps health test

# Cores para output
GREEN  := \033[0;32m
YELLOW := \033[0;33m
RED    := \033[0;31m
NC     := \033[0m # No Color

help: ## Mostra esta mensagem de ajuda
	@echo "$(GREEN)Rede Social Brasileira - Comandos Disponíveis$(NC)"
	@echo ""
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "  $(YELLOW)%-20s$(NC) %s\n", $$1, $$2}'

up: ## Inicia todos os serviços de infraestrutura
	@echo "$(GREEN)Iniciando serviços de infraestrutura...$(NC)"
	docker-compose up -d
	@echo "$(GREEN)Aguardando serviços ficarem prontos...$(NC)"
	@sleep 10
	@make health

down: ## Para todos os serviços
	@echo "$(YELLOW)Parando todos os serviços...$(NC)"
	docker-compose down

restart: ## Reinicia todos os serviços
	@echo "$(YELLOW)Reiniciando serviços...$(NC)"
	docker-compose restart

logs: ## Mostra logs de todos os serviços
	docker-compose logs -f

logs-postgres: ## Mostra logs do PostgreSQL
	docker-compose logs -f postgres

logs-mongodb: ## Mostra logs do MongoDB
	docker-compose logs -f mongodb

logs-redis: ## Mostra logs do Redis
	docker-compose logs -f redis

logs-kafka: ## Mostra logs do Kafka
	docker-compose logs -f kafka

logs-elasticsearch: ## Mostra logs do Elasticsearch
	docker-compose logs -f elasticsearch

logs-search-service: ## Mostra logs do Search Service
	docker-compose logs -f search-service

logs-recommendation-engine: ## Mostra logs do Recommendation Engine
	docker-compose logs -f recommendation-engine

logs-user-service: ## Mostra logs do User Service
	docker-compose logs -f user-service

ps: ## Lista status de todos os containers
	@echo "$(GREEN)Status dos containers:$(NC)"
	@docker-compose ps

health: ## Verifica saúde de todos os serviços
	@echo "$(GREEN)Verificando saúde dos serviços...$(NC)"
	@echo ""
	@echo "$(YELLOW)PostgreSQL:$(NC)"
	@docker-compose exec -T postgres pg_isready -U postgres || echo "$(RED)PostgreSQL não está pronto$(NC)"
	@echo ""
	@echo "$(YELLOW)MongoDB:$(NC)"
	@docker-compose exec -T mongodb mongosh --eval "db.adminCommand('ping')" --quiet || echo "$(RED)MongoDB não está pronto$(NC)"
	@echo ""
	@echo "$(YELLOW)Redis:$(NC)"
	@docker-compose exec -T redis redis-cli -a redis123 ping || echo "$(RED)Redis não está pronto$(NC)"
	@echo ""
	@echo "$(YELLOW)Elasticsearch:$(NC)"
	@curl -s http://localhost:9200/_cluster/health | grep -q "green\|yellow" && echo "$(GREEN)Elasticsearch está pronto$(NC)" || echo "$(RED)Elasticsearch não está pronto$(NC)"
	@echo ""

clean: ## Remove todos os containers, volumes e dados
	@echo "$(RED)ATENÇÃO: Isso irá remover TODOS os dados!$(NC)"
	@read -p "Tem certeza? [y/N] " -n 1 -r; \
	echo; \
	if [[ $$REPLY =~ ^[Yy]$$ ]]; then \
		echo "$(RED)Removendo containers e volumes...$(NC)"; \
		docker-compose down -v; \
		echo "$(GREEN)Limpeza concluída!$(NC)"; \
	else \
		echo "$(YELLOW)Operação cancelada.$(NC)"; \
	fi

init-db: ## Reinicializa os bancos de dados
	@echo "$(YELLOW)Reinicializando bancos de dados...$(NC)"
	docker-compose restart postgres mongodb
	@sleep 5
	@echo "$(GREEN)Bancos de dados reinicializados!$(NC)"

shell-postgres: ## Abre shell do PostgreSQL
	docker-compose exec postgres psql -U postgres -d rede_social

shell-mongodb: ## Abre shell do MongoDB
	docker-compose exec mongodb mongosh -u admin -p admin --authenticationDatabase admin rede_social

shell-redis: ## Abre shell do Redis
	docker-compose exec redis redis-cli -a redis123

kafka-topics: ## Lista tópicos do Kafka
	docker-compose exec kafka kafka-topics --bootstrap-server localhost:9092 --list

kafka-create-topics: ## Cria tópicos necessários do Kafka
	@echo "$(GREEN)Criando tópicos do Kafka...$(NC)"
	docker-compose exec kafka kafka-topics --bootstrap-server localhost:9092 --create --topic user.events --partitions 3 --replication-factor 1 --if-not-exists
	docker-compose exec kafka kafka-topics --bootstrap-server localhost:9092 --create --topic content.events --partitions 3 --replication-factor 1 --if-not-exists
	docker-compose exec kafka kafka-topics --bootstrap-server localhost:9092 --create --topic social.events --partitions 3 --replication-factor 1 --if-not-exists
	docker-compose exec kafka kafka-topics --bootstrap-server localhost:9092 --create --topic notification.events --partitions 3 --replication-factor 1 --if-not-exists
	@echo "$(GREEN)Tópicos criados com sucesso!$(NC)"

test: ## Executa todos os testes
	@echo "$(GREEN)Executando testes...$(NC)"
	@echo "$(YELLOW)Testes serão implementados nas próximas tarefas$(NC)"

install-deps: ## Instala dependências de todos os microsserviços
	@echo "$(GREEN)Instalando dependências...$(NC)"
	@echo "$(YELLOW)Dependências serão instaladas quando os microsserviços forem implementados$(NC)"

dev: ## Inicia ambiente de desenvolvimento completo
	@echo "$(GREEN)Iniciando ambiente de desenvolvimento...$(NC)"
	@make up
	@make kafka-create-topics
	@echo ""
	@echo "$(GREEN)Ambiente pronto!$(NC)"
	@echo ""
	@echo "Serviços disponíveis:"
	@echo "  - PostgreSQL:     localhost:5432"
	@echo "  - MongoDB:        localhost:27017"
	@echo "  - Redis:          localhost:6379"
	@echo "  - Kafka:          localhost:9093"
	@echo "  - Elasticsearch:  http://localhost:9200"
	@echo "  - Jaeger UI:      http://localhost:16686"
	@echo "  - Prometheus:     http://localhost:9090"
	@echo "  - Grafana:        http://localhost:3000"
	@echo ""

backup-postgres: ## Faz backup do PostgreSQL
	@echo "$(GREEN)Fazendo backup do PostgreSQL...$(NC)"
	docker-compose exec -T postgres pg_dump -U postgres rede_social > backup-postgres-$$(date +%Y%m%d-%H%M%S).sql
	@echo "$(GREEN)Backup concluído!$(NC)"

backup-mongodb: ## Faz backup do MongoDB
	@echo "$(GREEN)Fazendo backup do MongoDB...$(NC)"
	docker-compose exec -T mongodb mongodump --username admin --password admin --authenticationDatabase admin --db rede_social --archive > backup-mongodb-$$(date +%Y%m%d-%H%M%S).archive
	@echo "$(GREEN)Backup concluído!$(NC)"

monitor: ## Abre dashboards de monitoramento
	@echo "$(GREEN)Abrindo dashboards...$(NC)"
	@echo "Jaeger:     http://localhost:16686"
	@echo "Prometheus: http://localhost:9090"
	@echo "Grafana:    http://localhost:3000"
	@if command -v xdg-open > /dev/null; then \
		xdg-open http://localhost:16686 & \
		xdg-open http://localhost:9090 & \
		xdg-open http://localhost:3000 & \
	elif command -v open > /dev/null; then \
		open http://localhost:16686 & \
		open http://localhost:9090 & \
		open http://localhost:3000 & \
	fi

# User Service

Microsserviço de gerenciamento de usuários para a Rede Social Brasileira.

## Funcionalidades

- Registro e autenticação de usuários
- Gerenciamento de perfis
- Sistema de seguir/seguidores
- Hash de senhas com bcrypt (mínimo 10 rounds)
- Cache com Redis
- Tokens JWT para autenticação

## Tecnologias

- Java 17
- Spring Boot 3.2
- Spring Data JPA
- PostgreSQL
- Redis
- Flyway (migrations)
- jqwik (property-based testing)

## Pré-requisitos

### Opção 1: Maven Local
- Java 17 ou superior
- Maven 3.9 ou superior
- PostgreSQL 15 (ou via Docker)
- Redis 7 (ou via Docker)

### Opção 2: Docker (Recomendado)
- Docker Desktop instalado e rodando
- Não requer instalação de Java, Maven, PostgreSQL ou Redis

## Executar Localmente

### Com Maven Local

```bash
# Instalar dependências e compilar
mvn clean install

# Executar
mvn spring-boot:run

# Executar testes
mvn test

# Executar apenas testes de propriedade
mvn test -Dtest="**/*Properties"

# Executar apenas testes unitários
mvn test -Dtest="**/*Test"
```

### Com Docker

```bash
# Executar testes (PowerShell)
.\test-with-docker.ps1

# Executar testes (Bash)
./test-with-docker.sh

# Build da aplicação
docker build -t user-service .

# Executar com docker-compose (na raiz do projeto)
docker-compose up user-service
```

## Variáveis de Ambiente

Veja `.env.example` para configurações necessárias.

Principais variáveis:
- `DB_HOST`: Host do PostgreSQL (padrão: localhost)
- `DB_PORT`: Porta do PostgreSQL (padrão: 5432)
- `DB_NAME`: Nome do banco (padrão: redesocial)
- `DB_USER`: Usuário do banco (padrão: postgres)
- `DB_PASSWORD`: Senha do banco (padrão: postgres)
- `REDIS_HOST`: Host do Redis (padrão: localhost)
- `REDIS_PORT`: Porta do Redis (padrão: 6379)
- `JWT_SECRET`: Chave secreta para JWT (mínimo 256 bits)
- `PORT`: Porta do serviço (padrão: 8081)

## Endpoints

### Autenticação
- `POST /api/users/register` - Registrar novo usuário
- `POST /api/users/login` - Autenticar usuário

### Perfil
- `GET /api/users/{id}` - Obter perfil de usuário
- `PUT /api/users/{id}` - Atualizar perfil
- `DELETE /api/users/{id}` - Excluir conta

### Relacionamentos
- `POST /api/users/{id}/follow?followerId={followerId}` - Seguir usuário
- `DELETE /api/users/{id}/follow?followerId={followerId}` - Deixar de seguir
- `GET /api/users/{id}/followers?page=0&size=50` - Listar seguidores
- `GET /api/users/{id}/following?page=0&size=50` - Listar seguindo

### Health Check
- `GET /actuator/health` - Status do serviço

## Testes

O serviço utiliza uma estratégia dual de testes:

### Testes de Propriedade (jqwik)
Validam propriedades universais com 100+ iterações:
- **Property 1**: Registro de usuário válido cria conta
- **Property 2**: Email duplicado rejeita registro
- **Property 4**: Senhas são armazenadas com hash bcrypt
- **Property 5**: Atualização de perfil persiste alterações
- **Property 21**: Seguir usuário cria relacionamento
- **Property 22**: Deixar de seguir remove relacionamento
- **Property 23**: Contadores refletem relacionamentos reais
- **Property 24**: Lista de seguidores paginada com 50 por página

### Testes Unitários
Casos específicos e de borda:
- Tentativa de seguir a si mesmo
- Email duplicado
- Validações de campos obrigatórios
- Operações idempotentes
- Contadores não decrementam abaixo de zero

## Estrutura do Projeto

```
user-service/
├── src/
│   ├── main/
│   │   ├── java/com/redesocial/userservice/
│   │   │   ├── config/          # Configurações (Security, JWT, Redis)
│   │   │   ├── controller/      # Controllers REST
│   │   │   ├── dto/             # Data Transfer Objects
│   │   │   ├── exception/       # Exceções customizadas
│   │   │   ├── model/           # Entidades JPA
│   │   │   ├── repository/      # Repositories JPA
│   │   │   └── service/         # Lógica de negócio
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-test.yml
│   │       └── db/migration/    # Migrations Flyway
│   └── test/
│       └── java/com/redesocial/userservice/
│           └── service/         # Testes (Properties e Unit)
├── Dockerfile
├── pom.xml
└── README.md
```

## Desenvolvimento

### Adicionar Nova Migration

1. Criar arquivo em `src/main/resources/db/migration/`
2. Nomear como `V{version}__{description}.sql`
3. Exemplo: `V2__add_user_preferences.sql`

### Executar Localmente com Banco de Dados

```bash
# Iniciar PostgreSQL e Redis via Docker
docker-compose up postgres redis

# Em outro terminal, executar o serviço
mvn spring-boot:run
```

## Troubleshooting

### Erro: "Email already registered"
- O email já existe no banco de dados
- Use um email diferente ou delete o usuário existente

### Erro: "User cannot follow themselves"
- Tentativa de seguir o próprio usuário
- Verifique os IDs na requisição

### Erro: Connection refused (PostgreSQL/Redis)
- Verifique se os serviços estão rodando
- Execute `docker-compose up postgres redis`
- Verifique as variáveis de ambiente

### Testes falhando
- Certifique-se de que o profile `test` está ativo
- O profile test usa H2 in-memory, não requer PostgreSQL
- Execute: `mvn test -Dspring.profiles.active=test`

## Próximos Passos

Após implementar o User Service, os próximos passos são:
1. Implementar Content Service (posts, stories, interações)
2. Implementar Recommendation Engine (feed personalizado)
3. Implementar Search Service (busca e indexação)
4. Implementar Messaging Service (mensagens em tempo real)
5. Implementar Notification Service (notificações push)


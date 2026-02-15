# Guia de Instalação do Maven

Este guia explica como instalar o Apache Maven no Windows para executar o User Service.

## Opção 1: Instalação Manual

### 1. Baixar Maven

1. Acesse https://maven.apache.org/download.cgi
2. Baixe o arquivo `apache-maven-3.9.x-bin.zip`
3. Extraia para `C:\Program Files\Apache\maven`

### 2. Configurar Variáveis de Ambiente

1. Abra "Variáveis de Ambiente" no Windows
2. Adicione uma nova variável de sistema:
   - Nome: `MAVEN_HOME`
   - Valor: `C:\Program Files\Apache\maven`

3. Edite a variável `Path` e adicione:
   - `%MAVEN_HOME%\bin`

### 3. Verificar Instalação

Abra um novo terminal PowerShell e execute:

```powershell
mvn --version
```

Você deve ver algo como:
```
Apache Maven 3.9.x
Maven home: C:\Program Files\Apache\maven
Java version: 17.x.x
```

## Opção 2: Instalação via Chocolatey

Se você tem o Chocolatey instalado:

```powershell
choco install maven
```

## Opção 3: Instalação via Scoop

Se você tem o Scoop instalado:

```powershell
scoop install maven
```

## Opção 4: Usar Docker (Sem Instalação)

Se você não quer instalar Maven localmente, use Docker:

```powershell
# Executar testes
.\test-with-docker.ps1

# Build
docker run --rm -v "${PWD}:/app" -w /app maven:3.9-eclipse-temurin-17 mvn clean package
```

## Verificar Java

Maven requer Java 17 ou superior. Verifique:

```powershell
java -version
```

Se Java não estiver instalado:

### Instalar Java 17

**Opção 1: Eclipse Temurin (Recomendado)**
1. Acesse https://adoptium.net/
2. Baixe o instalador do Java 17 LTS
3. Execute o instalador

**Opção 2: Via Chocolatey**
```powershell
choco install temurin17
```

**Opção 3: Via Scoop**
```powershell
scoop bucket add java
scoop install temurin17-jdk
```

## Troubleshooting

### "mvn não é reconhecido"

1. Verifique se `MAVEN_HOME` está configurado
2. Verifique se `%MAVEN_HOME%\bin` está no Path
3. Feche e reabra o terminal
4. Execute `mvn --version`

### "JAVA_HOME não está definido"

1. Encontre onde o Java está instalado (geralmente `C:\Program Files\Eclipse Adoptium\jdk-17.x.x`)
2. Adicione variável de ambiente:
   - Nome: `JAVA_HOME`
   - Valor: caminho do JDK
3. Feche e reabra o terminal

### Erro de permissão

Execute o PowerShell como Administrador.

## Próximos Passos

Após instalar Maven, volte para o README.md e siga as instruções de execução.

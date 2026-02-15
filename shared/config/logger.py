"""
Configuração de logging estruturado para microsserviços Python
Usa structlog para logging estruturado e integração com Jaeger para distributed tracing
"""

import logging
import sys
import os
from typing import Any, Dict
import structlog
from structlog.stdlib import LoggerFactory
import uuid
from datetime import datetime


def configure_logging(service_name: str) -> structlog.BoundLogger:
    """
    Configura logging estruturado para o microsserviço
    
    Args:
        service_name: Nome do microsserviço
        
    Returns:
        Logger configurado
    """
    log_level = os.getenv('LOG_LEVEL', 'INFO').upper()
    log_format = os.getenv('LOG_FORMAT', 'json')
    
    # Configurar logging padrão do Python
    logging.basicConfig(
        format="%(message)s",
        stream=sys.stdout,
        level=getattr(logging, log_level)
    )
    
    # Processadores para formato JSON
    json_processors = [
        structlog.contextvars.merge_contextvars,
        structlog.stdlib.add_log_level,
        structlog.stdlib.add_logger_name,
        structlog.processors.TimeStamper(fmt="iso"),
        structlog.processors.StackInfoRenderer(),
        structlog.processors.format_exc_info,
        structlog.processors.UnicodeDecoder(),
        structlog.processors.JSONRenderer()
    ]
    
    # Processadores para formato console (desenvolvimento)
    console_processors = [
        structlog.contextvars.merge_contextvars,
        structlog.stdlib.add_log_level,
        structlog.stdlib.add_logger_name,
        structlog.processors.TimeStamper(fmt="%Y-%m-%d %H:%M:%S"),
        structlog.processors.StackInfoRenderer(),
        structlog.processors.format_exc_info,
        structlog.dev.ConsoleRenderer()
    ]
    
    processors = json_processors if log_format == 'json' else console_processors
    
    # Configurar structlog
    structlog.configure(
        processors=processors,
        wrapper_class=structlog.stdlib.BoundLogger,
        context_class=dict,
        logger_factory=LoggerFactory(),
        cache_logger_on_first_use=True,
    )
    
    # Criar logger com contexto do serviço
    logger = structlog.get_logger()
    logger = logger.bind(service=service_name)
    
    return logger


def generate_trace_id() -> str:
    """Gera um trace ID único"""
    return f"{int(datetime.now().timestamp() * 1000)}-{uuid.uuid4().hex[:9]}"


class RequestLoggingMiddleware:
    """
    Middleware FastAPI para logging de requisições
    """
    
    def __init__(self, logger: structlog.BoundLogger):
        self.logger = logger
    
    async def __call__(self, request, call_next):
        import time
        
        # Obter ou gerar trace ID
        trace_id = request.headers.get('x-trace-id', generate_trace_id())
        
        # Adicionar trace ID ao contexto
        structlog.contextvars.bind_contextvars(trace_id=trace_id)
        
        # Log da requisição
        self.logger.info(
            "incoming_request",
            method=request.method,
            path=request.url.path,
            query=str(request.query_params),
            client_ip=request.client.host if request.client else None,
            user_agent=request.headers.get('user-agent')
        )
        
        # Processar requisição
        start_time = time.time()
        response = await call_next(request)
        duration = (time.time() - start_time) * 1000  # em ms
        
        # Adicionar trace ID ao header da resposta
        response.headers['X-Trace-Id'] = trace_id
        
        # Log da resposta
        log_level = "error" if response.status_code >= 400 else "info"
        self.logger.log(
            log_level,
            "request_completed",
            method=request.method,
            path=request.url.path,
            status_code=response.status_code,
            duration_ms=f"{duration:.2f}"
        )
        
        # Limpar contexto
        structlog.contextvars.clear_contextvars()
        
        return response


def log_exception(logger: structlog.BoundLogger, exc: Exception, context: Dict[str, Any] = None):
    """
    Loga uma exceção com contexto adicional
    
    Args:
        logger: Logger configurado
        exc: Exceção a ser logada
        context: Contexto adicional (opcional)
    """
    log_data = {
        "exception_type": type(exc).__name__,
        "exception_message": str(exc),
        "exception_traceback": True
    }
    
    if context:
        log_data.update(context)
    
    logger.error("exception_occurred", **log_data, exc_info=exc)


# Exemplo de uso
if __name__ == "__main__":
    # Configurar logger
    logger = configure_logging("example-service")
    
    # Logs de exemplo
    logger.info("service_started", version="1.0.0")
    logger.debug("debug_message", data={"key": "value"})
    logger.warning("warning_message", reason="test")
    
    try:
        raise ValueError("Exemplo de erro")
    except Exception as e:
        log_exception(logger, e, {"additional_context": "test"})

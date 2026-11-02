from contextlib import asynccontextmanager
from apscheduler.schedulers.background import BackgroundScheduler
from fastapi import Depends, FastAPI, Request, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from sqlalchemy import text
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.config import settings
from app.database import Base, engine, get_db
from app import auth_models, push_models, sync_models  # Register additive tables.
from app.push import run_alert_cycle
from app.routers import dispositivos
from app.routers import alertas, auth, configuracoes_alerta, dashboard, estoques, medicamentos, movimentacoes, relatorios, unidades, usuarios


@asynccontextmanager
async def lifespan(app):
    if settings.CREATE_TABLES_ON_STARTUP:
        Base.metadata.create_all(bind=engine)
    scheduler = None
    if settings.ALERTS_ENABLED:
        scheduler = BackgroundScheduler(timezone=settings.TIMEZONE)
        scheduler.add_job(run_alert_cycle, "interval", seconds=settings.ALERTS_INTERVAL_SECONDS,
                          max_instances=1, coalesce=True, id="alertas_push")
        scheduler.start()
    yield
    if scheduler:
        scheduler.shutdown(wait=True)


app = FastAPI(
    title=settings.APP_NAME,
    version=settings.APP_VERSION,
    description="Backend didatico para controle de medicamentos hospitalares.",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.allow_origins_list,
    allow_credentials="*" not in settings.allow_origins_list,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.exception_handler(IntegrityError)
def tratar_erro_integridade(req: Request, e: IntegrityError):
    return JSONResponse(
        status_code=status.HTTP_400_BAD_REQUEST,
        content={"detail": "Nao foi possivel salvar. Verifique dados duplicados ou referencias invalidas."},
    )


@app.get("/")
def inicio():
    return {"mensagem": "API Remedio Certo funcionando.", "documentacao": "/docs"}


@app.get("/health")
def health_check(db: Session = Depends(get_db)):
    db.execute(text("SELECT 1"))
    return {"status": "ok"}


app.include_router(auth.router)
app.include_router(dispositivos.router)
app.include_router(unidades.router)
app.include_router(usuarios.router)
app.include_router(medicamentos.router)
app.include_router(estoques.router)
app.include_router(movimentacoes.router)
app.include_router(alertas.router)
app.include_router(configuracoes_alerta.router)
app.include_router(dashboard.router)
app.include_router(relatorios.router)

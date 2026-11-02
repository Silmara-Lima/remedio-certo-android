from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from app.database import get_db
from app.dependencies import exigir_admin, obter_usuario_atual
from app.enums import PerfilUsuario
from app.helpers import aplicar_alteracoes, buscar_ou_404, salvar
from app.models import ConfiguracaoAlerta, UnidadeSaude, Usuario
from app.schemas import ConfiguracaoAlertaCreate, ConfiguracaoAlertaOut, ConfiguracaoAlertaUpdate


router = APIRouter(prefix="/configuracoes-alerta", tags=["Configuracoes de Alerta"])


@router.get("", response_model=list[ConfiguracaoAlertaOut])
def listar_configuracoes(
    db: Session = Depends(get_db),
    usuario: Usuario = Depends(obter_usuario_atual),
):
    query = db.query(ConfiguracaoAlerta)
    if usuario.perfil != PerfilUsuario.admin:
        query = query.filter(ConfiguracaoAlerta.unidade_id == usuario.unidade_id)
    return query.order_by(ConfiguracaoAlerta.unidade_id).all()


@router.post("", response_model=ConfiguracaoAlertaOut)
def criar_configuracao(
    dados: ConfiguracaoAlertaCreate,
    db: Session = Depends(get_db),
    admin: Usuario = Depends(exigir_admin),
):
    buscar_ou_404(db, UnidadeSaude, dados.unidade_id, "Unidade")
    configuracao = ConfiguracaoAlerta(**dados.model_dump())
    return salvar(db, configuracao)


@router.get("/{configuracao_id}", response_model=ConfiguracaoAlertaOut)
def buscar_configuracao(
    configuracao_id: int,
    db: Session = Depends(get_db),
    usuario: Usuario = Depends(obter_usuario_atual),
):
    configuracao = buscar_ou_404(db, ConfiguracaoAlerta, configuracao_id, "Configuracao")
    if usuario.perfil != PerfilUsuario.admin and configuracao.unidade_id != usuario.unidade_id:
        raise HTTPException(status_code=403, detail="Acesso negado.")
    return configuracao


@router.put("/{configuracao_id}", response_model=ConfiguracaoAlertaOut)
def atualizar_configuracao(
    configuracao_id: int,
    dados: ConfiguracaoAlertaUpdate,
    db: Session = Depends(get_db),
    admin: Usuario = Depends(exigir_admin),
):
    configuracao = buscar_ou_404(db, ConfiguracaoAlerta, configuracao_id, "Configuracao")
    aplicar_alteracoes(configuracao, dados.model_dump(exclude_unset=True))
    return salvar(db, configuracao)

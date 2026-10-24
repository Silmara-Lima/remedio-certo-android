from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy import or_
from sqlalchemy.orm import Session

from app.database import get_db
from app.dependencies import exigir_admin
from app.enums import PerfilUsuario
from app.helpers import aplicar_alteracoes, buscar_ou_404, salvar
from app.models import UnidadeSaude, Usuario
from app.schemas import MensagemOut, UsuarioCreate, UsuarioOut, UsuarioUpdate
from app.security import gerar_hash_senha
from app.auth_service import bloquear_usuario, revogar_sessoes


router = APIRouter(prefix="/usuarios", tags=["Usuarios"])


def _validar_unidade_do_usuario(db: Session, perfil: PerfilUsuario, unidade_id: int | None):
    if perfil == PerfilUsuario.funcionario and unidade_id is None:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Funcionario precisa ter unidade_id.")

    if unidade_id is not None:
        buscar_ou_404(db, UnidadeSaude, unidade_id, "Unidade")


@router.get("", response_model=list[UsuarioOut])
def listar_usuarios(
    somente_ativos: bool = True,
    unidade_id: int | None = None,
    busca: str | None = Query(default=None),
    db: Session = Depends(get_db),
    admin: Usuario = Depends(exigir_admin),
):
    q = db.query(Usuario)

    if somente_ativos:
        q = q.filter(Usuario.ativo.is_(True))
    if unidade_id:
        q = q.filter(Usuario.unidade_id == unidade_id)
    if busca:
        termo = f"%{busca}%"
        q = q.filter(or_(Usuario.nome.ilike(termo), Usuario.email.ilike(termo)))

    return q.order_by(Usuario.nome).all()


@router.post("", response_model=UsuarioOut)
def criar_usuario(
    data: UsuarioCreate,
    db: Session = Depends(get_db),
    admin: Usuario = Depends(exigir_admin),
):
    _validar_unidade_do_usuario(db, data.perfil, data.unidade_id)
    body = data.model_dump(exclude={"senha"})
    usuario = Usuario(**body, senha_hash=gerar_hash_senha(data.senha))
    return salvar(db, usuario)


@router.get("/{usuario_id}", response_model=UsuarioOut)
def buscar_usuario(
    usuario_id: int,
    db: Session = Depends(get_db),
    admin: Usuario = Depends(exigir_admin),
):
    return buscar_ou_404(db, Usuario, usuario_id, "Usuario")


@router.put("/{usuario_id}", response_model=UsuarioOut)
def atualizar_usuario(
    usuario_id: int,
    data: UsuarioUpdate,
    db: Session = Depends(get_db),
    admin: Usuario = Depends(exigir_admin),
):
    usuario = bloquear_usuario(db, user_id=usuario_id)
    if usuario is None:
        raise HTTPException(404, "Usuario nao encontrado.")
    body = data.model_dump(exclude_unset=True)

    perfil = body.get("perfil", usuario.perfil)
    unidade = body.get("unidade_id", usuario.unidade_id)
    _validar_unidade_do_usuario(db, perfil, unidade)

    senha = body.pop("senha", None)
    aplicar_alteracoes(usuario, body)

    if senha:
        usuario.senha_hash = gerar_hash_senha(senha)
        revogar_sessoes(db, usuario.id)

    return salvar(db, usuario)


@router.delete("/{usuario_id}", response_model=MensagemOut)
def desativar_usuario(
    usuario_id: int,
    db: Session = Depends(get_db),
    admin: Usuario = Depends(exigir_admin),
):
    if usuario_id == admin.id:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Nao e possivel desativar o proprio usuario.")

    usuario = buscar_ou_404(db, Usuario, usuario_id, "Usuario")
    usuario.ativo = False
    db.commit()
    return MensagemOut(mensagem="Usuario desativado com sucesso.")

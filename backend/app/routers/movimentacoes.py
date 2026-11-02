from datetime import date, datetime, time
import hashlib
import json
from uuid import UUID
from zoneinfo import ZoneInfo

from fastapi import APIRouter, Depends, HTTPException, Query, status
from pydantic import ValidationError
from sqlalchemy import update
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session, joinedload

from app.database import get_db
from app.dependencies import obter_usuario_atual
from app.enums import PerfilUsuario, TipoMovimentacao
from app.helpers import buscar_ou_404
from app.models import EstoqueLote, Medicamento, Movimentacao, UnidadeSaude, Usuario
from app.schemas import MovimentacaoCreate, MovimentacaoOut
from app.services import sincronizar_alertas
from app.sync_models import SyncOperation
from app.sync_schemas import LoteMovimentacoesIn, LoteMovimentacoesOut, MovimentacaoSync


router = APIRouter(prefix="/movimentacoes", tags=["Movimentacoes"])
MAX_QUANTIDADE = 2_147_483_647


def _serializar_escritas(db: Session, usuario: Usuario, unidades: list[int] | None = None):
    """Serialize retries by user; SQLite needs a write transaction before SAVEPOINTs."""
    if db.get_bind().dialect.name == "sqlite":
        connection = db.connection()
        if not connection.connection.driver_connection.in_transaction:
            connection.exec_driver_sql("BEGIN IMMEDIATE")
    else:
        db.query(Usuario).filter(Usuario.id == usuario.id).with_for_update().one()
        if unidades:
            # Keep a stable lock order when two offline batches visit several lots/units.
            db.query(UnidadeSaude).filter(UnidadeSaude.id.in_(set(unidades))).order_by(
                UnidadeSaude.id
            ).with_for_update().all()


def _validar_acesso_unidade(user: Usuario, unidade_id: int):
    if user.perfil == PerfilUsuario.funcionario and user.unidade_id != unidade_id:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Funcionario so pode movimentar estoque da propria unidade.",
        )


def _localizar_estoque(db: Session, data: MovimentacaoCreate) -> EstoqueLote | None:
    if data.estoque_lote_id:
        estoque = (
            db.query(EstoqueLote)
            .filter(EstoqueLote.id == data.estoque_lote_id)
            .with_for_update()
            .first()
        )
        if estoque is None:
            raise HTTPException(status_code=404, detail="Lote de estoque nao encontrado.")
        if (estoque.medicamento_id, estoque.unidade_id, estoque.lote, estoque.validade) != (
            data.medicamento_id, data.unidade_id, data.lote, data.validade
        ):
            raise HTTPException(status_code=409, detail="O lote informado nao corresponde ao medicamento, unidade e validade.")
        return estoque

    return (
        db.query(EstoqueLote)
        .filter(
            EstoqueLote.medicamento_id == data.medicamento_id,
            EstoqueLote.unidade_id == data.unidade_id,
            EstoqueLote.lote == data.lote,
            EstoqueLote.validade == data.validade,
        )
        .with_for_update()
        .first()
    )


def _aplicar_movimentacao_no_estoque(db: Session, data: MovimentacaoCreate) -> EstoqueLote:
    hoje = datetime.now(ZoneInfo("America/Sao_Paulo")).date()
    if data.quantidade < 0 or data.quantidade > MAX_QUANTIDADE:
        raise HTTPException(status_code=400, detail="Quantidade fora do limite permitido.")
    if not data.lote.strip():
        raise HTTPException(status_code=400, detail="Informe o numero do lote.")
    if data.data_fabricacao and data.data_fabricacao > data.validade:
        raise HTTPException(status_code=400, detail="Fabricacao nao pode ser posterior a validade.")
    if data.tipo in (TipoMovimentacao.entrada, TipoMovimentacao.saida) and data.validade < hoje:
        raise HTTPException(status_code=400, detail="Lote vencido nao pode receber entrada ou saida comum; utilize descarte.")
    if data.tipo == TipoMovimentacao.ajuste and not (data.observacao or "").strip():
        raise HTTPException(status_code=400, detail="Ajuste exige justificativa na observacao.")
    if data.tipo != TipoMovimentacao.ajuste and data.quantidade <= 0:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Quantidade precisa ser maior que zero.")

    estoque = _localizar_estoque(db, data)

    if estoque is None and data.tipo in (TipoMovimentacao.entrada, TipoMovimentacao.ajuste):
        # The unique natural key can be created concurrently by another user's request.
        try:
            with db.begin_nested():
                estoque = EstoqueLote(
                    medicamento_id=data.medicamento_id,
                    unidade_id=data.unidade_id,
                    lote=data.lote,
                    data_fabricacao=data.data_fabricacao,
                    validade=data.validade,
                    quantidade_inicial=0,
                    quantidade=0,
                )
                db.add(estoque)
                db.flush()
        except IntegrityError:
            estoque = _localizar_estoque(db, data)
            if estoque is None:
                raise

    if estoque is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Lote de estoque nao encontrado.")

    statement = update(EstoqueLote).where(EstoqueLote.id == estoque.id)
    if data.tipo == TipoMovimentacao.entrada:
        statement = statement.where(
            EstoqueLote.quantidade <= MAX_QUANTIDADE - data.quantidade,
            EstoqueLote.quantidade_inicial <= MAX_QUANTIDADE - data.quantidade,
        ).values(
            quantidade=EstoqueLote.quantidade + data.quantidade,
            quantidade_inicial=EstoqueLote.quantidade_inicial + data.quantidade,
        )
    elif data.tipo == TipoMovimentacao.ajuste:
        # Android's quantity for adjustments is the counted final balance, not a delta.
        statement = statement.values(quantidade=data.quantidade)
    else:
        statement = statement.where(EstoqueLote.quantidade >= data.quantidade).values(
            quantidade=EstoqueLote.quantidade - data.quantidade
        )
    result = db.execute(statement.execution_options(synchronize_session=False))
    if result.rowcount != 1:
        raise HTTPException(status_code=400, detail="Estoque insuficiente ou quantidade fora do limite permitido.")
    db.refresh(estoque)
    return estoque


@router.get("", response_model=list[MovimentacaoOut])
def listar_movimentacoes(
    unidadeId: int | None = Query(default=None),
    unidade_id: int | None = Query(default=None),
    medicamento_id: int | None = None,
    tipo: str | None = None,
    dataInicio: date | None = Query(default=None),
    dataFim: date | None = Query(default=None),
    limite: int = Query(default=100, ge=1, le=500),
    db: Session = Depends(get_db),
    usuario: Usuario = Depends(obter_usuario_atual),
):
    id_unidade = unidadeId if unidadeId is not None else unidade_id
    tipo_ok = _normalizar_tipo_movimentacao(tipo)
    q = db.query(Movimentacao).options(
        joinedload(Movimentacao.medicamento),
        joinedload(Movimentacao.unidade),
        joinedload(Movimentacao.usuario),
    )

    if usuario.perfil == PerfilUsuario.funcionario:
        q = q.filter(Movimentacao.unidade_id == usuario.unidade_id)
    if id_unidade:
        q = q.filter(Movimentacao.unidade_id == id_unidade)
    if medicamento_id:
        q = q.filter(Movimentacao.medicamento_id == medicamento_id)
    if tipo_ok:
        q = q.filter(Movimentacao.tipo == tipo_ok)
    if dataInicio:
        q = q.filter(Movimentacao.criada_em >= datetime.combine(dataInicio, time.min))
    if dataFim:
        q = q.filter(Movimentacao.criada_em <= datetime.combine(dataFim, time.max))

    return q.order_by(Movimentacao.criada_em.desc()).limit(limite).all()


@router.post("", response_model=MovimentacaoOut)
def criar_movimentacao(
    data: MovimentacaoCreate,
    db: Session = Depends(get_db),
    usuario: Usuario = Depends(obter_usuario_atual),
):
    _serializar_escritas(db, usuario, [data.unidade_id])
    mov = registrar_movimentacao(db, data, usuario)
    db.commit()
    db.refresh(mov)
    return mov


def registrar_movimentacao(db: Session, data: MovimentacaoCreate, usuario: Usuario) -> Movimentacao:
    """Shared online/offline rules; caller owns the transaction and commits the receipt."""
    _validar_acesso_unidade(usuario, data.unidade_id)
    medicamento = buscar_ou_404(db, Medicamento, data.medicamento_id, "Medicamento")
    unidade = buscar_ou_404(db, UnidadeSaude, data.unidade_id, "Unidade")
    if not medicamento.ativo or not unidade.ativa:
        raise HTTPException(status_code=400, detail="Medicamento e unidade devem estar ativos.")

    estoque = _aplicar_movimentacao_no_estoque(db, data)
    mov = Movimentacao(
        tipo=data.tipo,
        medicamento_id=data.medicamento_id,
        unidade_id=data.unidade_id,
        estoque_lote_id=estoque.id,
        usuario_id=usuario.id,
        lote=estoque.lote,
        validade=estoque.validade,
        quantidade=data.quantidade,
        numero_documento=data.numero_documento,
        observacao=data.observacao,
    )

    db.add(mov)
    db.flush()
    sincronizar_alertas(db, estoque_lote_id=estoque.id)
    return mov


def _rejeitado(id_local: str | None, codigo: str, mensagem: str) -> dict:
    return {"id_local": id_local, "status": "rejeitado", "repetida": False, "codigo": codigo, "mensagem": mensagem}


def _sincronizar_item(db: Session, raw: dict, usuario: Usuario) -> dict:
    try:
        id_local = str(UUID(str(raw.get("id_local", ""))))
    except (ValueError, TypeError, AttributeError):
        return _rejeitado(None, "ID_LOCAL_INVALIDO", "Informe id_local como UUID para permitir reenvios seguros.")

    data = None
    try:
        data = MovimentacaoSync.model_validate(raw)
        payload = data.model_dump(mode="json", exclude={"id_local"})
    except ValidationError:
        payload = {key: value for key, value in raw.items() if key != "id_local"}
    fingerprint = hashlib.sha256(json.dumps(payload, sort_keys=True, separators=(",", ":"), ensure_ascii=False).encode()).hexdigest()
    anterior = db.query(SyncOperation).filter_by(usuario_id=usuario.id, id_local=id_local).first()
    if anterior is not None:
        if anterior.payload_hash != fingerprint:
            return _rejeitado(id_local, "ID_LOCAL_REUTILIZADO", "Este id_local ja identifica outra operacao. Use novo UUID para uma nova movimentacao.")
        # A user reassigned to another unit cannot retrieve the old unit's receipt details.
        if data is not None:
            try:
                _validar_acesso_unidade(usuario, data.unidade_id)
            except HTTPException as error:
                return _rejeitado(id_local, f"HTTP_{error.status_code}", str(error.detail))
        return {**anterior.resultado, "repetida": True}

    try:
        with db.begin_nested():
            if data is None:
                raise HTTPException(status_code=422, detail="Dados da movimentacao invalidos. Verifique campos, tipos e limites.")
            movimento = registrar_movimentacao(db, data, usuario)
            resultado = {
                "id_local": id_local, "status": "aceito", "repetida": False,
                "movimentacao": MovimentacaoOut.model_validate(movimento).model_dump(mode="json"),
            }
    except HTTPException as error:
        resultado = _rejeitado(id_local, f"HTTP_{error.status_code}", str(error.detail))
    db.add(SyncOperation(usuario_id=usuario.id, id_local=id_local, payload_hash=fingerprint, resultado=resultado))
    db.flush()
    return resultado


@router.post("/lote", response_model=LoteMovimentacoesOut)
def sincronizar_movimentacoes(
    data: LoteMovimentacoesIn,
    db: Session = Depends(get_db),
    usuario: Usuario = Depends(obter_usuario_atual),
):
    unidades = []
    for item in data.movimentacoes:
        try:
            unidade_id = int(item.get("unidade_id", ""))
            if unidade_id > 0:
                unidades.append(unidade_id)
        except (ValueError, TypeError, OverflowError):
            pass  # Validation below reports this item's error without discarding the batch.
    _serializar_escritas(db, usuario, unidades)
    resultados = [_sincronizar_item(db, item, usuario) for item in data.movimentacoes]
    # Receipt and stock are atomic: a lost HTTP response can be retried after reconnecting.
    db.commit()
    return {"resultados": resultados}


def _normalizar_tipo_movimentacao(tipo: str | None) -> TipoMovimentacao | None:
    if not tipo:
        return None
    try:
        return TipoMovimentacao(tipo.lower())
    except ValueError as e:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Tipo de movimentacao invalido.") from e


@router.get("/{movimentacao_id}", response_model=MovimentacaoOut)
def buscar_movimentacao(
    movimentacao_id: int,
    db: Session = Depends(get_db),
    usuario: Usuario = Depends(obter_usuario_atual),
):
    mov = (
        db.query(Movimentacao)
        .options(
            joinedload(Movimentacao.medicamento),
            joinedload(Movimentacao.unidade),
            joinedload(Movimentacao.usuario),
        )
        .filter(Movimentacao.id == movimentacao_id)
        .first()
    )

    if not mov:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Movimentacao nao encontrada.")

    if usuario.perfil == PerfilUsuario.funcionario and mov.unidade_id != usuario.unidade_id:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Acesso negado.")

    return mov

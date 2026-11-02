from datetime import datetime, timedelta
from zoneinfo import ZoneInfo

from sqlalchemy import func, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session, joinedload

from app.config import settings
from app.enums import StatusAlerta, TipoAlerta
from app.models import Alerta, ConfiguracaoAlerta, EstoqueLote, UnidadeSaude
from app.push import enqueue_alert
from app.push_models import AlertCondition


def sincronizar_alertas(db: Session, unidade_id: int | None = None, estoque_lote_id: int | None = None) -> int:
    db.flush()
    units = select(UnidadeSaude.id)
    if unidade_id is not None:
        units = units.where(UnidadeSaude.id == unidade_id)
    if estoque_lote_id is not None:
        units = units.where(UnidadeSaude.id.in_(
            select(EstoqueLote.unidade_id).where(EstoqueLote.id == estoque_lote_id)
        ))
    # All alert writers take unit locks before conditions/alerts/delivery FKs.
    # Movement writers already hold these locks in this same stable order.
    locked_units = list(db.scalars(units.order_by(UnidadeSaude.id).with_for_update()))
    q = db.query(EstoqueLote).options(
        joinedload(EstoqueLote.medicamento),
        joinedload(EstoqueLote.unidade),
    )
    q = q.filter(EstoqueLote.unidade_id.in_(locked_units))

    if unidade_id is not None:
        q = q.filter(EstoqueLote.unidade_id == unidade_id)
    if estoque_lote_id is not None:
        q = q.filter(EstoqueLote.id == estoque_lote_id)

    total = 0
    medicamentos_unidades = {}
    for item in q.all():
        total += _sincronizar_alertas_do_lote(db, item)
        medicamentos_unidades[(item.medicamento_id, item.unidade_id)] = item

    # RN04: compare the total for this medication/unit, never each lot separately.
    # A sync triggered for one lot must still count the other lots in that unit.
    for item in medicamentos_unidades.values():
        quantidade_total = db.scalar(select(func.coalesce(func.sum(EstoqueLote.quantidade), 0)).where(
            EstoqueLote.medicamento_id == item.medicamento_id,
            EstoqueLote.unidade_id == item.unidade_id,
        ))
        minimo = item.medicamento.quantidade_minima or 0
        total += _atualizar_alerta(
            db, item, TipoAlerta.estoque_baixo,
            ativo=item.medicamento.ativo and item.unidade.ativa and quantidade_total <= minimo,
            mensagem=(f"{item.medicamento.nome} esta com estoque baixo em {item.unidade.nome}. "
                      f"Quantidade total: {quantidade_total}. Minimo: {minimo}."),
            agregado=True,
        )

    db.flush()
    return total


def _sincronizar_alertas_do_lote(db: Session, estoque: EstoqueLote) -> int:
    hoje = datetime.now(ZoneInfo(settings.TIMEZONE)).date()
    cfg = (
        db.query(ConfiguracaoAlerta)
        .filter(ConfiguracaoAlerta.unidade_id == estoque.unidade_id)
        .first()
    )

    dias = cfg.dias_alerta_vencimento if cfg else 30
    total = 0
    disponivel = estoque.quantidade > 0 and estoque.medicamento.ativo and estoque.unidade.ativa

    total += _atualizar_alerta(
        db=db,
        estoque=estoque,
        tipo=TipoAlerta.vencido,
        ativo=disponivel and estoque.validade < hoje,
        mensagem=(
            f"{estoque.medicamento.nome}, lote {estoque.lote}, esta vencido desde "
            f"{estoque.validade.isoformat()}."
        ),
    )

    total += _atualizar_alerta(
        db=db,
        estoque=estoque,
        tipo=TipoAlerta.vencimento_proximo,
        ativo=disponivel and hoje <= estoque.validade <= hoje + timedelta(days=dias),
        mensagem=(
            f"{estoque.medicamento.nome}, lote {estoque.lote}, vence em "
            f"{estoque.validade.isoformat()}."
        ),
    )

    return total


def _atualizar_alerta(
    db: Session,
    estoque: EstoqueLote,
    tipo: TipoAlerta,
    ativo: bool,
    mensagem: str,
    agregado: bool = False,
) -> int:
    chave = (f"{tipo.value}:{estoque.medicamento_id}:{estoque.unidade_id}"
             if agregado else f"{tipo.value}:lote:{estoque.id}")
    condition = db.scalar(select(AlertCondition).where(AlertCondition.chave == chave).with_for_update())
    if condition is None:
        # Adopt historical alerts on the first run, including acknowledged ones.
        # No columns or statuses in existing tables are migrated/reinterpreted.
        historical = db.query(Alerta).filter(Alerta.tipo == tipo)
        if agregado:
            historical = historical.filter(Alerta.medicamento_id == estoque.medicamento_id,
                                           Alerta.unidade_id == estoque.unidade_id)
        else:
            historical = historical.filter(Alerta.estoque_lote_id == estoque.id)
        latest = historical.order_by(Alerta.id.desc()).first()
        try:
            with db.begin_nested():
                condition = AlertCondition(chave=chave, ativo=latest is not None,
                                           alerta_id=latest.id if latest else None)
                db.add(condition)
                db.flush()
                if agregado and latest is not None:
                    historical.filter(Alerta.id != latest.id, Alerta.status == StatusAlerta.ativo).update(
                        {Alerta.status: StatusAlerta.resolvido, Alerta.resolvido_em: datetime.utcnow()},
                        synchronize_session=False,
                    )
        except IntegrityError:
            condition = db.scalar(select(AlertCondition).where(
                AlertCondition.chave == chave
            ).with_for_update())
    alerta = db.get(Alerta, condition.alerta_id) if condition.alerta_id else None

    if ativo and (not condition.ativo or alerta is None):
        alerta = Alerta(
            tipo=tipo, mensagem=mensagem, medicamento_id=estoque.medicamento_id,
            unidade_id=estoque.unidade_id, estoque_lote_id=None if agregado else estoque.id,
        )
        db.add(alerta)
        db.flush()
        condition.ativo = True
        condition.alerta_id = alerta.id
        enqueue_alert(db, alerta)
        return 1

    if ativo and alerta is not None and alerta.status == StatusAlerta.ativo:
        alerta.mensagem = mensagem
        enqueue_alert(db, alerta)

    if not ativo:
        condition.ativo = False
        if alerta is not None and alerta.status == StatusAlerta.ativo:
            alerta.status = StatusAlerta.resolvido
            alerta.resolvido_em = datetime.utcnow()

    return 0

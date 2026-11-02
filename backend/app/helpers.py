from fastapi import HTTPException, status
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session


def buscar_ou_404(db: Session, model, id: int, nome: str):
    item = db.get(model, id)
    if not item:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=f"{nome} nao encontrado.")
    return item


def salvar(db: Session, item):
    try:
        db.add(item)
        db.commit()
        db.refresh(item)
        return item
    except IntegrityError as e:
        db.rollback()
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Nao foi possivel salvar. Verifique dados duplicados ou referencias invalidas.",
        ) from e


def aplicar_alteracoes(item, data: dict):
    for key, value in data.items():
        setattr(item, key, value)
    return item

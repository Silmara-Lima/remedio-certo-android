from pathlib import Path
import sys


ROOT_DIR = Path(__file__).resolve().parents[1]
sys.path.append(str(ROOT_DIR))

from app.security import gerar_hash_senha


if __name__ == "__main__":
    senha = input("Digite a senha para gerar o hash: ")
    print(gerar_hash_senha(senha))

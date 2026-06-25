"""Verify Ollama embedding generation and pgvector persistence in a rollback-only transaction."""

import asyncio
import uuid

import asyncpg

from app.core.config import settings
from app.services.ollama_client import OllamaClient


async def main() -> None:
    vector = await OllamaClient().generate_embedding(
        "KnowledgeVault vector persistence test"
    )
    if len(vector) != settings.EMBEDDING_DIMENSION:
        raise RuntimeError(
            f"Expected {settings.EMBEDDING_DIMENSION} dimensions, got {len(vector)}"
        )

    database_url = settings.DATABASE_URL.replace(
        "postgresql+asyncpg://", "postgresql://"
    )
    connection = await asyncpg.connect(database_url)
    transaction = connection.transaction()
    await transaction.start()

    try:
        user_id = await connection.fetchval(
            "SELECT id FROM users WHERE username = 'admin'"
        )
        collection_id = await connection.fetchval(
            """
            INSERT INTO collections(name, created_by)
            VALUES($1, $2)
            RETURNING id
            """,
            f"dimension-test-{uuid.uuid4()}",
            user_id,
        )
        document_id = await connection.fetchval(
            """
            INSERT INTO documents(collection_id, title, created_by)
            VALUES($1, 'dimension test', $2)
            RETURNING id
            """,
            collection_id,
            user_id,
        )
        version_id = await connection.fetchval(
            """
            INSERT INTO document_versions(
                document_id, version_number, file_name, file_path,
                file_size, mime_type, uploaded_by
            )
            VALUES($1, 1, 'test.txt', '/tmp/test.txt', 1, 'text/plain', $2)
            RETURNING id
            """,
            document_id,
            user_id,
        )
        chunk_id = await connection.fetchval(
            """
            INSERT INTO document_chunks(
                document_id, version_id, chunk_index, content, token_count
            )
            VALUES($1, $2, 0, 'dimension test', 2)
            RETURNING id
            """,
            document_id,
            version_id,
        )

        vector_literal = f"[{','.join(str(value) for value in vector)}]"
        await connection.execute(
            """
            INSERT INTO embeddings(
                chunk_id, model_name, embedding, dimension
            )
            VALUES($1, $2, $3::vector, $4)
            """,
            chunk_id,
            settings.OLLAMA_EMBEDDING_MODEL,
            vector_literal,
            len(vector),
        )
        similarity = await connection.fetchval(
            """
            SELECT 1 - (embedding <=> $1::vector)
            FROM embeddings
            WHERE chunk_id = $2
            """,
            vector_literal,
            chunk_id,
        )
        print(
            f"dimension={len(vector)} "
            f"self_similarity={float(similarity):.6f}"
        )
    finally:
        await transaction.rollback()
        await connection.close()


if __name__ == "__main__":
    asyncio.run(main())

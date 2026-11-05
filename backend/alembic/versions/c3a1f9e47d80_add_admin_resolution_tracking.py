"""add admin resolution tracking to emergency_sessions

Adds two nullable columns to emergency_sessions:
  - resolved_by_admin_id: FK → users.id, the admin who ended the session
  - admin_notes: optional free-text dispatcher note

Revision ID: c3a1f9e47d80
Revises: f5f414602e78
Create Date: 2026-10-13

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = 'c3a1f9e47d80'
down_revision: Union[str, Sequence[str], None] = 'f5f414602e78'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    """Add resolver tracking columns to emergency_sessions."""
    op.add_column(
        'emergency_sessions',
        sa.Column(
            'resolved_by_admin_id',
            sa.Integer(),
            sa.ForeignKey('users.id', ondelete='SET NULL'),
            nullable=True,
            comment='FK → users.id — the admin who ended this session, if applicable',
        ),
    )
    op.add_column(
        'emergency_sessions',
        sa.Column(
            'admin_notes',
            sa.String(length=500),
            nullable=True,
            comment='Free-text dispatcher note recorded when ending the session',
        ),
    )
    # Index for efficient admin resolution queries
    op.create_index(
        'ix_emergency_sessions_resolved_by_admin_id',
        'emergency_sessions',
        ['resolved_by_admin_id'],
        unique=False,
    )


def downgrade() -> None:
    """Remove resolver tracking columns from emergency_sessions."""
    op.drop_index(
        'ix_emergency_sessions_resolved_by_admin_id',
        table_name='emergency_sessions',
    )
    op.drop_column('emergency_sessions', 'admin_notes')
    op.drop_column('emergency_sessions', 'resolved_by_admin_id')

from __future__ import annotations

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Runtime settings loaded from DARWIN_* variables and an optional .env."""

    model_config = SettingsConfigDict(
        env_file=".env",
        env_prefix="DARWIN_",
        extra="ignore",
    )

    adb_path: str = "adb"
    device_serial: str | None = None
    dry_run: bool = True
    require_confirmation: bool = True
    max_actions: int = 12
    model: str = "gpt-5.2"

from pydantic import BaseModel
from typing import Optional

class LoginRequest(BaseModel):
    username: Optional[str] = None
    password: Optional[str] = None
    code: Optional[str] = None
    device_id: str
    device_name: Optional[str] = "Fire TV Stick"

class LoginResponse(BaseModel):
    token: str
    user_id: int
    username: str
    device_id: str

class AuthStatusResponse(BaseModel):
    is_valid: bool
    active_device_id: Optional[str] = None
    message: Optional[str] = None

class CreateUserRequest(BaseModel):
    username: str
    login_code: Optional[str] = None


class UpdateUserRequest(BaseModel):
    user_id: int
    username: str
    login_code: str
    is_active: bool = True


class UserDto(BaseModel):
    id: int
    username: str
    login_code: str
    created_at: float
    is_active: bool
    active_device_id: Optional[str] = None

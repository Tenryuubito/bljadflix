import atexit
import json
import os
import shutil
import tempfile

TEST_ADMIN_PASSWORD = "test-admin-password"

_test_dir = tempfile.mkdtemp(prefix="bljadflix_tests_")
atexit.register(shutil.rmtree, _test_dir, True)

_config_path = os.path.join(_test_dir, "config.json")
with open(_config_path, "w", encoding="utf-8") as f:
    json.dump({"backend_port": 8000, "admin_password": TEST_ADMIN_PASSWORD}, f)

os.environ["BLJADFLIX_DB_DIR"] = _test_dir
os.environ["BLJADFLIX_CONFIG"] = _config_path

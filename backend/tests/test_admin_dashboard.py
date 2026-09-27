import os
import sys
import unittest

backend_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if backend_dir not in sys.path:
    sys.path.insert(0, backend_dir)

from database import DatabaseManager
from services.admin_service import AdminService
import app_backend


class TestAdminDashboard(unittest.TestCase):

    def setUp(self):
        self.db = DatabaseManager(db_dir=os.path.dirname(__file__))
        self.db.init_db()
        self.admin_service = AdminService(self.db)

    def test_get_dashboard_stats(self):
        stats = self.admin_service.get_dashboard_stats()
        self.assertIn("total_users", stats)
        self.assertIn("active_users", stats)
        self.assertIn("total_watch_hours", stats)
        self.assertIn("content_counts", stats)

    def test_get_users_list(self):
        users = self.admin_service.get_users_list()
        self.assertTrue(len(users) >= 1)
        self.assertIn("username", users[0])
        self.assertIn("is_active", users[0])

    def test_db_explorer_tables_and_data(self):
        tables_res = self.admin_service.get_db_tables("users")
        self.assertEqual(tables_res["db_name"], "users")
        self.assertTrue(any(t["name"] == "users" for t in tables_res["tables"]))

        data_res = self.admin_service.get_table_data("users", "users", page=1, limit=10)
        self.assertEqual(data_res["table_name"], "users")
        self.assertIn("schema", data_res)
        self.assertIn("rows", data_res)

    def test_execute_sql_query(self):
        query_res = self.admin_service.execute_sql_query("users", "SELECT id, username FROM users LIMIT 1;")
        self.assertEqual(query_res["status"], "success")
        self.assertIn("columns", query_res)
        self.assertIn("rows", query_res)

    def test_dashboard_html_endpoint(self):
        html_res = app_backend.get_dashboard_page()
        self.assertIn("Bljadflix Dashboard", html_res.body.decode("utf-8"))

    def test_create_update_delete_user(self):
        from models.auth import CreateUserRequest, UpdateUserRequest

        # Create
        new_user = app_backend.auth_service.create_user(CreateUserRequest(username="temp_test_user", login_code="999888"))
        user_id = new_user["id"]
        self.assertEqual(new_user["username"], "temp_test_user")

        # Update
        update_res = app_backend.auth_service.update_user(UpdateUserRequest(user_id=user_id, username="temp_updated_user", login_code="888999", is_active=True))
        self.assertEqual(update_res["status"], "success")

        # Delete
        delete_res = app_backend.auth_service.delete_user(user_id)
        self.assertEqual(delete_res["status"], "success")

    def test_user_details_and_logs(self):
        # Test get_user_details for default user (id=1)
        details = self.admin_service.get_user_details(1)
        self.assertEqual(details["status"], "success")
        self.assertIn("user", details)
        self.assertIn("stats", details)
        self.assertIn("total_watch_hours", details["stats"])
        self.assertIn("completion_rate", details["stats"])

        # Log a test search directly using test db
        from services.content_service import ContentService
        cs = ContentService(self.db)
        cs.log_search(user_id=1, source="aniworld", query="Naruto", genre="Action", results_count=5)

        searches = self.admin_service.get_user_search_history(1)
        self.assertTrue(searches["total"] >= 1)
        self.assertEqual(searches["searches"][0]["query"], "Naruto")

        # Test user watchlist details
        watchlist = self.admin_service.get_user_watchlist_details(1)
        self.assertIsInstance(watchlist, list)


if __name__ == "__main__":
    unittest.main()

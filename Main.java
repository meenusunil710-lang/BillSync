import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.*;
import java.util.*;

public class Main {

    static List<Product> products = new ArrayList<>();
    static final String DB_URL = "jdbc:sqlite:billsync.db";

    // ---------------- Data Model ----------------
    static class Product {
        int id;
        String name;
        double price;
        double gst;
        int stock;

        Product(int id, String name, double price, double gst, int stock) {
            this.id = id;
            this.name = name;
            this.price = price;
            this.gst = gst;
            this.stock = stock;
        }
    }

    // ---------------- Main ----------------
    public static void main(String[] args) throws Exception {
        initDB();
        createProductsTable();
        insertInitialProducts();
        loadProductsFromDB();

        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);

        // Serve static frontend files
        server.createContext("/", exchange -> {
            try {
                String path = exchange.getRequestURI().getPath();
                if (path.equals("/")) path = "/index.html";

                File file = new File("static" + path);
                if (!file.exists()) {
                    String notFound = "404 - File Not Found";
                    exchange.sendResponseHeaders(404, notFound.length());
                    exchange.getResponseBody().write(notFound.getBytes());
                    exchange.close();
                    return;
                }

                byte[] bytes = Files.readAllBytes(file.toPath());
                String contentType = "text/html";
                if (path.endsWith(".css")) contentType = "text/css";
                else if (path.endsWith(".js")) contentType = "application/javascript";

                exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=UTF-8");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        // API routes
        server.createContext("/products", new ProductsHandler());
        server.createContext("/addProduct", new AddProductHandler());
        server.createContext("/editProduct", new EditProductHandler());
        server.createContext("/deleteProduct", new DeleteProductHandler());
        server.createContext("/updateStock", new UpdateStockHandler());

        server.setExecutor(null);
        server.start();

        System.out.println("🚀 Server started at http://localhost:8000");
    }

    // ---------------- Database Setup ----------------
    static void initDB() {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            if (conn != null) System.out.println("✅ SQLite DB initialized");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    static void createProductsTable() {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            String sql = "CREATE TABLE IF NOT EXISTS products (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "name TEXT," +
                    "price REAL," +
                    "gst REAL," +
                    "stock INTEGER DEFAULT 50)";
            stmt.execute(sql);
            System.out.println("✅ Products table ready");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    static void insertInitialProducts() {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) as count FROM products");
            if (rs.next() && rs.getInt("count") > 0) return; // already populated

            for (int i = 1; i <= 10; i++) {
                stmt.executeUpdate(String.format(
                        "INSERT INTO products (name, price, gst, stock) VALUES ('Product %d', %d, %d, %d)",
                        i, 10 + i, 5 + (i % 10), 50 + (i % 50)
                ));
            }
            System.out.println("✅ Inserted sample products");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    static void loadProductsFromDB() {
        products.clear();
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id, name, price, gst, stock FROM products")) {

            while (rs.next()) {
                products.add(new Product(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getDouble("price"),
                        rs.getDouble("gst"),
                        rs.getInt("stock")
                ));
            }
            System.out.println("✅ Loaded " + products.size() + " products from DB");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ---------------- Handlers ----------------
    static class ProductsHandler implements HttpHandler {
        public void handle(HttpExchange t) {
            try {
                StringBuilder json = new StringBuilder("[");
                for (int i = 0; i < products.size(); i++) {
                    Product p = products.get(i);
                    json.append(String.format(
                            "{\"id\":%d,\"name\":\"%s\",\"price\":%.2f,\"gst\":%.2f,\"stock\":%d}",
                            p.id, p.name, p.price, p.gst, p.stock
                    ));
                    if (i < products.size() - 1) json.append(",");
                }
                json.append("]");

                byte[] response = json.toString().getBytes(StandardCharsets.UTF_8);
                t.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                t.sendResponseHeaders(200, response.length);
                t.getResponseBody().write(response);
                t.getResponseBody().close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    static class AddProductHandler implements HttpHandler {
        public void handle(HttpExchange t) {
            try {
                if (!t.getRequestMethod().equalsIgnoreCase("POST")) {
                    t.sendResponseHeaders(405, -1);
                    return;
                }

                String body = new String(t.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                Map<String, String> params = parseFormData(body);

                String name = params.get("name");
                double price = Double.parseDouble(params.get("price"));
                double gst = Double.parseDouble(params.get("gst"));
                int stock = Integer.parseInt(params.getOrDefault("stock", "50"));

                try (Connection conn = DriverManager.getConnection(DB_URL);
                     PreparedStatement ps = conn.prepareStatement(
                             "INSERT INTO products (name, price, gst, stock) VALUES (?, ?, ?, ?)")) {
                    ps.setString(1, name);
                    ps.setDouble(2, price);
                    ps.setDouble(3, gst);
                    ps.setInt(4, stock);
                    ps.executeUpdate();
                }

                loadProductsFromDB();
                sendJson(t, 200, "{\"message\":\"Product added successfully\"}");
            } catch (Exception e) {
                e.printStackTrace();
                sendJson(t, 500, "{\"error\":\"Failed to add product\"}");
            }
        }
    }

    static class EditProductHandler implements HttpHandler {
        public void handle(HttpExchange t) {
            try {
                if (!t.getRequestMethod().equalsIgnoreCase("POST")) {
                    t.sendResponseHeaders(405, -1);
                    return;
                }

                String body = new String(t.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                Map<String, String> params = parseFormData(body);

                int id = Integer.parseInt(params.get("id"));
                String name = params.get("name");
                double price = Double.parseDouble(params.get("price"));
                double gst = Double.parseDouble(params.get("gst"));
                int stock = Integer.parseInt(params.get("stock"));

                try (Connection conn = DriverManager.getConnection(DB_URL);
                     PreparedStatement ps = conn.prepareStatement(
                             "UPDATE products SET name=?, price=?, gst=?, stock=? WHERE id=?")) {
                    ps.setString(1, name);
                    ps.setDouble(2, price);
                    ps.setDouble(3, gst);
                    ps.setInt(4, stock);
                    ps.setInt(5, id);
                    ps.executeUpdate();
                }

                loadProductsFromDB();
                sendJson(t, 200, "{\"message\":\"Product updated successfully\"}");
            } catch (Exception e) {
                e.printStackTrace();
                sendJson(t, 500, "{\"error\":\"Failed to update product\"}");
            }
        }
    }

    static class DeleteProductHandler implements HttpHandler {
        public void handle(HttpExchange t) {
            try {
                if (!t.getRequestMethod().equalsIgnoreCase("POST")) {
                    t.sendResponseHeaders(405, -1);
                    return;
                }

                String body = new String(t.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                Map<String, String> params = parseFormData(body);
                int id = Integer.parseInt(params.get("id"));

                try (Connection conn = DriverManager.getConnection(DB_URL);
                     PreparedStatement ps = conn.prepareStatement("DELETE FROM products WHERE id=?")) {
                    ps.setInt(1, id);
                    ps.executeUpdate();
                }

                loadProductsFromDB();
                sendJson(t, 200, "{\"message\":\"Product deleted successfully\"}");
            } catch (Exception e) {
                e.printStackTrace();
                sendJson(t, 500, "{\"error\":\"Failed to delete product\"}");
            }
        }
    }

    static class UpdateStockHandler implements HttpHandler {
        public void handle(HttpExchange t) {
            try {
                if (!t.getRequestMethod().equalsIgnoreCase("POST")) {
                    t.sendResponseHeaders(405, -1);
                    return;
                }

                String body = new String(t.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                Map<String, String> params = parseFormData(body);

                int id = Integer.parseInt(params.get("id"));
                int newStock = Integer.parseInt(params.get("stock"));

                try (Connection conn = DriverManager.getConnection(DB_URL);
                     PreparedStatement ps = conn.prepareStatement("UPDATE products SET stock=? WHERE id=?")) {
                    ps.setInt(1, newStock);
                    ps.setInt(2, id);
                    ps.executeUpdate();
                }

                loadProductsFromDB();
                sendJson(t, 200, "{\"message\":\"Stock updated\"}");
            } catch (Exception e) {
                e.printStackTrace();
                sendJson(t, 500, "{\"error\":\"Failed to update stock\"}");
            }
        }
    }

    // ---------------- Utility ----------------
    static Map<String, String> parseFormData(String formData) {
        Map<String, String> map = new HashMap<>();
        for (String pair : formData.split("&")) {
            String[] kv = pair.split("=");
            if (kv.length == 2)
                map.put(kv[0], java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
        }
        return map;
    }

    static void sendJson(HttpExchange t, int status, String json) {
        try {
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            t.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            t.sendResponseHeaders(status, bytes.length);
            t.getResponseBody().write(bytes);
            t.getResponseBody().close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;

// Работа с базой SQLite
public class Database {

    private Connection connection;

    public Database(String dbPath) throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        createTables();
    }

    private void createTables() throws SQLException {
        Statement stmt = connection.createStatement();
        stmt.execute(
                "CREATE TABLE IF NOT EXISTS scans (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "root_path TEXT NOT NULL, " +
                "scanned_at INTEGER NOT NULL)"
        );
        stmt.execute(
                "CREATE TABLE IF NOT EXISTS files (" +
                "scan_id INTEGER NOT NULL, " +
                "path TEXT NOT NULL, " +
                "size INTEGER NOT NULL, " +
                "hash TEXT, " +
                "is_directory INTEGER NOT NULL)"
        );
        stmt.close();
    }

    public void close() throws SQLException {
        connection.close();
    }

    // Новое сканирование, возвращает его номер
    public long addScan(String folder, long time) throws SQLException {
        PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO scans (root_path, scanned_at) VALUES (?, ?)",
                Statement.RETURN_GENERATED_KEYS
        );
        ps.setString(1, folder);
        ps.setLong(2, time);
        ps.executeUpdate();

        ResultSet keys = ps.getGeneratedKeys();
        long id = keys.getLong(1);
        keys.close();
        ps.close();
        return id;
    }

    public void addFile(long scanId, String path, long size, String hash, int isDir) throws SQLException {
        PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO files (scan_id, path, size, hash, is_directory) VALUES (?, ?, ?, ?, ?)"
        );
        ps.setLong(1, scanId);
        ps.setString(2, path);
        ps.setLong(3, size);
        ps.setString(4, hash);
        ps.setInt(5, isDir);
        ps.executeUpdate();
        ps.close();
    }

    // Список всех сканирований
    public void printScanList() throws SQLException {
        Statement stmt = connection.createStatement();
        ResultSet rs = stmt.executeQuery(
                "SELECT id, root_path, scanned_at FROM scans ORDER BY scanned_at DESC"
        );

        System.out.printf("%-4s %-20s  %s%n", "ID", "Дата", "Папка");
        System.out.println("--------------------------------------------------------------");

        while (rs.next()) {
            long id = rs.getLong("id");
            String folder = rs.getString("root_path");
            long time = rs.getLong("scanned_at");
            int fileCount = countFiles(id);
            System.out.printf("#%-3d %-20s  %s (%d файлов)%n",
                    id, Main.formatTime(time), folder, fileCount);
        }

        rs.close();
        stmt.close();
    }

    public int countFiles(long scanId) throws SQLException {
        PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM files WHERE scan_id = ? AND is_directory = 0"
        );
        ps.setLong(1, scanId);
        ResultSet rs = ps.executeQuery();
        int count = rs.next() ? rs.getInt(1) : 0;
        rs.close();
        ps.close();
        return count;
    }

    // Файлы с одинаковым хешем — дубликаты
    public HashMap<String, ArrayList<String>> findDuplicateGroups(long scanId) throws SQLException {
        HashMap<String, ArrayList<String>> groups = new HashMap<>();

        PreparedStatement ps = connection.prepareStatement(
                "SELECT path, hash FROM files WHERE scan_id = ? AND is_directory = 0 AND hash IS NOT NULL"
        );
        ps.setLong(1, scanId);
        ResultSet rs = ps.executeQuery();

        while (rs.next()) {
            String hash = rs.getString("hash");
            String path = rs.getString("path");
            if (!groups.containsKey(hash)) {
                groups.put(hash, new ArrayList<String>());
            }
            groups.get(hash).add(path);
        }

        rs.close();
        ps.close();

        // оставляем только группы из 2 и более файлов
        HashMap<String, ArrayList<String>> result = new HashMap<>();
        for (String hash : groups.keySet()) {
            if (groups.get(hash).size() >= 2) {
                result.put(hash, groups.get(hash));
            }
        }
        return result;
    }
}

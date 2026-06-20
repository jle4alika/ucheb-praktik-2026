package folderindexer;

import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Scanner;

// Индексатор папок — главный класс программы
public class Main {

    public static void main(String[] args) {
        Scanner input = new Scanner(System.in);
        Database db = null;

        try {
            db = new Database("index.db");
            System.out.println("=== Индексатор папок ===");

            int choice = -1;
            while (choice != 0) {
                System.out.println();
                System.out.println("1 — Проиндексировать папку");
                System.out.println("2 — Найти дубликаты");
                System.out.println("3 — История сканирований");
                System.out.println("0 — Выход");
                System.out.print("Выбор: ");

                choice = Integer.parseInt(input.nextLine().trim());

                if (choice == 1) {
                    scanFolder(input, db);
                } else if (choice == 2) {
                    findDuplicates(input, db);
                } else if (choice == 3) {
                    db.printScanList();
                } else if (choice != 0) {
                    System.out.println("Нет такого пункта.");
                }
            }

            System.out.println("До свидания.");
        } catch (Exception e) {
            System.out.println("Ошибка: " + e.getMessage());
        } finally {
            if (db != null) {
                try {
                    db.close();
                } catch (Exception ignored) {
                }
            }
            input.close();
        }
    }

    // Сканирование папки и сохранение в базу
    private static void scanFolder(Scanner input, Database db) throws Exception {
        System.out.print("Путь к папке: ");
        File root = new File(input.nextLine().trim());

        if (!root.isDirectory()) {
            System.out.println("Это не папка.");
            return;
        }

        long scanId = db.addScan(root.getAbsolutePath(), System.currentTimeMillis());

        System.out.println("Сканирование...");
        scanRecursive(db, root, root, scanId);

        int files = db.countFiles(scanId);
        System.out.println("Готово. Сканирование #" + scanId + ", файлов: " + files);
    }

    // Рекурсивный обход: заходим в каждую вложенную папку
    private static void scanRecursive(Database db, File root, File current, long scanId) throws Exception {
        File[] items = current.listFiles();
        if (items == null) {
            return;
        }

        for (int i = 0; i < items.length; i++) {
            File item = items[i];
            String relative = item.getAbsolutePath()
                    .substring(root.getAbsolutePath().length() + 1);

            if (item.isDirectory()) {
                db.addFile(scanId, relative, 0, null, 1);
                scanRecursive(db, root, item, scanId);
            } else {
                String hash = calcHash(item);
                db.addFile(scanId, relative, item.length(), hash, 0);
            }
        }
    }

    // Хеш файла — «отпечаток» содержимого, по нему ищем дубликаты
    private static String calcHash(File file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[4096];
        FileInputStream in = new FileInputStream(file);

        int read;
        while ((read = in.read(buffer)) != -1) {
            md.update(buffer, 0, read);
        }
        in.close();

        byte[] bytes = md.digest();
        String result = "";
        for (int i = 0; i < bytes.length; i++) {
            result = result + String.format("%02x", bytes[i]);
        }
        return result;
    }

    // Поиск одинаковых файлов в выбранном сканировании
    private static void findDuplicates(Scanner input, Database db) throws Exception {
        db.printScanList();
        System.out.print("ID сканирования: ");
        long scanId = Long.parseLong(input.nextLine().trim());

        HashMap<String, ArrayList<String>> groups = db.findDuplicateGroups(scanId);

        if (groups.size() == 0) {
            System.out.println("Дубликаты не найдены.");
            return;
        }

        System.out.println("Групп дубликатов: " + groups.size());
        int num = 1;
        for (String hash : groups.keySet()) {
            ArrayList<String> paths = groups.get(hash);
            System.out.println("Группа " + num + ":");
            for (int i = 0; i < paths.size(); i++) {
                System.out.println("  " + paths.get(i));
            }
            num++;
        }
    }

    // Формат даты для вывода (вызывается из Database)
    public static String formatTime(long millis) {
        SimpleDateFormat f = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
        return f.format(new Date(millis));
    }
}

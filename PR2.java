import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


public class AsyncArrayProcessor {

    private static final int MIN_ARRAY_SIZE = 40;
    private static final int MAX_ARRAY_SIZE = 60;
    private static final int ARRAY_MIN_VALUE = -100;
    private static final int ARRAY_MAX_VALUE = 100;
    private static final int CHUNK_SIZE = 10;


    static class ArrayMultiplierTask implements Callable<List<Integer>> {
        private final List<Integer> subList;
        private final int multiplier;

        
        
    
        public ArrayMultiplierTask(List<Integer> subList, int multiplier) {
            this.subList = subList;
            this.multiplier = multiplier;
        }

        @Override
        public List<Integer> call() throws Exception {
            List<Integer> resultChunk = new ArrayList<>();
            Thread.sleep(100);

            for (int number : subList) {
                resultChunk.add(number * multiplier);
            }
            return resultChunk;
        }
    }

    public static void main(String[] args) {
        long startTime = System.nanoTime();
        Scanner scanner = new Scanner(System.in);

        System.out.println("--- Налаштування Обробки Масиву ---");
        System.out.print("Введіть множник (ціле число): ");
        int multiplier = scanner.nextInt();

        int arraySize = new Random().nextInt(MAX_ARRAY_SIZE - MIN_ARRAY_SIZE + 1) + MIN_ARRAY_SIZE;
        List<Integer> originalArray = generateRandomArray(arraySize, ARRAY_MIN_VALUE, ARRAY_MAX_VALUE);
        
        System.out.println("--- Створення та Обробка ---");
        System.out.println("Розмір масиву: " + arraySize);
        System.out.println("Множник: " + multiplier);
        System.out.println("Діапазон елементів: [" + ARRAY_MIN_VALUE + "; " + ARRAY_MAX_VALUE + "]");
        System.out.println("Оригінальний масив (перші 10): " + originalArray.stream().limit(10).collect(Collectors.toList()) + "...");
        
        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        List<Future<List<Integer>>> futures = new ArrayList<>();
        
        for (int i = 0; i < arraySize; i += CHUNK_SIZE) {
            int end = Math.min(i + CHUNK_SIZE, arraySize);
            List<Integer> subList = originalArray.subList(i, end);
            
            ArrayMultiplierTask task = new ArrayMultiplierTask(subList, multiplier);
            Future<List<Integer>> future = executor.submit(task);
            futures.add(future);
        }

       
        List<Integer> finalResultArray = new CopyOnWriteArrayList<>();

        int processedChunks = 0;
        for (Future<List<Integer>> future : futures) {
            try {
                while (!future.isDone()) {
                    System.out.print(".");
                    Thread.sleep(50); 
                }
                
                List<Integer> resultChunk = future.get();
                finalResultArray.addAll(resultChunk);
                processedChunks++;
            } catch (Exception e) {
                System.err.println("\nПомилка при отриманні результату: " + e.getMessage());
            }
        }
        
        System.out.println("\n--- Результати Обробки ---");
        System.out.println("Кількість оброблених частин: " + processedChunks);
        System.out.println("Розмір підсумкового масиву: " + finalResultArray.size());
        System.out.println("Підсумковий масив (перші 10): " + finalResultArray.stream().limit(10).collect(Collectors.toList()) + "...");
        if (!originalArray.isEmpty() && !finalResultArray.isEmpty()) {
             System.out.println("Перевірка: " + originalArray.get(0) + " * " + multiplier + " = " + finalResultArray.get(0));
             System.out.println("Перевірка: " + originalArray.get(originalArray.size()-1) + " * " + multiplier + " = " + finalResultArray.get(finalResultArray.size()-1));
        }

        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow(); 
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        long endTime = System.nanoTime();
        double durationSeconds = (double) (endTime - startTime) / 1_000_000_000.0;
        
        System.out.printf("\nЧас роботи програми: %.4f с%n", durationSeconds);
        scanner.close();
    }

 
    private static List<Integer> generateRandomArray(int size, int min, int max) {
        Random random = new Random();
        List<Integer> array = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            array.add(random.nextInt(max - min + 1) + min); 
        }
        return array;
    }
}

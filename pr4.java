import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class CompletableFutureTasks {

    public static void main(String[] args) {
        System.out.println(" ЗАВДАННЯ 1 ");
        performTask1().join();

        System.out.println("\n-----------------------------------\n");

        System.out.println(" ЗАВДАННЯ 2 ");
        performTask2().join();
    }

    // 1 завдання
    private static CompletableFuture<Void> performTask1() {
        //  Асинхронне генерація масиву 
        return CompletableFuture.supplyAsync(() -> {
            long start = System.nanoTime();
            
            int[] array = new int[10];
            Random random = new Random();
            for (int i = 0; i < array.length; i++) {
                array[i] = random.nextInt(20) + 1; // Числа від 1 до 20 ,обмеження щоб факторіал не був завеликим
            }
            
            printTime("Генерація масиву 1", start);
            System.out.println("Масив 1: " + Arrays.toString(array));
            return array;
        })
        //  Отримання масиву, створення другого масиву 
        .thenApplyAsync((firstArray) -> {
            long start = System.nanoTime();
            
            int[] secondArray = Arrays.stream(firstArray).map(i -> i + 5).toArray();
            
            printTime("Створення масиву 2 (+5)", start);
            System.out.println("Масив 2: " + Arrays.toString(secondArray));
            
            // Повертаємо контейнер з обома масивами для наступного кроку
            return new int[][]{firstArray, secondArray}; 
        })
        //  Обчислення факторіалу від суми елементів обох масивів
        .thenApplyAsync((arrays) -> {
            long start = System.nanoTime();
            
            int sum1 = Arrays.stream(arrays[0]).sum();
            int sum2 = Arrays.stream(arrays[1]).sum();
            int totalSum = sum1 + sum2;
            
            System.out.println("Сума масиву 1: " + sum1 + ", Сума масиву 2: " + sum2 + ". Загальна сума: " + totalSum);
            
            BigInteger factorial = calculateFactorial(totalSum);
            
            printTime("Обчислення факторiалу", start);
            return factorial;
        })
        // Виведення результату 
        .thenAcceptAsync((factorial) -> {
            long start = System.nanoTime();
            System.out.println("РЕЗУЛЬТАТ (Факторiал): " + factorial);
            printTime("Виведення результату", start);
        });
    }

    // 2 завдання
    private static CompletableFuture<Void> performTask2() {
        long globalStart = System.nanoTime();

        //  Генеруємо послідовність
        CompletableFuture<List<Integer>> sequenceTask = CompletableFuture.supplyAsync(() -> {
            List<Integer> list = IntStream.range(0, 20)
                    .mapToObj(i -> ThreadLocalRandom.current().nextInt(1, 100))
                    .collect(Collectors.toList());
            
            System.out.println("Згенерована послідовність: " + list);
            return list;
        });

        // К Обчислюємо min за формулою (a1+a2, a2+a3)
        CompletableFuture<String> calculationTask = sequenceTask.thenApplyAsync((list) -> {
            if (list.size() < 2) return "Недостатньо елементів";

            int minSum = Integer.MAX_VALUE;
            List<Integer> sums = new ArrayList<>();

            for (int i = 0; i < list.size() - 1; i++) {
                int sum = list.get(i) + list.get(i + 1);
                sums.add(sum);
                if (sum < minSum) {
                    minSum = sum;
                }
            }
            return "Мiнiмальна сума сусiднiх елементiв: " + minSum + " (iз сум: " + sums + ")";
        });

        // Виводимо результат
        CompletableFuture<Void> printTask = calculationTask.thenAcceptAsync((resultInfo) -> {
            System.out.println(resultInfo);
        });

        //  виведення загального часу
        return printTask.thenRunAsync(() -> {
            printTime("ЗАГАЛЬНИЙ ЧАС виконання Завдання 2", globalStart);
        });
    }


    // метод для вимірювання та виведення часу виконання 
    private static void printTime(String operationName, long startTimeNano) {
        long duration = (System.nanoTime() - startTimeNano) / 1000; // переведення в мікросекунди
        System.out.printf("[LOG] %s зайняло: %d мкс (Thread: %s)%n", 
                operationName, duration, Thread.currentThread().getName());
    }

    // метод для обчислення факторіалу 
    private static BigInteger calculateFactorial(int n) {
        BigInteger result = BigInteger.ONE;
        for (int i = 2; i <= n; i++) {
            result = result.multiply(BigInteger.valueOf(i));
        }
        return result;
    }
}

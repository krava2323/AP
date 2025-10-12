import java.util.concurrent.Semaphore;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;
import java.util.List;


public class ParkingSimulation {

    private static final int NUMBER_OF_CARS = 10;
    private static final long SIMULATION_DURATION_MS = 20000; 
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    
    public static void log(String message) {
        String time = LocalTime.now().format(TIME_FORMATTER);
        System.out.println(time + " | " + message);
    }


    static class Parking {
        public static final int DAY_CAPACITY = 5;  
        public static final int NIGHT_CAPACITY = 8; 

        private final Semaphore semaphore = new Semaphore(NIGHT_CAPACITY, true); 
        private final AtomicInteger occupiedSpots = new AtomicInteger(0);
        private final Object capacityLock = new Object();

        public int getCurrentCapacity(LocalTime currentTime) {
            LocalTime dayStart = LocalTime.of(6, 0);
            LocalTime nightStart = LocalTime.of(21, 0);
            boolean isDay = (currentTime.isAfter(dayStart) || currentTime.equals(dayStart)) && currentTime.isBefore(nightStart);
            return isDay ? DAY_CAPACITY : NIGHT_CAPACITY;
        }

        
        public void updateCapacity() {
            synchronized (capacityLock) {
                LocalTime now = LocalTime.now();
                int currentCapacity = getCurrentCapacity(now);
                int currentOccupied = occupiedSpots.get();
                int totalPermits = currentOccupied + semaphore.availablePermits();
                
                if (totalPermits != currentCapacity) {
                    int diff = currentCapacity - totalPermits; 

                    if (diff < 0) {
                        int permitsToDrain = totalPermits - currentCapacity;
                        if (semaphore.availablePermits() >= permitsToDrain) {
                             semaphore.acquireUninterruptibly(permitsToDrain);
                        } else {
                            semaphore.drainPermits();
                            int permitsToRestore = currentCapacity - currentOccupied;
                            if (permitsToRestore > 0) {
                                semaphore.release(permitsToRestore);
                            }
                        }
                        log(" ЗМІНА ЛІМІТУ: **ДЕНЬ** (" + currentCapacity + " місць).");
                    } else if (diff > 0) {
                        semaphore.release(diff); 
                        log("ЗМІНА ЛІМІТУ: **НІЧ** (" + currentCapacity + " місць).");
                    }
                }
            }
        }

      
     
        public void parkCar(String carName) throws InterruptedException {
            try {
                semaphore.acquire(); 
                occupiedSpots.incrementAndGet();
                long parkingTime = (long) (Math.random() * 5000) + 1000;
                Thread.sleep(parkingTime); 
            } catch (InterruptedException e) {
                log("ACC ПОМИЛКА: " + carName + " був перерваний.");
                throw e;
            }
        }

     
        public void leaveCar(String carName) {
            semaphore.release();
            occupiedSpots.decrementAndGet();
        }
    }


    static class Car implements Runnable {
        private final String name;
        private final Parking parking;
        private boolean hasAcquiredSpot = false; 

        public Car(String name, Parking parking) {
            this.name = name;
            this.parking = parking; 
        }

        @Override
        public void run() {
            try {
                parking.parkCar(name);
                hasAcquiredSpot = true; 
            } catch (InterruptedException e) {
            } catch (Exception e) {
                 log("❌ КРИТИЧНА ПОМИЛКА " + name + ": " + e.getClass().getSimpleName());
            } finally {
                if (hasAcquiredSpot) {
                    parking.leaveCar(name);
                }
            }
        }
    }


    static class TimeUpdater implements Runnable {
        private final Parking parking;
        private volatile boolean running = true; 

        public TimeUpdater(Parking parking) {
            this.parking = parking;
        }

        public void stop() { this.running = false; }

        @Override
        public void run() {
            while (running) {
                try {
                    Thread.sleep(2000); 
                    parking.updateCapacity();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    running = false; 
                }
            }
        }
    }


    static class Monitor implements Runnable {
        private final Parking parking;
        private final List<Thread> carThreads;
        private volatile boolean running = true;

        public Monitor(Parking parking, List<Thread> carThreads) {
            this.parking = parking;
            this.carThreads = carThreads;
        }
        
        public void stop() { this.running = false; }

        @Override
        public void run() {
            while (running) {
                try {
                    Thread.sleep(1500); 
                    
                    int maxCapacity = parking.getCurrentCapacity(LocalTime.now());
                    int occupied = parking.occupiedSpots.get();
                    int available = parking.semaphore.availablePermits();
                    String time = LocalTime.now().format(TIME_FORMATTER);

                    System.out.println("\n\n============================================ СТАН ПАРКОВКИ (" + time + ") ============================================");
                    
                    System.out.printf("| %-10s | %-12s | %-12s | %-12s |\n", "ТИП ЧАСУ", "МАКС. МIСЦЬ", "ЗАЙНЯТО", "ВІЛЬНО");
                    System.out.println("--------------------------------------------------------------------------");
                    System.out.printf("| %-10s | %-12d | %-12d | %-12d |\n", 
                                      maxCapacity == Parking.DAY_CAPACITY ? " ДЕНЬ" : "НІЧ", 
                                      maxCapacity, 
                                      occupied, 
                                      available);
                    System.out.println("==========================================================================");

                    System.out.printf("| %-15s | %-18s | %-20s |\n", "АВТОМОБIЛЬ", "СТАТУС (ЛЮДСЬК.)", "СТАН ПОТОКУ (Java)");
                    System.out.println("--------------------------------------------------------------------------");

                    for (Thread t : carThreads) {
                        if (t.isAlive()) {
                            String carStatus;
                            Thread.State state = t.getState();
                            
                            if (state == Thread.State.TIMED_WAITING) {
                                carStatus = "Паркується (Sleep)";
                            } else if (state == Thread.State.WAITING || state == Thread.State.BLOCKED) {
                                carStatus = "Очікує мiсця (Queue)";
                            } else if (state == Thread.State.RUNNABLE) {
                                carStatus = "Виконується (Drive/Leave)";
                            } else {
                                carStatus = "Завершено/Неактивний";
                            }
                            
                            System.out.printf("| %-15s | %-18s | %-20s |\n", t.getName(), carStatus, state);
                        }
                    }
                    System.out.println("==========================================================================");

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    running = false;
                }
            }
        }
    }


    public static void main(String[] args) {
        System.out.println("==================================================================================================================================");
        System.out.println(" СИМУЛЯЦІЯ ПАРКУВАННЯ ");
        System.out.println("----------------------------------------------------------------------------------------------------------------------------------");
        log("Симуляція розпочата. Тривалість: " + (SIMULATION_DURATION_MS / 1000) + " сек.");
        System.out.println("----------------------------------------------------------------------------------------------------------------------------------");

        Parking parking = new Parking();
        List<Thread> carThreads = new ArrayList<>();

        TimeUpdater timeUpdater = new TimeUpdater(parking); 
        Thread timeThread = new Thread(timeUpdater, "TimeUpdater");
        timeThread.start();

        Monitor monitor = new Monitor(parking, carThreads);
        Thread monitorThread = new Thread(monitor, "Monitor");
        monitorThread.start();

        for (int i = 1; i <= NUMBER_OF_CARS; i++) {
            Car car = new Car("Авто-" + i, parking);
            Thread carThread = new Thread(car, "Авто-" + i);
            carThreads.add(carThread);
            carThread.start();
        }

        try {
            Thread.sleep(SIMULATION_DURATION_MS); 
            
            timeUpdater.stop();
            timeThread.interrupt();
            timeThread.join();

            monitor.stop();
            monitorThread.interrupt();
            monitorThread.join();

            System.out.println("\n\n==================================================================================================================================");
            log("СИМУЛЯЦІЯ ЗАВЕРШУЄТЬСЯ. Примусове переривання потоків.");
            
            for (Thread t : carThreads) {
                if (t.isAlive()) {
                    t.interrupt(); 
                }
            }
            
            for (Thread t : carThreads) {
                try {
                    t.join(500); 
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

        } catch (InterruptedException e) {
            System.err.println("Головний поток симуляції був перерваний.");
            Thread.currentThread().interrupt();
        }
        
        System.out.println("\n==================================================================================================================================");
        log(" СИМУЛЯЦІЯ УСПІШНО ЗАВЕРШЕНА.");
        System.out.println("==================================================================================================================================");
    }
}
